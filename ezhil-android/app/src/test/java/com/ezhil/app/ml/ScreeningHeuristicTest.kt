package com.ezhil.app.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The screening score must respond to the recording.
 *
 * A previous version returned a flat 0.5 for every input, so every child was
 * scored identically and every child was tagged "phoneme_confusion". A
 * constant is worse than no score at all: it looks like a measurement while
 * carrying no information, and a teacher acts on it.
 */
class ScreeningHeuristicTest {

    /** Mean-square for a sine of the given peak amplitude. */
    private fun meanSquare(amplitude: Float) = amplitude * amplitude / 2f

    @Test
    fun `a clear reader scores well below the phoneme-confusion threshold`() {
        // 0.40 is the tag threshold in ScreeningModel.buildResult.
        val clear = ScreeningHeuristic.score(meanSquare(0.3f), longPauses = 0)
        assertTrue(
            "clear reading scored ${clear.phonemeErrorRate}, which would tag every fluent child",
            clear.phonemeErrorRate < 0.40f,
        )
    }

    @Test
    fun `quieter audio scores higher than clear audio`() {
        val clear = ScreeningHeuristic.score(meanSquare(0.3f), 0)
        val quiet = ScreeningHeuristic.score(meanSquare(0.02f), 0)
        assertTrue(
            "quiet=${quiet.risk} should exceed clear=${clear.risk}",
            quiet.risk > clear.risk,
        )
    }

    @Test
    fun `the score is not a constant across realistic recordings`() {
        val amplitudes = listOf(0.9f, 0.5f, 0.3f, 0.1f, 0.03f, 0.008f)
        val risks = amplitudes.map { ScreeningHeuristic.score(meanSquare(it), 0).risk }
        val spread = (risks.max() - risks.min())
        assertTrue(
            "risk spread was $spread across $amplitudes — the score carries no information",
            spread > 0.1f,
        )
    }

    @Test
    fun `pauses raise the syllable-skip component`() {
        val none = ScreeningHeuristic.score(meanSquare(0.3f), longPauses = 0)
        val many = ScreeningHeuristic.score(meanSquare(0.3f), longPauses = 8)
        assertTrue(many.syllableSkipRate > none.syllableSkipRate)
        assertTrue(many.risk > none.risk)
    }

    @Test
    fun `hesitation outweighs volume in the risk score`() {
        // A loud but halting reader must not score below a quiet but fluent
        // one. An even split allowed exactly that.
        val loudHalting = ScreeningHeuristic.score(meanSquare(0.5f), longPauses = 6)
        val quietFluent = ScreeningHeuristic.score(meanSquare(0.03f), longPauses = 0)
        assertTrue(
            "loud-halting=${loudHalting.risk} should exceed quiet-fluent=${quietFluent.risk}",
            loudHalting.risk > quietFluent.risk,
        )
    }

    @Test
    fun `quietness is bounded and oriented correctly`() {
        assertEquals(0f, ScreeningHeuristic.quietness(meanSquare(1.0f)), 0.001f)
        assertEquals(1f, ScreeningHeuristic.quietness(0f), 0.001f)
        assertTrue(ScreeningHeuristic.quietness(meanSquare(0.02f)) > ScreeningHeuristic.quietness(meanSquare(0.3f)))
    }

    @Test
    fun `every component stays inside its documented range`() {
        for (a in listOf(0f, 0.001f, 0.05f, 0.3f, 1.0f)) {
            for (p in listOf(0, 1, 5, 40)) {
                val s = ScreeningHeuristic.score(meanSquare(a), p)
                assertTrue(s.risk in 0f..1f)
                assertTrue(s.phonemeErrorRate in 0f..0.5f)
                assertTrue(s.syllableSkipRate in 0f..0.5f)
                // Risk is weighted toward hesitation, not volume.
                val expected = 0.75f * s.syllableSkipRate + 0.25f * s.phonemeErrorRate
                assertTrue(abs(s.risk - expected) < 1e-5f)
            }
        }
    }
}
