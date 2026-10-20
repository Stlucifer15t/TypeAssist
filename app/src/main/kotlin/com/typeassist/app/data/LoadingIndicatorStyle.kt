package com.typeassist.app.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure helpers for the floating loading indicator's look (colour + size).
 *
 * Deliberately free of any Android/Compose import so the same rules can be exercised by plain
 * JVM unit tests, and shared by `OverlayManager` (the real overlay) and the settings screen
 * (preview + pickers) without them drifting apart.
 */
object LoadingIndicatorStyle {

    /** Opaque white - the colour the indicator has always used. */
    const val DEFAULT_COLOR: Int = -1 // 0xFFFFFFFF

    /** Electric cyan - the default neon colour, and the first swatch of the neon picker. */
    const val DEFAULT_NEON_COLOR: Int = 0xFF00F5FF.toInt()

    /**
     * The neon colours on offer. Picking one re-tints the whole spectrum: the ring's gradient is
     * derived from the chosen hue rather than being locked to a fixed set of colours.
     */
    val NEON_PALETTE: List<Int> = listOf(
        0xFF00F5FF.toInt(), // Electric cyan
        0xFF0066FF.toInt(), // Laser blue
        0xFF7B2CFF.toInt(), // Ultraviolet
        0xFFFF00D4.toInt(), // Neon magenta
        0xFFFF2A6D.toInt()  // Hot pink
    )

    /** The rotating spectrum ring. Older configs stored this style under the id "neon". */
    const val STYLE_SPECTRUM: String = "spectrum"

    /** The style id older configs used for the spectrum ring. */
    const val LEGACY_STYLE_NEON: String = "neon"

    /**
     * How far the hue is rotated for each step of the spectrum. The first entry is the chosen
     * colour itself, the rest walk around the colour wheel, so the ring keeps the rainbow sweep
     * while wearing the hue the user picked.
     */
    private val SPECTRUM_HUE_OFFSETS = floatArrayOf(0f, 36f, 72f, 108f, 144f)

    /**
     * The style id from a stored value of any shape (older configs stored it untyped, and Gson can
     * hand back null). Anything unknown or blank is "classic", the same rule [sanitize] applies.
     */
    fun styleIdOf(stored: Any?): String {
        val raw = stored as? String
        return if (raw.isNullOrBlank()) "classic" else raw
    }

    /** True for the rotating spectrum ring, which is always drawn in neon colours. */
    fun isSpectrumStyle(style: String): Boolean = style == STYLE_SPECTRUM

    /**
     * The style id a stored value stands for. Older configs stored the spectrum ring under the id
     * "neon"; that is the same animation, so it is simply renamed.
     */
    fun normalizeStyle(style: String): String =
        if (style == LEGACY_STYLE_NEON) STYLE_SPECTRUM else style

    /**
     * The colour the indicator body is drawn in: the neon colour while the neon look is on, the
     * colour picked in Settings otherwise. The spectrum ring is always neon.
     */
    fun effectiveColor(style: String, picked: Int, neon: Boolean, neonColor: Int): Int =
        if (neon || isSpectrumStyle(style)) sanitizeNeonColor(neonColor) else sanitizeColor(picked)

    const val DEFAULT_SIZE_PERCENT: Int = 100
    const val MIN_SIZE_PERCENT: Int = 50
    const val MAX_SIZE_PERCENT: Int = 200

    /**
     * Swatches offered in Settings, plus a "pick your own" path for anything else. White stays first
     * (it is the shipped default), then the app's lime accent and a small, useful spread of hues.
     */
    val PRESET_COLORS: List<Int> = listOf(
        0xFFFFFFFF.toInt(), // White - the indicator's original colour
        0xFFCBF56A.toInt(), // Lime (app accent)
        0xFF22D3EE.toInt(), // Cyan
        0xFF4ADE80.toInt(), // Green
        0xFFFACC15.toInt(), // Amber
        0xFFFB7185.toInt(), // Rose
        0xFFC084FC.toInt(), // Violet
        0xFFFB923C.toInt()  // Orange
    )

    /**
     * Configs written before colour/size existed (and Java-deserialised legacy objects) report `0`
     * for the missing primitives, which would mean "fully transparent". Fall back to the defaults.
     */
    fun sanitizeColor(stored: Int): Int = if (stored == 0) DEFAULT_COLOR else stored

    /**
     * Same idea for the neon colour: `0` means "never set" (Gson writes 0 for a missing int), not
     * "transparent black", so it falls back to the default neon hue.
     */
    fun sanitizeNeonColor(stored: Int): Int = if (stored == 0) DEFAULT_NEON_COLOR else stored

    fun sanitizeSizePercent(stored: Int): Int =
        if (stored == 0) {
            DEFAULT_SIZE_PERCENT
        } else {
            stored.coerceIn(MIN_SIZE_PERCENT, MAX_SIZE_PERCENT)
        }

    /** Multiplier applied to every dp dimension of the overlay indicator. */
    fun scaleOf(sizePercent: Int): Float = sanitizeSizePercent(sizePercent) / 100f

