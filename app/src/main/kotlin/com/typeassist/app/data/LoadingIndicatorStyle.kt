package com.typeassist.app.data

import java.util.Locale

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

    /** Built-in, high-saturation colours used by the animated neon spectrum (and locked in Settings). */
    val NEON_PALETTE: List<Int> = listOf(
        0xFF00F5FF.toInt(), // Electric cyan
        0xFF0066FF.toInt(), // Laser blue
        0xFF7B2CFF.toInt(), // Ultraviolet
        0xFFFF00D4.toInt(), // Neon magenta
        0xFFFF2A6D.toInt()  // Hot pink
    )

    /** Neon uses its own vivid palette; other styles use the user's selected colour. */
    fun effectiveColor(style: String, picked: Int): Int =
        if (style == "neon") NEON_PALETTE.first() else sanitizeColor(picked)

    const val DEFAULT_SIZE_PERCENT: Int = 100
    const val MIN_SIZE_PERCENT: Int = 50
    const val MAX_SIZE_PERCENT: Int = 200

    /** Swatches offered in Settings, plus a "pick your own" path for anything else. */
    val PRESET_COLORS: List<Int> = listOf(
        0xFFFFFFFF.toInt(), // White
        0xFF818CF8.toInt(), // Indigo (app accent)
        0xFF22D3EE.toInt(), // Cyan
        0xFF4ADE80.toInt(), // Green
        0xFFFACC15.toInt(), // Amber
        0xFFFB7185.toInt(), // Rose
        0xFFC084FC.toInt(), // Purple
        0xFFFB923C.toInt()  // Orange
    )

    /**
     * Configs written before colour/size existed (and Java-deserialised legacy objects) report `0`
     * for the missing primitives, which would mean "fully transparent". Fall back to the defaults.
     */
    fun sanitizeColor(stored: Int): Int = if (stored == 0) DEFAULT_COLOR else stored

    fun sanitizeSizePercent(stored: Int): Int =
        if (stored == 0) {
            DEFAULT_SIZE_PERCENT
        } else {
            stored.coerceIn(MIN_SIZE_PERCENT, MAX_SIZE_PERCENT)
        }

    /** Multiplier applied to every dp dimension of the overlay indicator. */
    fun scaleOf(sizePercent: Int): Float = sanitizeSizePercent(sizePercent) / 100f

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

    /**
     * Normalises a freshly loaded config. Call it right after `Gson.fromJson(...)` because Gson
     * instantiates the class without running the constructor, so Kotlin defaults never apply.
     */
    fun sanitize(config: AppConfig): AppConfig {
        config.loadingIndicatorColor = sanitizeColor(config.loadingIndicatorColor)
        config.loadingIndicatorSizePercent = sanitizeSizePercent(config.loadingIndicatorSizePercent)
        val style = config.loadingIndicatorStyle as String?
        if (style.isNullOrBlank()) config.loadingIndicatorStyle = "classic"
        return config
    }
}
