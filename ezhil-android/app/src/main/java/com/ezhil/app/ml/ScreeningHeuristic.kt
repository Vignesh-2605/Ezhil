package com.ezhil.app.ml

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * The audio-only fallback score, kept pure so it can be tested.
 *
 * It is a proxy, not a measurement: quiet reading is weak evidence of mumbling
 * and long pauses are weak evidence of decoding effort. The teacher screen
 * shows an "Estimate" pill next to anything derived from it for that reason.
 *
 * What it must never be again is a constant. The previous formula compared
 * mean-square energy directly against a 0..1 scale —
 * `0.05f + (1f - energy.coerceIn(0.01f, 1f))` — and normal speech has a
 * mean-square near 0.045, so the expression always blew past its own 0.5
 * ceiling. Every recording scored 0.5, every child tripped the 0.40
 * "phoneme_confusion" threshold, and the TFLite model distilled from this
 * heuristic dutifully learned to emit the same constant.
 */
object ScreeningHeuristic {

    data class Scores(
        val risk: Float,
        val phonemeErrorRate: Float,
        val syllableSkipRate: Float,
    )

    /** How much of the risk score comes from hesitation rather than volume. */
    private const val RISK_FROM_PAUSES = 0.75f

    /** Loudness where a clear reader sits, in dBFS. */
    private const val CLEAR_DBFS = -12f
    /** Loudness where a reader is too quiet to judge, in dBFS. */
    private const val QUIET_DBFS = -40f

    /**
     * @param meanSquare mean of squared PCM samples, each in [-1, 1]
     * @param longPauses count of silences longer than the pause threshold
     */
    fun score(meanSquare: Float, longPauses: Int): Scores {
        val quietness = quietness(meanSquare)
        val phoneme = (0.05f + 0.45f * quietness).coerceIn(0f, 0.5f)
        val skip = (longPauses * 0.03f).coerceIn(0f, 0.5f)

        // Risk leans on hesitation, not on volume. Weighting the two equally
        // let a loud but halting reader score lower than a quiet but fluent
        // one — the retrained model's own ordering check caught it. How often
        // a child stops is at least a plausible proxy for decoding effort;
        // how loudly they speak is mostly a property of the room and the
        // microphone, so it stays a minor term.
        val risk = (RISK_FROM_PAUSES * skip + (1f - RISK_FROM_PAUSES) * phoneme)
        return Scores(
            risk = risk.coerceIn(0f, 1f),
            phonemeErrorRate = phoneme,
            syllableSkipRate = skip,
        )
    }

    /** 0.0 at a clear reading level, rising to 1.0 as the audio gets quieter. */
    fun quietness(meanSquare: Float): Float {
        val rms = sqrt(meanSquare.toDouble()).toFloat()
        val dbfs = if (rms > 1e-6f) 20f * log10(rms.toDouble()).toFloat() else -90f
        return ((CLEAR_DBFS - dbfs) / (CLEAR_DBFS - QUIET_DBFS)).coerceIn(0f, 1f)
    }
}
