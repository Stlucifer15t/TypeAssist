package com.typeassist.app.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
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

private val LightColorScheme = lightColorScheme(
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
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF191B25),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191B25),
    surfaceVariant = Color(0xFFEEF0F6),
    onSurfaceVariant = Color(0xFF626574),
    outline = Color(0xFF85889A),
    outlineVariant = Color(0xFFD8DAE4),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColorScheme = darkColorScheme(
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
    background = Color(0xFF0D1016),
    onBackground = Color(0xFFE8E9F1),
    surface = Color(0xFF151922),
    onSurface = Color(0xFFE8E9F1),
    surfaceVariant = Color(0xFF232835),
    onSurfaceVariant = Color(0xFFC2C6D3),
    outline = Color(0xFF8C91A1),
    outlineVariant = Color(0xFF3A404D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

object AppThemeMode {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
    const val AMOLED = "amoled"

    fun sanitize(value: String?): String = when (value?.lowercase()) {
        LIGHT -> LIGHT
        DARK -> DARK
        AMOLED -> AMOLED
        else -> SYSTEM
    }
}

private val AmoledColorScheme = DarkColorScheme.copy(
    background = Color.Black,
    onBackground = Color(0xFFF3F4FA),
    surface = Color(0xFF080A0E),
    onSurface = Color(0xFFF3F4FA),
    surfaceVariant = Color(0xFF171A22),
    onSurfaceVariant = Color(0xFFC4C8D4),
    outlineVariant = Color(0xFF303643)
)

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
    val resolvedMode = AppThemeMode.sanitize(themeMode)
    val darkTheme = when (resolvedMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
        else -> isSystemInDarkTheme()
    }
    val colorScheme = when (resolvedMode) {
        AppThemeMode.LIGHT -> LightColorScheme
        AppThemeMode.DARK -> DarkColorScheme
        AppThemeMode.AMOLED -> AmoledColorScheme
        else -> if (darkTheme) DarkColorScheme else LightColorScheme
    }
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
