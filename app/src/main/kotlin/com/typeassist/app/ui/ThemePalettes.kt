package com.typeassist.app.ui

/**
 * The Appearance choices as stored in preferences (`GeminiConfig` / `theme_mode`).
 */
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

/**
 * The neutral roles of one theme: the surfaces, backgrounds and outlines that make Light, Dark and
 * AMOLED look different. Every surface-container role is written out here, so Material 3 never
 * falls back to its built-in purple-grey baseline. That fallback is what made Dark and AMOLED look
 * the same in 4.5.0: cards, dialogs, menus and navigation bars used the `surfaceContainer*` roles,
 * which the old AMOLED scheme inherited unchanged from Dark.
 *
 * Plain ARGB values with no Compose or Android imports, so the palettes can be checked by JVM unit
 * tests and shared with the overlays, which are drawn outside the Compose tree.
 */
data class SurfacePalette(
    val background: Int,
    val onBackground: Int,
    val surface: Int,
    val onSurface: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val outlineVariant: Int,
    val surfaceDim: Int,
    val surfaceBright: Int,
    val surfaceContainerLowest: Int,
    val surfaceContainerLow: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int
)

/** Colours for the floating overlays (result chip, preview card, snippet picker) in one theme. */
data class OverlayColors(
    /** Background of the preview and snippet-picker cards. */
    val cardBackground: Int,
    /** Accent outline around those cards. */
    val cardStroke: Int,
    /** Card titles and the Copy/Cancel buttons. */
    val titleText: Int,
    /** Response text and snippet variations. */
    val bodyText: Int,
    /** Hints and the Discard button. */
    val mutedText: Int,
    /** Separators between snippet variations. */
    val divider: Int,
    /** Background of the Accept · Reject · Retry chip. */
    val chipBackground: Int,
    val chipStroke: Int,
    val chipDivider: Int,
    val accept: Int,
    val reject: Int,
    /** Retry and any other chip action. */
    val accent: Int
)

object ThemePalettes {

    /** Light keeps the surface tones Material 3 already drew, now written out explicitly. */
    val LIGHT = SurfacePalette(
        background = 0xFFF6F7FB.toInt(),
        onBackground = 0xFF191B25.toInt(),
        surface = 0xFFFFFFFF.toInt(),
        onSurface = 0xFF191B25.toInt(),
        surfaceVariant = 0xFFEEF0F6.toInt(),
        onSurfaceVariant = 0xFF626574.toInt(),
        outline = 0xFF85889A.toInt(),
        outlineVariant = 0xFFD8DAE4.toInt(),
        surfaceDim = 0xFFDED8E1.toInt(),
        surfaceBright = 0xFFFEF7FF.toInt(),
        surfaceContainerLowest = 0xFFFFFFFF.toInt(),
        surfaceContainerLow = 0xFFF7F2FA.toInt(),
        surfaceContainer = 0xFFF3EDF7.toInt(),
        surfaceContainerHigh = 0xFFECE6F0.toInt(),
        surfaceContainerHighest = 0xFFE6E0E9.toInt()
    )

    /** Dark is a lifted dark grey. The old #0D1016 background was already close to black. */
    val DARK = SurfacePalette(
        background = 0xFF14171F.toInt(),
        onBackground = 0xFFE8E9F1.toInt(),
        surface = 0xFF1A1E27.toInt(),
        onSurface = 0xFFE8E9F1.toInt(),
        surfaceVariant = 0xFF232835.toInt(),
        onSurfaceVariant = 0xFFC2C6D3.toInt(),
        outline = 0xFF8C91A1.toInt(),
        outlineVariant = 0xFF3A404D.toInt(),
        surfaceDim = 0xFF111419.toInt(),
        surfaceBright = 0xFF373D4A.toInt(),
        surfaceContainerLowest = 0xFF0D1015.toInt(),
        surfaceContainerLow = 0xFF191D26.toInt(),
        surfaceContainer = 0xFF20242E.toInt(),
        surfaceContainerHigh = 0xFF262B36.toInt(),
        surfaceContainerHighest = 0xFF2A2F3A.toInt()
    )

