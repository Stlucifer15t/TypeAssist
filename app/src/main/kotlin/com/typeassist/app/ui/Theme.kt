package com.typeassist.app.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.AppThemeMode

/**
 * Holds the app-wide appearance choice so every screen (and the Compose host in
 * [com.typeassist.app.MainActivity]) reacts instantly when the user switches theme.
 * Kept outside of [AppConfig] state on purpose: the accessibility service also reads
 * the persisted config, while this object mirrors the currently selected values.
 */
object ThemeController {
    var themeMode by mutableStateOf(AppThemeMode.SYSTEM)
        private set
    var useDynamicColor by mutableStateOf(true)
        private set

    /** True when the resolved (effective) scheme is dark; updated on every composition. */
    @Volatile
    var isEffectiveDark: Boolean = false
        private set

    fun syncFrom(config: AppConfig?) {
        if (config == null) return
        themeMode = AppThemeMode.sanitize(config.appThemeMode)
        // Older configs have no stored value; treat null (Gson) as the default.
        useDynamicColor = try { config.useDynamicColor } catch (_: Exception) { true }
    }
}

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF5348CE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3DFFF),
    onPrimaryContainer = Color(0xFF180A68),
    secondary = Color(0xFF146962),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFA0F0E8),
    onSecondaryContainer = Color(0xFF00201D),
    tertiary = Color(0xFF96490E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCB),
    onTertiaryContainer = Color(0xFF351000),
    background = Color(0xFFF7F5FF),
    onBackground = Color(0xFF1A1B22),
    surface = Color(0xFFFBF9FF),
    onSurface = Color(0xFF1A1B22),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    surfaceDim = Color(0xFFDBD9E3),
    surfaceBright = Color(0xFFFBF9FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFEDF5),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE3E1E9),
    outline = Color(0xFF78767F),
    outlineVariant = Color(0xFFC8C5D0),
    inverseSurface = Color(0xFF2F3037),
    inverseOnSurface = Color(0xFFF1F0F7),
    inversePrimary = Color(0xFFC4C0FF),
    surfaceTint = Color(0xFF5348CE),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color.Black
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFC4C0FF),
    onPrimary = Color(0xFF241976),
    primaryContainer = Color(0xFF3B31A5),
    onPrimaryContainer = Color(0xFFE3DFFF),
    secondary = Color(0xFF84D4CC),
    onSecondary = Color(0xFF003733),
    secondaryContainer = Color(0xFF00504A),
    onSecondaryContainer = Color(0xFFA0F0E8),
    tertiary = Color(0xFFFFB68C),
    onTertiary = Color(0xFF522100),
    tertiaryContainer = Color(0xFF743500),
    onTertiaryContainer = Color(0xFFFFDBCB),
    background = Color(0xFF12131A),
    onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF12131A),
    onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC8C5D0),
    surfaceDim = Color(0xFF12131A),
    surfaceBright = Color(0xFF383941),
    surfaceContainerLowest = Color(0xFF0D0E14),
    surfaceContainerLow = Color(0xFF1A1B22),
    surfaceContainer = Color(0xFF1E1F26),
    surfaceContainerHigh = Color(0xFF292A31),
    surfaceContainerHighest = Color(0xFF34353C),
    outline = Color(0xFF928F9A),
    outlineVariant = Color(0xFF47464F),
    inverseSurface = Color(0xFFE3E1E9),
    inverseOnSurface = Color(0xFF2F3037),
    inversePrimary = Color(0xFF5348CE),
    surfaceTint = Color(0xFFC4C0FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black
)

/** Pure-black variant of the dark scheme for AMOLED screens (no grey wash, true off pixels). */
private fun darkColorSchemeForAmoled(): darkColorScheme = DarkColorScheme.copy(
    background = Color.Black,
    onBackground = Color(0xFFE3E1E9),
    surface = Color.Black,
    onSurface = Color(0xFFE3E1E9),
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF2E2F36),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B10),
    surfaceContainer = Color(0xFF101015),
    surfaceContainerHigh = Color(0xFF1A1A20),
    surfaceContainerHighest = Color(0xFF25252B)
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = when (ThemeController.themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
        else -> isSystemInDarkTheme()
    }
    val amoled = darkTheme && ThemeController.themeMode == AppThemeMode.AMOLED
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val baseScheme = when {
        dynamicAvailable && ThemeController.useDynamicColor && darkTheme ->
            dynamicDarkColorScheme(context)
        dynamicAvailable && ThemeController.useDynamicColor ->
            dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    // AMOLED always forces true blacks, including over wallpaper-derived colours.
    val colorScheme = if (amoled) darkColorSchemeForAmoled() else baseScheme
    ThemeController.isEffectiveDark = darkTheme

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
