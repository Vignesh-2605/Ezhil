import { describe, expect, it } from 'vitest';

import { errorTags, quietness, riskLevel, score } from '../screeningHeuristic';

/**
 * The screening score must respond to the recording.
 *
 * A previous version returned a flat 0.5 for every input, so every child was
 * scored identically and every child was tagged "phoneme_confusion". A
 * constant is worse than no score at all: it looks like a measurement while
 * carrying no information, and a teacher acts on it.
 *
 * These mirror ScreeningHeuristicTest.kt. If one side is changed alone, the
 * web and the handset will disagree about the same child.
 */

/** Mean-square for a sine of the given peak amplitude. */
const meanSquare = (amplitude: number) => (amplitude * amplitude) / 2;

describe('screening heuristic', () => {
  it('scores a clear reader well below the phoneme-confusion threshold', () => {
    // 0.40 is the tag threshold in errorTags().
    expect(score(meanSquare(0.3), 0).phonemeErrorRate).toBeLessThan(0.4);
  });

  it('scores quieter audio higher than clear audio', () => {
    expect(score(meanSquare(0.02), 0).risk).toBeGreaterThan(score(meanSquare(0.3), 0).risk);
  });

  it('is not a constant across realistic recordings', () => {
    const risks = [0.9, 0.5, 0.3, 0.1, 0.03, 0.008]
      .map(a => score(meanSquare(a), 0).risk);
    expect(Math.max(...risks) - Math.min(...risks)).toBeGreaterThan(0.1);
  });

  it('raises the syllable-skip component with pauses', () => {
    const none = score(meanSquare(0.3), 0);
    const many = score(meanSquare(0.3), 8);
    expect(many.syllableSkipRate).toBeGreaterThan(none.syllableSkipRate);
    expect(many.risk).toBeGreaterThan(none.risk);
  });

  it('weighs hesitation above volume', () => {
    // A loud but halting reader must not score below a quiet but fluent one.
    // An even split allowed exactly that.
    const loudHalting = score(meanSquare(0.5), 6);
    const quietFluent = score(meanSquare(0.03), 0);
    expect(loudHalting.risk).toBeGreaterThan(quietFluent.risk);
  });

  it('keeps quietness bounded and oriented correctly', () => {
    expect(quietness(meanSquare(1.0))).toBeCloseTo(0, 3);
    expect(quietness(0)).toBeCloseTo(1, 3);
    expect(quietness(meanSquare(0.02))).toBeGreaterThan(quietness(meanSquare(0.3)));
  });

  it('keeps every component inside its documented range', () => {
    for (const a of [0, 0.001, 0.05, 0.3, 1.0]) {
      for (const p of [0, 1, 5, 40]) {
        const s = score(meanSquare(a), p);
        expect(s.risk).toBeGreaterThanOrEqual(0);
        expect(s.risk).toBeLessThanOrEqual(1);
        expect(s.phonemeErrorRate).toBeGreaterThanOrEqual(0);
        expect(s.phonemeErrorRate).toBeLessThanOrEqual(0.5);
        expect(s.syllableSkipRate).toBeGreaterThanOrEqual(0);
        expect(s.syllableSkipRate).toBeLessThanOrEqual(0.5);
        expect(s.risk).toBeCloseTo(0.75 * s.syllableSkipRate + 0.25 * s.phonemeErrorRate, 5);
      }
    }
  });

  it('does not tag a fluent reader', () => {
    expect(errorTags(score(meanSquare(0.3), 0))).toEqual([]);
  });

  it('bands the risk score the way the handset does', () => {
    expect(riskLevel(0.7)).toBe('high');
    expect(riskLevel(0.4)).toBe('medium');
    expect(riskLevel(0.2)).toBe('low');
  });
});