    /** AMOLED is true black, with containers stepping up through near-black tones. */
    val AMOLED = SurfacePalette(
        background = 0xFF000000.toInt(),
        onBackground = 0xFFF3F4FA.toInt(),
        surface = 0xFF080A0E.toInt(),
        onSurface = 0xFFF3F4FA.toInt(),
        surfaceVariant = 0xFF171A22.toInt(),
        onSurfaceVariant = 0xFFC4C8D4.toInt(),
        outline = 0xFF8C91A1.toInt(),
        outlineVariant = 0xFF303643.toInt(),
        surfaceDim = 0xFF000000.toInt(),
        surfaceBright = 0xFF262B35.toInt(),
        surfaceContainerLowest = 0xFF000000.toInt(),
        surfaceContainerLow = 0xFF0A0C11.toInt(),
        surfaceContainer = 0xFF0F1218.toInt(),
        surfaceContainerHigh = 0xFF151920.toInt(),
        surfaceContainerHighest = 0xFF1B1F29.toInt()
    )

    private val OVERLAY_LIGHT = OverlayColors(
        cardBackground = 0xFFFFFBFE.toInt(),
        cardStroke = 0xFF4F46E5.toInt(),
        titleText = 0xFF4F46E5.toInt(),
        bodyText = 0xFF1C1B1F.toInt(),
        mutedText = 0xFF49454F.toInt(),
        divider = 0xFFE7E0EC.toInt(),
        chipBackground = 0xF2FFFFFF.toInt(),
        chipStroke = 0x664F46E5,
        chipDivider = 0x1F000000,
        accept = 0xFF15803D.toInt(),
        reject = 0xFFB3261E.toInt(),
        accent = 0xFF4F46E5.toInt()
    )

    private val OVERLAY_DARK = OverlayColors(
        cardBackground = 0xFF20242E.toInt(),
        cardStroke = 0xFF818CF8.toInt(),
        titleText = 0xFF818CF8.toInt(),
        bodyText = 0xFFE8E9F1.toInt(),
        mutedText = 0xFFC2C6D3.toInt(),
        divider = 0xFF3A404D.toInt(),
        chipBackground = 0xF220242E.toInt(),
        chipStroke = 0x66818CF8,
        chipDivider = 0x33FFFFFF,
        accept = 0xFF4ADE80.toInt(),
        reject = 0xFFFB7185.toInt(),
        accent = 0xFF818CF8.toInt()
    )

    private val OVERLAY_AMOLED = OverlayColors(
        cardBackground = 0xFF0F1218.toInt(),
        cardStroke = 0xFF818CF8.toInt(),
        titleText = 0xFF818CF8.toInt(),
        bodyText = 0xFFF3F4FA.toInt(),
        mutedText = 0xFFC4C8D4.toInt(),
        divider = 0xFF303643.toInt(),
        chipBackground = 0xF20F1218.toInt(),
        chipStroke = 0x66818CF8,
        chipDivider = 0x33FFFFFF,
        accept = 0xFF4ADE80.toInt(),
        reject = 0xFFFB7185.toInt(),
        accent = 0xFF818CF8.toInt()
    )

    /**
     * Turns a stored Appearance value into the theme that is actually drawn: LIGHT, DARK or AMOLED.
     * System follows [systemDark], the device's night mode. Unknown or missing values count as System.
     */
    fun resolveMode(stored: String?, systemDark: Boolean): String {
        val mode = AppThemeMode.sanitize(stored)
        return when {
            mode != AppThemeMode.SYSTEM -> mode
            systemDark -> AppThemeMode.DARK
            else -> AppThemeMode.LIGHT
        }
    }

    /** Surface roles for a resolved mode. Anything that is not Dark or AMOLED is drawn as Light. */
    fun surfacesFor(resolvedMode: String): SurfacePalette = when (resolvedMode) {
        AppThemeMode.DARK -> DARK
        AppThemeMode.AMOLED -> AMOLED
        else -> LIGHT
    }

    /** Overlay colours for a resolved mode. Anything that is not Dark or AMOLED is drawn as Light. */
    fun overlayColors(resolvedMode: String): OverlayColors = when (resolvedMode) {
        AppThemeMode.DARK -> OVERLAY_DARK
        AppThemeMode.AMOLED -> OVERLAY_AMOLED
        else -> OVERLAY_LIGHT
    }
}
