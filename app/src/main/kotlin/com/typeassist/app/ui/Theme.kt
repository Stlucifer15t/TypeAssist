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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.typeassist.app.R

/**
 * ============================== THEME ==============================
 *
 * One identity, three appearances: Light, Dark and AMOLED black. Everything the UI draws comes from
 * here - the palettes in `ThemePalettes`, the type scale below, the shape ladder and the spacing
 * tokens. Screens are expected to use the tokens; raw `Color(0x...)`, raw `fontSize = ...sp` and
 * ad-hoc corner radii are what made the old UI feel assembled rather than designed, and they are
 * deliberately not part of this file's vocabulary.
 *
 * The accent is a single lime hue (see `LimeScale`). Dark themes draw it at the luminous end, the
 * light theme draws it at the deep end, so the product reads the same in both.
 */

/** Extra roles Material 3 has no slot for. Reached through [AppTheme.extra]. */
data class AppExtraColors(
    /** Positive confirmations: service active, provider connected, permissions granted. */
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    /** Attention without failure: recommended-but-optional items, skipped stability settings. */
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    /** A soft lime wash for a selected row or an accent-tinted panel. */
    val accentSoft: Color,
    /** Text/icon colour that sits on [accentSoft]. */
    val onAccentSoft: Color,
    /** Hairline used for the 1dp borders that separate grouped rows. */
    val hairline: Color
)

private val LightExtras = AppExtraColors(
    success = Color(0xFF2E6A1F),
    successContainer = Color(0xFFE2F4DC),
    onSuccessContainer = Color(0xFF16330D),
    warning = Color(0xFF7A5A12),
    warningContainer = Color(0xFFF7EBCB),
    onWarningContainer = Color(0xFF2A1F04),
    accentSoft = Color(LimeScale.LIME_100),
    onAccentSoft = Color(LimeScale.LIME_900),
    hairline = Color(0xFFDDE0DA)
)

private val DarkExtras = AppExtraColors(
    success = Color(0xFF8FE07A),
    successContainer = Color(0xFF162B14),
    onSuccessContainer = Color(0xFFB7F0A6),
    warning = Color(0xFFE8C67A),
    warningContainer = Color(0xFF33280F),
    onWarningContainer = Color(0xFFF6E5BE),
    accentSoft = Color(LimeScale.LIME_900),
    onAccentSoft = Color(LimeScale.LIME_200),
    hairline = Color(0xFF31353D)
)

private val AmoledExtras = DarkExtras.copy(
    accentSoft = Color(0xFF141B08),
    hairline = Color(0xFF282C33)
)

private val LocalAppExtraColors = staticCompositionLocalOf { DarkExtras }

private fun lightScheme(surfaces: SurfacePalette): ColorScheme = lightColorScheme(
    primary = Color(LimeScale.LIME_700),
    onPrimary = Color.White,
    primaryContainer = Color(LimeScale.LIME_100),
    onPrimaryContainer = Color(LimeScale.LIME_900),
    inversePrimary = Color(LimeScale.LIME_300),
    secondary = Color(0xFF4A5058),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7EAE4),
    onSecondaryContainer = Color(0xFF1B1E22),
    tertiary = Color(0xFF7A5A12),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7EBCB),
    onTertiaryContainer = Color(0xFF2A1F04),
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
    scrim = Color(0xFF15171A),
    inverseSurface = Color(0xFF2B2E33),
    inverseOnSurface = Color(0xFFF2F3F6),
    surfaceDim = Color(surfaces.surfaceDim),
    surfaceBright = Color(surfaces.surfaceBright),
    surfaceContainerLowest = Color(surfaces.surfaceContainerLowest),
    surfaceContainerLow = Color(surfaces.surfaceContainerLow),
    surfaceContainer = Color(surfaces.surfaceContainer),
    surfaceContainerHigh = Color(surfaces.surfaceContainerHigh),
    surfaceContainerHighest = Color(surfaces.surfaceContainerHighest)
)