    /**
     * The colours the spectrum ring cycles through, derived from the chosen neon colour by rotating
     * its hue. Choosing a different neon colour therefore re-tints the whole animation instead of
     * the palette being locked to one fixed rainbow.
     */
    fun neonSpectrum(base: Int): List<Int> {
        val hsv = rgbToHsv(sanitizeNeonColor(base))
        // Keep the spectrum vivid even when the picked colour is a little washed out.
        val saturation = maxOf(hsv[1], 0.75f)
        val value = maxOf(hsv[2], 0.85f)
        return SPECTRUM_HUE_OFFSETS.map { offset ->
            hsvToRgb((hsv[0] + offset) % 360f, saturation, value)
        }
    }

    /**
     * Parses `#RGB`, `#RRGGBB` or `#AARRGGBB` (the `#` is optional). Returns null when the text is
     * not a colour yet, so the caller can keep typing.
     */
    fun parseHexColor(raw: String): Int? {
        val hex = raw.trim().removePrefix("#")
        if (hex.isEmpty()) return null
        val value = hex.toLongOrNull(16) ?: return null
        return when (hex.length) {
            3 -> {
                val r = hex[0].digitToInt(16) * 17
                val g = hex[1].digitToInt(16) * 17
                val b = hex[2].digitToInt(16) * 17
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            6 -> (0xFF000000.toInt()) or value.toInt()
            8 -> value.toInt()
            else -> null
        }
    }

    /** `#RRGGBB` - what the hex field shows; alpha is always forced opaque there. */
    fun toHexRgb(color: Int): String {
        val rgb = color and 0x00FFFFFF
        return String.format(Locale.US, "#%06X", rgb)
    }

    fun withAlpha(color: Int, alpha: Int): Int {
        val clamped = alpha.coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (clamped shl 24)
    }

    fun alphaOf(color: Int): Int = (color ushr 24) and 0xFF

    /** A transparent pick would make the indicator invisible, so force it opaque. */
    fun ensureOpaque(color: Int): Int = if (alphaOf(color) == 0) withAlpha(color, 255) else color

    /** Relative luminance, used to keep the centre dot readable on any chosen colour. */
    fun luminance(color: Int): Float {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /** White core on dark colours, near-black core on pale ones. */
    fun contrastColor(color: Int): Int = if (luminance(color) > 0.6f) 0xFF101010.toInt() else 0xFFFFFFFF.toInt()

    // --- Colour space helpers -----------------------------------------------------------------
    // Android's Color.colorToHSV cannot be used here: this object is plain JVM code so the rules
    // can be unit tested without an Android device.

    /** The colour as hue (0..360), saturation (0..1) and value (0..1). */
    private fun rgbToHsv(color: Int): FloatArray {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val hue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        val saturation = if (max <= 0f) 0f else delta / max
        return floatArrayOf(if (hue < 0f) hue + 360f else hue, saturation, max)
    }

    /** The inverse of [rgbToHsv]; always fully opaque. */
    private fun hsvToRgb(hue: Float, saturation: Float, value: Float): Int {
        val chroma = value * saturation
        val hueSector = hue / 60f
        val second = chroma * (1f - abs(hueSector % 2f - 1f))
        val (r1, g1, b1) = when {
            hueSector < 1f -> Triple(chroma, second, 0f)
            hueSector < 2f -> Triple(second, chroma, 0f)
            hueSector < 3f -> Triple(0f, chroma, second)
            hueSector < 4f -> Triple(0f, second, chroma)
            hueSector < 5f -> Triple(second, 0f, chroma)
            else -> Triple(chroma, 0f, second)
        }
        val match = value - chroma
        val r = ((r1 + match) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((g1 + match) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((b1 + match) * 255f).roundToInt().coerceIn(0, 255)
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }

    /**
     * Normalises a freshly loaded config. Call it right after `Gson.fromJson(...)` because Gson
     * instantiates the class without running the constructor, so Kotlin defaults never apply.
     */
    fun sanitize(config: AppConfig): AppConfig {
        config.loadingIndicatorColor = sanitizeColor(config.loadingIndicatorColor)
        config.loadingIndicatorNeonColor = sanitizeNeonColor(config.loadingIndicatorNeonColor)
        config.loadingIndicatorSizePercent = sanitizeSizePercent(config.loadingIndicatorSizePercent)
        // "neon" used to be a style of its own. It is now the spectrum ring plus the neon look,
        // so an old config keeps its ring and gains the glow that goes with it.
        if (config.loadingIndicatorStyle == LEGACY_STYLE_NEON) {
            config.loadingIndicatorStyle = STYLE_SPECTRUM
            config.loadingIndicatorNeon = true
        }
        if (styleIdOf(config.loadingIndicatorStyle) != config.loadingIndicatorStyle) {
            config.loadingIndicatorStyle = styleIdOf(config.loadingIndicatorStyle)
        }
        return config
    }
}
