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
    primary = Color(0xFF5848D8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E4FF),
    onPrimaryContainer = Color(0xFF241A68),
    secondary = Color(0xFF137B78),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F4EE),
    onSecondaryContainer = Color(0xFF073D3B),
    tertiary = Color(0xFFB85D35),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE3D7),
    onTertiaryContainer = Color(0xFF53210E),
    background = Color(0xFFF6F6FA),
    onBackground = Color(0xFF171822),
    surface = Color(0xFFFEFDFF),
    onSurface = Color(0xFF171822),
    surfaceVariant = Color(0xFFECEBF3),
    onSurfaceVariant = Color(0xFF626274),
    outline = Color(0xFF858497),
    outlineVariant = Color(0xFFD8D7E2),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB8AEFF),
    onPrimary = Color(0xFF251B69),
    primaryContainer = Color(0xFF39317D),
    onPrimaryContainer = Color(0xFFE8E4FF),
    secondary = Color(0xFF84D8CA),
    onSecondary = Color(0xFF003735),
    secondaryContainer = Color(0xFF15534F),
    onSecondaryContainer = Color(0xFFC5F1E9),
    tertiary = Color(0xFFFFB596),
    onTertiary = Color(0xFF5B250F),
    tertiaryContainer = Color(0xFF793A22),
    onTertiaryContainer = Color(0xFFFFDBCA),
    background = Color(0xFF101117),
    onBackground = Color(0xFFE8E7F0),
    surface = Color(0xFF171820),
    onSurface = Color(0xFFE8E7F0),
    surfaceVariant = Color(0xFF23242F),
    onSurfaceVariant = Color(0xFFC3C1D0),
    outline = Color(0xFF8F8DA0),
    outlineVariant = Color(0xFF41424E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
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
