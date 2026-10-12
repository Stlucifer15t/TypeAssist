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
 * The visual identity: **Graphite & Electric Lime**.
 *
 * The app is built from two families and nothing else:
 *
 *  * **Graphite** - a cool, near-neutral grey ramp that carries every surface, border and panel.
 *    Dark mode is a deep charcoal (never pure black except in AMOLED) so cards can step *up* from
 *    the canvas instead of glowing.
 *  * **Lime** - one accent hue, from a pale tint to a deep olive. Dark themes use the luminous
 *    end (`lime300`) because it is read as light on dark; the light theme uses the deep end
 *    (`lime700`) because it is read as ink on paper. Same hue, correct contrast for each. This is
 *    why the light theme is still recognisably the same product rather than a separate design.
 *
 * Rules that keep it consistent:
 *  * One accent. Emergency (error) and attention (tertiary) hues are the only other colours.
 *  * Every surface role is written out explicitly, so Material 3 never falls back to its built-in
 *    purple-grey baseline and Light / Dark / AMOLED are guaranteed to be three distinct designs.
 *  * Plain ARGB values with no Compose or Android imports, so the palettes can be checked by JVM
 *    unit tests (see `ThemePalettesTest`) and shared with the overlays, which are drawn outside the
 *    Compose tree.
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

/**
 * Colours for the floating overlays (result chip, preview card, snippet picker) in one theme.
 *
 * These are drawn by `OverlayManager` with plain Android views, so they cannot read
 * `MaterialTheme`; they are resolved from the stored theme instead. All of them are checked for
 * contrast against the surface they sit on by `ThemePalettesTest`.
 */
