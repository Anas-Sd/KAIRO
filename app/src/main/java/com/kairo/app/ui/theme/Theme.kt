package com.kairo.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = KairoPrimary,
    onPrimary = KairoOnPrimary,
    primaryContainer = KairoPrimaryContainer,
    onPrimaryContainer = KairoOnPrimaryContainer,
    secondary = KairoSecondary,
    onSecondary = KairoOnSecondary,
    secondaryContainer = KairoSecondaryContainer,
    tertiary = KairoTertiary,
    tertiaryContainer = KairoTertiaryContainer,
    background = KairoBackground,
    surface = KairoSurface,
    surfaceVariant = KairoSurfaceContainerHighest,
    onSurface = KairoOnSurface,
    onSurfaceVariant = KairoOnSurfaceVariant,
    error = KairoError,
    errorContainer = KairoErrorContainer,
    onErrorContainer = KairoOnErrorContainer,
    outline = KairoOutline,
    outlineVariant = KairoOutlineVariant
)

@Composable
fun KAIROTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = KairoBackground.toArgb()
            window.navigationBarColor = KairoBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
