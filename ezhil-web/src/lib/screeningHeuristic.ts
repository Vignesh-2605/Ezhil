/**
 * The audio-only screening score, kept pure so it can be tested.
 *
 * This mirrors ScreeningHeuristic.kt. The two must agree: a child screened in
 * the browser and the same child screened on the handset should not land in
 * different risk bands because of which client they happened to use.
 *
 * It is a proxy, not a measurement — quiet reading is weak evidence of
 * mumbling and long pauses are weak evidence of decoding effort. Anything
 * derived from it carries an "Estimate" label on the teacher screen.
 *
 * What it must never be again is a constant. The previous formula compared
 * mean-square energy directly against a 0..1 scale —
 * `0.05 + (1 - clamp(energy, 0.01, 1))` — and normal speech has a mean-square
 * near 0.045, so the expression always blew past its own 0.5 ceiling. Every
 * recording scored 0.5 and every child tripped the 0.40 "phoneme_confusion"
 * threshold.
 */

export interface Scores {
  risk: number;
  phonemeErrorRate: number;
  syllableSkipRate: number;
}

/** How much of the risk score comes from hesitation rather than volume. */
const RISK_FROM_PAUSES = 0.75;

/** Loudness where a clear reader sits, in dBFS. */
const CLEAR_DBFS = -12;
/** Loudness where a reader is too quiet to judge, in dBFS. */
const QUIET_DBFS = -40;

const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

/** 0.0 at a clear reading level, rising to 1.0 as the audio gets quieter. */
export function quietness(meanSquare: number): number {
  const rms = Math.sqrt(Math.max(meanSquare, 0));
  const dbfs = rms > 1e-6 ? 20 * Math.log10(rms) : -90;
  return clamp((CLEAR_DBFS - dbfs) / (CLEAR_DBFS - QUIET_DBFS), 0, 1);
}

/**
 * @param meanSquare mean of squared PCM samples, each in [-1, 1]
 * @param longPauses count of silences longer than the pause threshold
 */
export function score(meanSquare: number, longPauses: number): Scores {
  const phoneme = clamp(0.05 + 0.45 * quietness(meanSquare), 0, 0.5);
  const skip = clamp(longPauses * 0.03, 0, 0.5);

  // Risk leans on hesitation, not on volume. Weighting the two equally let a
  // loud but halting reader score lower than a quiet but fluent one. How often
  // a child stops is at least a plausible proxy for decoding effort; how
  // loudly they speak is mostly a property of the room and the microphone.
  const risk = RISK_FROM_PAUSES * skip + (1 - RISK_FROM_PAUSES) * phoneme;

  return {
    risk: clamp(risk, 0, 1),
    phonemeErrorRate: phoneme,
    syllableSkipRate: skip,
  };
}

export function riskLevel(score: number): 'low' | 'medium' | 'high' {
  if (score >= 0.65) return 'high';
  if (score >= 0.35) return 'medium';
  return 'low';
}

export function errorTags(s: Scores): string[] {
  const tags: string[] = [];
  if (s.phonemeErrorRate > 0.4) tags.push('phoneme_confusion');
  if (s.syllableSkipRate > 0.3) tags.push('syllable_skip');
  return tags;
}
