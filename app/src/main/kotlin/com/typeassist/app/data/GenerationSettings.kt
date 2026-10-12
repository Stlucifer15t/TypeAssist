package com.typeassist.app.data

import java.util.Locale

/**
 * ============================== GENERATION SETTINGS ==============================
 *
 * The sampling parameters every cloud provider is sent: how literal the answer is, how wide the
 * model may look for the next word, and how long the answer may get.
 *
 * Pure Kotlin on purpose. The same rules run in the settings UI (sliders), in the service (before a
 * request) and in the unit tests, and a config that arrives from a `.tabak` backup or from an older
 * install cannot smuggle in a value that breaks a request: [sanitize] clamps everything, treats a
 * missing object as the defaults, and rejects NaN/infinite values, which some JSON files contain.
 */
object GenerationSettings {

    const val DEFAULT_TEMPERATURE: Double = 0.2
    const val DEFAULT_TOP_P: Double = 0.95

    /** 0 means "leave it to the provider" — no `max_tokens` field is sent. */
    const val DEFAULT_MAX_TOKENS: Int = 0

    const val MIN_TEMPERATURE: Double = 0.0
    const val MAX_TEMPERATURE: Double = 2.0
    const val MIN_TOP_P: Double = 0.05
    const val MAX_TOP_P: Double = 1.0
    const val MAX_MAX_TOKENS: Int = 4096

    /** Step sizes used by the sliders, so the two stay in agreement. */
    const val TEMPERATURE_STEP: Double = 0.1
    const val TOP_P_STEP: Double = 0.05
    const val MAX_TOKENS_STEP: Int = 256

    fun defaults(): GenConfig = GenConfig(
        temperature = DEFAULT_TEMPERATURE,
        topP = DEFAULT_TOP_P,
        maxTokens = DEFAULT_MAX_TOKENS
    )

    /**
     * Repairs a stored configuration. Returns the same instance when nothing had to change, so
     * callers can detect work with a reference comparison (the same contract as
     * [LoadingIndicatorStyle.sanitize] and [ConfigMigrations.apply]).
     */
    fun sanitize(gen: GenConfig?): GenConfig {
        if (gen == null) return defaults()
        val temperature = clampTemperature(gen.temperature)
        val topP = clampTopP(gen.topP)
        val maxTokens = clampMaxTokens(gen.maxTokens)
        return if (temperature == gen.temperature && topP == gen.topP && maxTokens == gen.maxTokens) {
            gen
        } else {
            GenConfig(temperature = temperature, topP = topP, maxTokens = maxTokens)
        }
    }

    /**
     * The values as they go on the wire. [WireParameters.maxTokens] is null when no length field
     * should be sent at all — the difference between "let the provider decide" and "send 0", which
     * some providers reject. The clients take the numbers from here rather than reading
     * [GenConfig] themselves, so the sliders, the three providers and the tests cannot disagree.
     */
    data class WireParameters(
        val temperature: Double,
        val topP: Double,
        val maxTokens: Int?
    )

    fun wireParameters(gen: GenConfig?): WireParameters {
        val safe = sanitize(gen)
        return WireParameters(
            temperature = safe.temperature,
            topP = safe.topP,
            maxTokens = if (safe.maxTokens > 0) safe.maxTokens else null
        )
    }

    fun clampTemperature(value: Double): Double = when {
        value.isNaN() -> DEFAULT_TEMPERATURE
        value.isInfinite() -> if (value > 0) MAX_TEMPERATURE else MIN_TEMPERATURE
        else -> value.coerceIn(MIN_TEMPERATURE, MAX_TEMPERATURE)
    }

    fun clampTopP(value: Double): Double = when {
        value.isNaN() -> DEFAULT_TOP_P
        value.isInfinite() -> if (value > 0) MAX_TOP_P else MIN_TOP_P
        else -> value.coerceIn(MIN_TOP_P, MAX_TOP_P)
    }

    /** Anything below zero is meaningless here; 0 stays 0 and means "provider default". */
    fun clampMaxTokens(value: Int): Int = value.coerceIn(0, MAX_MAX_TOKENS)

    /** `0.20` — always two decimals, so the value under a slider never jumps around in width. */
    fun formatTemperature(value: Double): String = String.format(Locale.US, "%.2f", clampTemperature(value))

    fun formatTopP(value: Double): String = String.format(Locale.US, "%.2f", clampTopP(value))

    /** "Provider default" instead of a bare 0, which would read like a mistake. */
    fun formatMaxTokens(value: Int): String {
        val tokens = clampMaxTokens(value)
        return if (tokens == 0) "Provider default" else "$tokens tokens"
    }

    /** One line under the temperature slider: what the current value actually does. */
    fun describeTemperature(value: Double): String = when {
        value <= 0.05 -> "Almost always the same answer. Best for translation and factual questions."
        value <= 0.3 -> "Close to what you wrote, with almost no improvisation."
        value <= 0.7 -> "A balance of consistency and variation."
        value <= 1.2 -> "Noticeably more variety in wording."
        else -> "Very loose: answers can drift or become surprising."
    }

    /** One line under the top-p slider. */
    fun describeTopP(value: Double): String = when {
        value <= 0.3 -> "Only the most likely words. Safe but repetitive."
        value <= 0.7 -> "A narrower choice of words."
        value <= 0.95 -> "The usual setting: natural wording without nonsense."
        else -> "Considers uncommon words too, which can add character or noise."
    }

    fun describeMaxTokens(value: Int): String = when {
        clampMaxTokens(value) == 0 -> "The provider decides how long the answer may be."
        clampMaxTokens(value) <= 256 -> "Roughly a short paragraph. Long answers are cut off."
        clampMaxTokens(value) <= 1024 -> "Roughly half a page."
        else -> "Long answers allowed. Replies take longer to finish."
    }

    /** A named starting point, so nobody has to know what top-p means to change the feel. */
    data class Preset(
        val id: String,
        val name: String,
        val description: String,
        val temperature: Double,
        val topP: Double
    )

    val PRESETS: List<Preset> = listOf(
        Preset(
            id = "precise",
            name = "Precise",
            description = "Sticks to your text. Best for grammar, translation and questions.",
            temperature = 0.1,
            topP = 0.9
        ),
        Preset(
            id = "balanced",
            name = "Balanced",
            description = "The default: predictable answers with a little flexibility.",
            temperature = DEFAULT_TEMPERATURE,
            topP = DEFAULT_TOP_P
        ),
        Preset(
            id = "creative",
            name = "Creative",
            description = "More freedom in wording. Best for drafting and rewriting.",
            temperature = 0.9,
            topP = 1.0
        )
    )

    /** The preset these values match exactly, or null when they were picked by hand. */
    fun presetIdOf(temperature: Double, topP: Double): String? {
        val t = clampTemperature(temperature)
        val p = clampTopP(topP)
        return PRESETS.firstOrNull {
            kotlin.math.abs(it.temperature - t) < 0.001 && kotlin.math.abs(it.topP - p) < 0.001
        }?.id
    }

    fun presetById(id: String?): Preset? = PRESETS.firstOrNull { it.id == id }

    /** "Balanced · 0.20 · 0.95" for the settings hub, so the current values are visible at a glance. */
    fun summary(config: AppConfig?): String {
        val gen = sanitize(config?.generationConfig)
        val preset = presetIdOf(gen.temperature, gen.topP)?.let { id ->
            PRESETS.firstOrNull { it.id == id }?.name
        }
        val values = "${formatTemperature(gen.temperature)} · ${formatTopP(gen.topP)}"
        return if (preset != null) "$preset · $values" else "Custom · $values"
    }
}
