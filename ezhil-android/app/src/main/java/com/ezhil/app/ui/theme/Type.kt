package com.ezhil.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezhil.app.R

val BaloTamizha2 = FontFamily(
    Font(R.font.balo_tamizha2_regular, FontWeight.Normal),
    Font(R.font.balo_tamizha2_bold,    FontWeight.Bold)
)

val NotoSansTamil = FontFamily(
    Font(R.font.noto_sans_tamil_regular, FontWeight.Normal),
    Font(R.font.noto_sans_tamil_bold,    FontWeight.Bold)
)

val NotoSerifTamil = FontFamily(
    Font(R.font.noto_serif_tamil_regular, FontWeight.Normal)
)

val DMSans = FontFamily(
    Font(R.font.dm_sans_regular,   FontWeight.Normal),
    Font(R.font.dm_sans_semibold,  FontWeight.SemiBold),
    Font(R.font.dm_sans_bold,      FontWeight.Bold)
)

// Lesson reader — strict dyslexia spec, NEVER override these values
object ReaderConstraints {
    val FontSize      = 24.sp
    val LineHeight    = 43.2.sp    // 24 * 1.8
    val LetterSpacing = 0.96.sp    // 0.04em * 24sp
    val MaxWidthDp    = 680.dp
    val Background    = BgReader
    val TextColor     = TextReader
    val FontFamily    = NotoSerifTamil
}

object Spacing {
    val xs  = 4.dp
    val sm  = 8.dp
    val md  = 16.dp
    val lg  = 24.dp
    val xl  = 32.dp
    val xxl = 48.dp
}

/**
 * The type ramp, named.
 *
 * Screens were setting fontSize inline in ~350 places across 12 distinct
 * values — 9, 10, 11, 13, 17sp and so on — which is why the hierarchy reads as
 * arbitrary rather than designed. These are the only sizes any screen should
 * use, and none is below the 12sp readability floor.
 *
 * Ratio is a major third (1.25) from a 16sp base, tightened at the display end
 * so headlines have presence without the jump feeling accidental.
 */
object TypeScale {
    val hero    = 34.sp   // one per screen, at most
    val display = 26.sp   // screen titles
    val title   = 21.sp   // section headings
    val heading = 18.sp   // card headings
    val body    = 16.sp   // default reading size
    val bodySm  = 14.sp   // secondary copy
    val label   = 13.sp   // buttons, chips, tabs
    val caption = 12.sp   // metadata — the floor, never go under
}

/**
 * Optical letter-spacing. Large type needs negative tracking to stop looking
 * loose; small uppercase labels need positive tracking to stay legible.
 * Tamil is not tracked at all — its diacritics stack, and spacing them apart
 * breaks the glyph clusters.
 */
object Tracking {
    val hero    = (-0.8).sp
    val display = (-0.4).sp
    val title   = (-0.2).sp
    val label   = 0.4.sp
    val caption = 0.6.sp
    val tamil   = 0.sp
}

object TouchTarget {
    val student = 48.dp
    val teacher = 44.dp
    val game    = 56.dp
}

val EzhilTypography = Typography(
    displayLarge   = TextStyle(
        fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
        fontSize = TypeScale.hero, lineHeight = 40.sp,
        letterSpacing = Tracking.tamil, color = TextPrimary,
    ),
    displayMedium  = TextStyle(
        fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
        fontSize = TypeScale.display, lineHeight = 32.sp,
        letterSpacing = Tracking.tamil, color = TextPrimary,
    ),
    headlineLarge  = TextStyle(
        fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
        fontSize = TypeScale.title, lineHeight = 28.sp,
        letterSpacing = Tracking.tamil, color = TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontFamily = DMSans, fontWeight = FontWeight.SemiBold,
        fontSize = TypeScale.heading, lineHeight = 24.sp,
        letterSpacing = Tracking.title, color = TextPrimary,
    ),
    // Tamil needs more leading than Latin at the same size — the vowel signs
    // and pulli marks sit above and below the base glyph and collide at 1.4.
    bodyLarge      = TextStyle(
        fontFamily = NotoSansTamil, fontWeight = FontWeight.Normal,
        fontSize = TypeScale.body, lineHeight = 26.sp,
        letterSpacing = Tracking.tamil, color = TextSecondary,
    ),
    bodyMedium     = TextStyle(
        fontFamily = NotoSansTamil, fontWeight = FontWeight.Normal,
        fontSize = TypeScale.bodySm, lineHeight = 22.sp,
        letterSpacing = Tracking.tamil, color = TextSecondary,
    ),
    labelLarge     = TextStyle(
        fontFamily = DMSans, fontWeight = FontWeight.SemiBold,
        fontSize = TypeScale.label, lineHeight = 18.sp,
        letterSpacing = Tracking.label, color = TextMuted,
    ),
    labelMedium    = TextStyle(
        fontFamily = DMSans, fontWeight = FontWeight.Medium,
        fontSize = TypeScale.caption, lineHeight = 16.sp,
        letterSpacing = Tracking.caption, color = TextMuted,
    ),
)
