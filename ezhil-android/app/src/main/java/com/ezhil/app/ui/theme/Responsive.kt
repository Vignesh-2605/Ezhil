package com.ezhil.app.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times

/**
 * Screen-size adaptation.
 *
 * The app previously had no adaptive layout at all — no WindowSizeClass, no
 * BoxWithConstraints, no configuration reads — so every dimension was authored
 * against one reference handset. On a 5" budget phone the denser screens
 * overflow; on a tablet everything sits in a narrow ribbon down the middle.
 * Classrooms use both.
 */
enum class ScreenSize {
    /** < 360dp wide. Budget handsets, and any phone in display-zoom mode. */
    Compact,
    /** 360–599dp. The overwhelming majority of phones. */
    Regular,
    /** 600–839dp. Small tablets and unfolded foldables. */
    Expanded,
    /** ≥ 840dp. Full tablets. */
    Large;

    val isTablet: Boolean get() = this == Expanded || this == Large
}

val LocalScreenSize = compositionLocalOf { ScreenSize.Regular }

@Composable
@ReadOnlyComposable
fun rememberScreenSize(): ScreenSize {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < 360 -> ScreenSize.Compact
        widthDp < 600 -> ScreenSize.Regular
        widthDp < 840 -> ScreenSize.Expanded
        else          -> ScreenSize.Large
    }
}

/** Pick a value per screen size without a `when` at every call site. */
@Composable
@ReadOnlyComposable
fun <T> adaptive(compact: T, regular: T, expanded: T = regular, large: T = expanded): T =
    when (LocalScreenSize.current) {
        ScreenSize.Compact  -> compact
        ScreenSize.Regular  -> regular
        ScreenSize.Expanded -> expanded
        ScreenSize.Large    -> large
    }

/**
 * Content inset that grows with the display, so a tablet does not render a
 * phone-width column glued to the left edge.
 */
@Composable
@ReadOnlyComposable
fun screenPadding(): PaddingValues = PaddingValues(
    horizontal = adaptive(Spacing.sm, Spacing.md, Spacing.xl, 64.dp),
    vertical = Spacing.md,
)

/**
 * Cap on reading-width content. Long lines are hard for every reader and
 * materially harder for a dyslexic one, so text never spans a tablet.
 */
@Composable
@ReadOnlyComposable
fun contentMaxWidth(): Dp = adaptive(
    compact  = 480.dp,
    regular  = 560.dp,
    expanded = 680.dp,
    large    = 760.dp,
)

/** Nudges type up slightly on big screens and down on very small ones. */
@Composable
@ReadOnlyComposable
fun TextUnit.scaled(): TextUnit = when (LocalScreenSize.current) {
    ScreenSize.Compact  -> this * 0.92f
    ScreenSize.Regular  -> this
    ScreenSize.Expanded -> this * 1.08f
    ScreenSize.Large    -> this * 1.15f
}

/** Same, for dimensions that should track the type scale. */
@Composable
@ReadOnlyComposable
fun Dp.scaled(): Dp = when (LocalScreenSize.current) {
    ScreenSize.Compact  -> this * 0.9f
    ScreenSize.Regular  -> this
    ScreenSize.Expanded -> this * 1.1f
    ScreenSize.Large    -> this * 1.2f
}

/**
 * Floor for readable text.
 *
 * The screens hardcoded 9–11sp in 129 places. That is too small for a reading
 * aid on any device, and it disappears entirely on a small screen or when a
 * user has raised their system font size — which the people this app is for
 * are more likely than average to have done.
 */
object MinType {
    /** Nothing user-facing goes below this. */
    val absolute = 12.sp
    /** Floor for metadata and captions specifically. */
    val caption = 13.sp
}

/**
 * Standard content column for a screen body.
 *
 * Every screen hardcoded its horizontal padding — 80 call sites of a fixed
 * Spacing value. On a sub-360dp handset that leaves content pressed against
 * the edges; on a tablet the same 16dp means text runs the full 840dp width,
 * which is unreadable and looks like a stretched phone app.
 *
 * This pads adaptively and caps the column, centring it once the display is
 * wider than the cap. Apply to the scrolling body of a screen, not to full
 * bleed chrome like top bars.
 */
@Composable
fun Modifier.screenContent(
    horizontal: Boolean = true,
    maxWidth: Dp = contentMaxWidth(),
): Modifier {
    val pad = adaptive(Spacing.sm, Spacing.md, Spacing.xl, 64.dp)
    return this
        .fillMaxWidth()
        .widthIn(max = maxWidth)
        .then(if (horizontal) Modifier.padding(horizontal = pad) else Modifier)
}

/** Centres [screenContent] inside its parent on wide displays. */
@Composable
fun Modifier.centeredScreenContent(maxWidth: Dp = contentMaxWidth()): Modifier =
    this.screenContent(maxWidth = maxWidth)

/**
 * Horizontal inset for screen bodies, replacing a hardcoded Spacing value.
 *
 * Sized so a sub-360dp handset is not pressed against the bezel and a tablet
 * does not run text edge to edge. Call it wherever a screen was passing a
 * fixed value to `padding(horizontal = …)`.
 */
@Composable
@ReadOnlyComposable
fun screenGutter(): Dp = adaptive(
    compact  = Spacing.sm,
    regular  = Spacing.md,
    expanded = Spacing.xl,
    large    = 56.dp,
)