private fun darkScheme(surfaces: SurfacePalette): ColorScheme = darkColorScheme(
    primary = Color(LimeScale.LIME_300),
    onPrimary = Color(LimeScale.LIME_950),
    primaryContainer = Color(LimeScale.LIME_900),
    onPrimaryContainer = Color(LimeScale.LIME_200),
    inversePrimary = Color(LimeScale.LIME_700),
    secondary = Color(0xFFAEB6C2),
    onSecondary = Color(0xFF1B1E22),
    secondaryContainer = Color(0xFF24272D),
    onSecondaryContainer = Color(0xFFDDE1E8),
    tertiary = Color(0xFFE8C67A),
    onTertiary = Color(0xFF3A2C0B),
    tertiaryContainer = Color(0xFF33280F),
    onTertiaryContainer = Color(0xFFF6E5BE),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3A0F0C),
    errorContainer = Color(0xFF471210),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(surfaces.background),
    onBackground = Color(surfaces.onBackground),
    surface = Color(surfaces.surface),
    onSurface = Color(surfaces.onSurface),
    surfaceVariant = Color(surfaces.surfaceVariant),
    onSurfaceVariant = Color(surfaces.onSurfaceVariant),
    outline = Color(surfaces.outline),
    outlineVariant = Color(surfaces.outlineVariant),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFECEEF2),
    inverseOnSurface = Color(0xFF15171A),
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

/** The colour scheme for a stored Appearance value, with System resolved against the device. */
private fun appColorScheme(resolvedMode: String): ColorScheme = when (resolvedMode) {
    AppThemeMode.DARK -> DarkColorScheme
    AppThemeMode.AMOLED -> AmoledColorScheme
    else -> LightColorScheme
}

/** Matching extras for a resolved mode. */
private fun appExtraColors(resolvedMode: String): AppExtraColors = when (resolvedMode) {
    AppThemeMode.DARK -> DarkExtras
    AppThemeMode.AMOLED -> AmoledExtras
    else -> LightExtras
}

/**
 * ============================== TYPE ==============================
 *
 * The default Material type scale is tuned for large marketing surfaces: it is loose at the top and
 * vague in the middle. This scale tightens tracking as size grows, sets a firm three-step title
 * ramp for headers, and keeps body text at 14-15sp with generous line height for comfortable
 * reading on a phone. Weight does the hierarchy work, not size.
 */
val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.8).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp
    )
)

/**
 * The app's own typeface: Nunito, bundled as four static instances (4/5/6/700) so it renders
 * identically on every Android version the app supports - a variable font would need API 26+.
 * Nunito is SIL Open Font License 1.1; the licence ships in assets/licenses/Nunito-OFL.txt.
 *
 * Weight is what carries hierarchy here, so the family has to provide each weight rather than
 * relying on a synthetic bold.
 */
val AppFontFamily: FontFamily = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_medium, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold)
)

/** Monospace is the voice of the product: every trigger, pattern and code fragment. */
val MonoFamily: FontFamily = FontFamily.Monospace

/**
 * ============================== SHAPE ==============================
 *
 * Deliberately restrained. 12-14dp is the workhorse (cards, panels, sheets); larger radii are only
 * used for the pill-shaped elements (chips, the floating overlay) which build their own shape. The
 * old 24-32dp balloon cards are gone.
 */
private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
)

/**
 * ============================== LAYOUT TOKENS ==============================
 *
 * One spacing scale, one set of control heights. Every screen lays out on a 4dp grid using these,
 * so padding is consistent across the app instead of being re-guessed per screen.
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** Horizontal screen padding. */
    val screen = 16.dp

    /** Vertical gap between two distinct groups of content. */
    val groupGap = 20.dp

    /** Inner padding of a card or panel. */
    val card = 14.dp

    /** Left inset for a grouped row. */
    val rowStart = 14.dp
    val rowEnd = 14.dp
    val rowVertical = 12.dp
}

object Sizes {
    /** Minimum touch target, per the platform accessibility guidance. */
    val touchTarget = 48.dp
    val topBar = 56.dp
    val button = 48.dp
    val buttonSmall = 36.dp
    val switchRowMin = 56.dp
    val hairline = 1.dp
    val iconSm = 16.dp
    val icon = 20.dp
    val iconLg = 24.dp
    val statusDot = 7.dp
}

@Composable
fun AppTheme(
    themeMode: String = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val resolvedMode = ThemePalettes.resolveMode(themeMode, systemDark)
    val darkTheme = resolvedMode != AppThemeMode.LIGHT
    val colorScheme = appColorScheme(resolvedMode)
    val extras = appExtraColors(resolvedMode)
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

    CompositionLocalProvider(LocalAppExtraColors provides extras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

/**
 * Reaches the roles Material 3 does not define, without a second theme object:
 * `MaterialTheme.extra.success`, `MaterialTheme.extra.hairline`. Import it alongside MaterialTheme.
 */
val MaterialTheme.extra: AppExtraColors
    @Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = LocalAppExtraColors.current
