package com.typeassist.app.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Accent colours are defined here; every surface role comes from ThemePalettes, so that all of
// Light, Dark and AMOLED set the full surface-container family explicitly.

private fun lightScheme(surfaces: SurfacePalette): ColorScheme = lightColorScheme(
    primary = Color(0xFF4F46D8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E7FF),
    onPrimaryContainer = Color(0xFF201A5C),
    secondary = Color(0xFF14766E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8F1EC),
    onSecondaryContainer = Color(0xFF073D38),
    tertiary = Color(0xFFB75F3E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE3D8),
    onTertiaryContainer = Color(0xFF51210F),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(surfaces.background),
    onBackground = Color(surfaces.onBackground),
    surface = Color(surfaces.surface),
    onSurface = Color(surfaces.onSurface),
    surfaceVariant = Color(surfaces.surfaceVariant),
    onSurfaceVariant = Color(surfaces.onSurfaceVariant),
    outline = Color(surfaces.outline),
    outlineVariant = Color(surfaces.outlineVariant),
    surfaceDim = Color(surfaces.surfaceDim),
    surfaceBright = Color(surfaces.surfaceBright),
    surfaceContainerLowest = Color(surfaces.surfaceContainerLowest),
    surfaceContainerLow = Color(surfaces.surfaceContainerLow),
    surfaceContainer = Color(surfaces.surfaceContainer),
    surfaceContainerHigh = Color(surfaces.surfaceContainerHigh),
    surfaceContainerHighest = Color(surfaces.surfaceContainerHighest)
)

private fun darkScheme(surfaces: SurfacePalette): ColorScheme = darkColorScheme(
    primary = Color(0xFFC0B8FF),
    onPrimary = Color(0xFF231D60),
    primaryContainer = Color(0xFF393574),
    onPrimaryContainer = Color(0xFFE9E7FF),
    secondary = Color(0xFF7DD8C9),
    onSecondary = Color(0xFF003832),
    secondaryContainer = Color(0xFF15534D),
    onSecondaryContainer = Color(0xFFC5F0E8),
    tertiary = Color(0xFFFFB69A),
    onTertiary = Color(0xFF5B260F),
    tertiaryContainer = Color(0xFF793C25),
    onTertiaryContainer = Color(0xFFFFDCCF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(surfaces.background),
    onBackground = Color(surfaces.onBackground),
    surface = Color(surfaces.surface),
    onSurface = Color(surfaces.onSurface),
    surfaceVariant = Color(surfaces.surfaceVariant),
    onSurfaceVariant = Color(surfaces.onSurfaceVariant),
    outline = Color(surfaces.outline),
    outlineVariant = Color(surfaces.outlineVariant),
    surfaceDim = Color(surfaces.surfaceDim),
    surfaceBright = Color(surfaces.surfaceBright),
    surfaceContainerLowest = Color(surfaces.surfaceContainerLowest),
    surfaceContainerLow = Color(surfaces.surfaceContainerLow),
    surfaceContainer = Color(surfaces.surfaceContainer),
    surfaceContainerHigh = Color(surfaces.surfaceContainerHigh),
    surfaceContainerHighest = Color(surfaces.surfaceContainerHighest)
)

private val LightColorScheme = lightScheme(ThemePalettes.LIGHT)
private val DarkColorScheme = darkScheme(ThemePalettes.DARK)

/** AMOLED keeps the Dark accents and swaps in the true-black surfaces. */
private val AmoledColorScheme = darkScheme(ThemePalettes.AMOLED)

/** The colour scheme for a stored Appearance value, with System resolved against [systemDark]. */
private fun appColorScheme(themeMode: String, systemDark: Boolean): ColorScheme =
    when (ThemePalettes.resolveMode(themeMode, systemDark)) {
        AppThemeMode.DARK -> DarkColorScheme
        AppThemeMode.AMOLED -> AmoledColorScheme
        else -> LightColorScheme
    }

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
)

@Composable
fun AppTheme(
    themeMode: String = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val resolvedMode = ThemePalettes.resolveMode(themeMode, systemDark)
    val darkTheme = resolvedMode != AppThemeMode.LIGHT
    val colorScheme = appColorScheme(resolvedMode, systemDark)
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        shapes = AppShapes,
        content = content
    )
}
