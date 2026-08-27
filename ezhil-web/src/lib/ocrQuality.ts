/**
 * How far to trust an extraction, and whether a teacher has to read it first.
 *
 * The words that come out of here become a passage a dyslexic child will
 * practise aloud. A misread word is not a cosmetic bug — it teaches the wrong
 * spelling to the reader least able to catch it. So an uncertain extraction
 * stops for review rather than flowing into the generator.
 *
 * The server enforces the same rule; this exists so the studio can show the
 * requirement up front instead of letting a teacher fill in a whole lesson and
 * then be refused.
 */

export interface Extraction {
  text: string;
  confidence: number | null;
  minConfidence: number | null;
  engine: string | null;
  sourceHash: string | null;
  requiresReview: boolean;
  reviewReason: string | null;
}

export const emptyExtraction = (text = ''): Extraction => ({
  text,
  confidence: null,
  minConfidence: null,
  engine: null,
  sourceHash: null,
  requiresReview: false,
  reviewReason: null,
});

/** A .docx or .txt is read exactly — no reader confidence is involved. */
export const exactExtraction = (text: string): Extraction => ({
  ...emptyExtraction(text),
  confidence: 1,
  minConfidence: 1,
  engine: 'native',
});

/**
 * The weakest reading in a set, ignoring anything that produced no text.
 *
 * One bad page is enough to make the whole lesson wrong, so a batch inherits
 * its worst member rather than an average — averaging would let a clean page
 * mask a garbled one.
 */
export function worstOf(extractions: Extraction[]): Extraction | null {
  const withText = extractions.filter(e => e.text.trim());
  if (withText.length === 0) return null;
  return withText.reduce((acc, e) =>
    (e.confidence ?? 1) < (acc.confidence ?? 1) ? e : acc,
  );
}

/**
 * Combine several extractions into the one the generator will be given.
 *
 * [sourceHash] is kept only when a single extraction is being used, because
 * that is the only case where the server's cached confidence still describes
 * the text being sent.
 */
export function aggregate(extractions: Extraction[], combinedText: string): Extraction {
  const worst = worstOf(extractions);
  const withText = extractions.filter(e => e.text.trim());
  return {
    text: combinedText,
    confidence: worst?.confidence ?? null,
    minConfidence: worst?.minConfidence ?? null,
    engine: worst?.engine ?? null,
    sourceHash: withText.length === 1 ? withText[0].sourceHash : null,
    requiresReview: withText.some(e => e.requiresReview),
    reviewReason: worst?.reviewReason ?? null,
  };
}

/** Where the studio goes next once extraction finishes. */
export type NextStep = 'manual' | 'review' | 'generate';

export function nextStep(quality: Extraction): NextStep {
  if (!quality.text.trim()) return 'manual';
  return quality.requiresReview ? 'review' : 'generate';
}