data class OverlayColors(
    /** Background of the preview and snippet-picker cards. */
    val cardBackground: Int,
    /** Accent outline around those cards. */
    val cardStroke: Int,
    /** Card titles and the primary card action. */
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

/**
 * The lime hue ramp. One hue, ten stops, used deliberately: the luminous stops carry the accent in
 * dark themes, the deep stops carry it in the light theme. Kept here (rather than in Theme.kt) so
 * the overlay colours can use the same values without a Compose dependency.
 */
object LimeScale {
    const val LIME_50 = 0xFFF7FDE8.toInt()
    const val LIME_100 = 0xFFEDF9D4.toInt()
    const val LIME_200 = 0xFFDDF4AE.toInt()
    const val LIME_300 = 0xFFCBF56A.toInt()
    const val LIME_400 = 0xFFB8E94B.toInt()
    const val LIME_500 = 0xFFA5DC2C.toInt()
    const val LIME_600 = 0xFF86B31C.toInt()
    const val LIME_700 = 0xFF4F6B14.toInt()
    const val LIME_800 = 0xFF3A4E10.toInt()
    const val LIME_900 = 0xFF25330A.toInt()
    const val LIME_950 = 0xFF171E06.toInt()
}

/** The graphite ramp behind every surface. Also shared with the overlays. */
object GraphiteScale {
    const val GRAPHITE_1000 = 0xFF000000.toInt()
    const val GRAPHITE_975 = 0xFF070809.toInt()
    const val GRAPHITE_950 = 0xFF0A0B0D.toInt()
    const val GRAPHITE_925 = 0xFF0E1012.toInt()
    const val GRAPHITE_900 = 0xFF0E0F11.toInt()
    const val GRAPHITE_875 = 0xFF141518.toInt()
    const val GRAPHITE_850 = 0xFF16181C.toInt()
    const val GRAPHITE_800 = 0xFF1A1C21.toInt()
    const val GRAPHITE_750 = 0xFF202329.toInt()
    const val GRAPHITE_700 = 0xFF262A31.toInt()
    const val GRAPHITE_650 = 0xFF31353D.toInt()
    const val GRAPHITE_500 = 0xFF5A606B.toInt()
    const val GRAPHITE_400 = 0xFF8A9099.toInt()
    const val GRAPHITE_300 = 0xFFA7ADB8.toInt()
    const val GRAPHITE_100 = 0xFFECEEF2.toInt()
    const val GRAPHITE_200 = 0xFFDDDFE4.toInt()
}

object ThemePalettes {

    /**
     * Light is paper: a warm-neutral canvas, white cards and a low-contrast container ladder that
     * steps *down* from white. Borders do the separating, not shadows.
     */
    val LIGHT = SurfacePalette(
        background = 0xFFF6F7F4.toInt(),
        onBackground = 0xFF15171A.toInt(),
        surface = 0xFFFFFFFF.toInt(),
        onSurface = 0xFF15171A.toInt(),
        surfaceVariant = 0xFFEDEFEA.toInt(),
        onSurfaceVariant = 0xFF5B6169.toInt(),
        outline = 0xFF8A9099.toInt(),
        outlineVariant = 0xFFDDE0DA.toInt(),
        surfaceDim = 0xFFE4E6E1.toInt(),
        surfaceBright = 0xFFFFFFFF.toInt(),
        surfaceContainerLowest = 0xFFFFFFFF.toInt(),
        surfaceContainerLow = 0xFFFAFBF8.toInt(),
        surfaceContainer = 0xFFF4F6F1.toInt(),
        surfaceContainerHigh = 0xFFEFF1EC.toInt(),
        surfaceContainerHighest = 0xFFE9ECE6.toInt()
    )

    /**
     * Dark is deep charcoal. The canvas is deliberately a few stops off black so that raised cards,
     * sheets and the navigation bar can be lighter than it - the classic "surface steps up" model.
     */
    val DARK = SurfacePalette(
        background = 0xFF0E0F11.toInt(),
        onBackground = 0xFFECEEF2.toInt(),
        surface = 0xFF141518.toInt(),
        onSurface = 0xFFECEEF2.toInt(),
        surfaceVariant = 0xFF1E2026.toInt(),
        onSurfaceVariant = 0xFFA7ADB8.toInt(),
        outline = 0xFF5A606B.toInt(),
        outlineVariant = 0xFF31353D.toInt(),
        surfaceDim = 0xFF0A0B0D.toInt(),
        surfaceBright = 0xFF2F333B.toInt(),
        surfaceContainerLowest = 0xFF0A0B0D.toInt(),
        surfaceContainerLow = 0xFF16181C.toInt(),
        surfaceContainer = 0xFF1A1C21.toInt(),
        surfaceContainerHigh = 0xFF202329.toInt(),
        surfaceContainerHighest = 0xFF262A31.toInt()
    )

    /**
     * AMOLED is true black, with containers stepping up through near-black tones. Cards are almost
     * invisible until the hairline border defines them, which is the point: it saves power and lets
     * the lime accent do all of the signalling.
     */
    val AMOLED = SurfacePalette(
        background = 0xFF000000.toInt(),
        onBackground = 0xFFF2F3F6.toInt(),
        surface = 0xFF070809.toInt(),
        onSurface = 0xFFF2F3F6.toInt(),
        surfaceVariant = 0xFF141619.toInt(),
        onSurfaceVariant = 0xFFAEB4BF.toInt(),
        outline = 0xFF5E646F.toInt(),
        outlineVariant = 0xFF282C33.toInt(),
        surfaceDim = 0xFF000000.toInt(),
        surfaceBright = 0xFF22262C.toInt(),
        surfaceContainerLowest = 0xFF000000.toInt(),
        surfaceContainerLow = 0xFF0A0B0D.toInt(),
        surfaceContainer = 0xFF0E1012.toInt(),
        surfaceContainerHigh = 0xFF141618.toInt(),
        surfaceContainerHighest = 0xFF191C20.toInt()
    )

    private val OVERLAY_LIGHT = OverlayColors(
        cardBackground = 0xFFFFFFFF.toInt(),
        cardStroke = 0xFF4F6B14.toInt(),
        titleText = 0xFF3F560F.toInt(),
        bodyText = 0xFF15171A.toInt(),
        mutedText = 0xFF5B6169.toInt(),
        divider = 0xFFE3E6E0.toInt(),
        chipBackground = 0xF7FFFFFF.toInt(),
        chipStroke = 0x3D4F6B14,
        chipDivider = 0x1A000000,
        accept = 0xFF2E6A1F.toInt(),
        reject = 0xFFB3261E.toInt(),
        accent = 0xFF4F6B14.toInt()
    )

    private val OVERLAY_DARK = OverlayColors(
        cardBackground = 0xFF1A1C21.toInt(),
        cardStroke = 0xFF45514A.toInt(),
        titleText = 0xFFCBF56A.toInt(),
        bodyText = 0xFFECEEF2.toInt(),
        mutedText = 0xFFA7ADB8.toInt(),
        divider = 0xFF31353D.toInt(),
        chipBackground = 0xF2141518.toInt(),
        chipStroke = 0x59CBF56A,
        chipDivider = 0x26FFFFFF,
        accept = 0xFF8FE07A.toInt(),
        reject = 0xFFFF8A80.toInt(),
        accent = 0xFFCBF56A.toInt()
    )

    private val OVERLAY_AMOLED = OverlayColors(
        cardBackground = 0xFF0E1012.toInt(),
        cardStroke = 0xFF45514A.toInt(),
        titleText = 0xFFCBF56A.toInt(),
        bodyText = 0xFFF2F3F6.toInt(),
        mutedText = 0xFFAEB4BF.toInt(),
        divider = 0xFF282C33.toInt(),
        chipBackground = 0xF20A0B0D.toInt(),
        chipStroke = 0x59CBF56A,
        chipDivider = 0x26FFFFFF,
        accept = 0xFF8FE07A.toInt(),
        reject = 0xFFFF8A80.toInt(),
        accent = 0xFFCBF56A.toInt()
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
