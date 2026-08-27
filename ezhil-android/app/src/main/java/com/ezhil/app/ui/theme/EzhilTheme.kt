package com.ezhil.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary            = Cyan,
    onPrimary          = TextOnCyan,
    primaryContainer   = CyanDim,
    secondary          = Amber,
    onSecondary        = TextOnAmber,
    secondaryContainer = AmberDim,
    background         = BgDark,
    onBackground       = TextPrimary,
    surface            = BgCard,
    onSurface          = TextPrimary,
    surfaceVariant     = BgCardElevated,
    onSurfaceVariant   = TextSecondary,
    outline            = Border,
    error              = Error,
    onError            = TextPrimary,
    errorContainer     = ErrorBg,
)

@Composable
fun EzhilTheme(
    content: @Composable () -> Unit
) {
    // Publish the screen size once, here, so no screen has to read the
    // configuration itself and they cannot disagree about what "compact" means.
    CompositionLocalProvider(LocalScreenSize provides rememberScreenSize()) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography  = EzhilTypography,
            shapes      = EzhilShapes,
            content     = content
        )
    }
}
