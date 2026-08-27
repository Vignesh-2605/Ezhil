import { describe, expect, it } from 'vitest';

import {
  aggregate,
  emptyExtraction,
  exactExtraction,
  nextStep,
  worstOf,
  type Extraction,
} from '../ocrQuality';

/**
 * These guard the rule that a doubtful extraction must stop for a teacher to
 * read. The words it lets through become a passage a dyslexic child practises
 * aloud, so a regression here teaches wrong spellings to the reader least able
 * to notice.
 */

const read = (over: Partial<Extraction> = {}): Extraction => ({
  ...emptyExtraction('ஒரு பெரிய யானை காட்டில் வாழ்ந்தது.'),
  confidence: 0.95,
  minConfidence: 0.93,
  engine: 'paddleocr',
  sourceHash: 'abc123',
  ...over,
});

describe('worstOf', () => {
  it('picks the lowest-confidence page', () => {
    const worst = worstOf([read({ confidence: 0.95 }), read({ confidence: 0.61 })]);
    expect(worst?.confidence).toBe(0.61);
  });

  it('ignores pages that produced no text', () => {
    // An empty page contributes nothing and must not be treated as the worst.
    const worst = worstOf([
      read({ confidence: 0.9 }),
      read({ text: '   ', confidence: 0 }),
    ]);
    expect(worst?.confidence).toBe(0.9);
  });

  it('returns null when nothing was read', () => {
    expect(worstOf([read({ text: '' })])).toBeNull();
    expect(worstOf([])).toBeNull();
  });

  it('treats an unknown confidence as trustworthy rather than worst', () => {
    // Native text has no reader confidence; it should not drag a batch down.
    const worst = worstOf([exactExtraction('typed'), read({ confidence: 0.7 })]);
    expect(worst?.confidence).toBe(0.7);
  });
});

describe('aggregate', () => {
  it('a batch inherits its weakest page, not an average', () => {
    // Averaging would let a clean page mask a garbled one.
    const q = aggregate([read({ confidence: 0.99 }), read({ confidence: 0.55 })], 'combined');
    expect(q.confidence).toBe(0.55);
  });

  it('one page needing review makes the whole batch need review', () => {
    const q = aggregate(
      [read(), read({ requiresReview: true, reviewReason: 'blurry' })],
      'combined',
    );
    expect(q.requiresReview).toBe(true);
  });

  it('keeps the server hash only for a single extraction', () => {
    expect(aggregate([read()], 'x').sourceHash).toBe('abc123');
    expect(aggregate([read(), read()], 'x').sourceHash).toBeNull();
  });

  it('ignores review flags on pages that produced no text', () => {
    const q = aggregate([read(), read({ text: '', requiresReview: true })], 'combined');
    expect(q.requiresReview).toBe(false);
  });

  it('carries the combined text through', () => {
    expect(aggregate([read()], 'the joined text').text).toBe('the joined text');
  });
});

describe('nextStep', () => {
  it('a clean read goes straight to generation', () => {
    expect(nextStep(read())).toBe('generate');
  });

  it('a flagged read stops for review', () => {
    expect(nextStep(read({ requiresReview: true }))).toBe('review');
  });

  it('an empty read goes to manual entry, not review', () => {
    // There is nothing to review, so asking for a check would be a dead end.
    expect(nextStep(read({ text: '', requiresReview: true }))).toBe('manual');
    expect(nextStep(read({ text: '   ' }))).toBe('manual');
  });

  it('typed text is never sent for review', () => {
    expect(nextStep(exactExtraction('a teacher typed this passage'))).toBe('generate');
  });
});
