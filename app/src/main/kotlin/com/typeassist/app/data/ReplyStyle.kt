package com.typeassist.app.data

/**
 * How `.reply` writes: whose tone it copies and how long it is.
 *
 * Kept apart from the prompt text so the settings UI can list the choices and the unit tests can
 * check the rules without touching Android. Stored values are plain strings, and anything unknown
 * (an older backup, a typo, a field Gson left null) falls back to the documented default instead of
 * producing an empty instruction.
 */
object ReplyStyle {

    /** Copy the user's own voice from the conversation. */
    const val TONE_MATCH: String = "match"
    const val TONE_WARM: String = "warm"
    const val TONE_FORMAL: String = "formal"
    const val TONE_DIRECT: String = "direct"

    const val LENGTH_SHORT: String = "short"
    const val LENGTH_MEDIUM: String = "medium"
    const val LENGTH_DETAILED: String = "detailed"

    data class Option(val id: String, val name: String, val description: String)

    val TONES: List<Option> = listOf(
        Option(TONE_MATCH, "Match me", "Copy your tone, vocabulary and punctuation from the conversation."),
        Option(TONE_WARM, "Warm", "Friendly and human, without being over-familiar."),
        Option(TONE_FORMAL, "Formal", "Professional, for work, clients and official replies."),
        Option(TONE_DIRECT, "Direct", "Straight to the point, no pleasantries.")
    )

    val LENGTHS: List<Option> = listOf(
        Option(LENGTH_SHORT, "Short", "One or two sentences, about 25 words at most."),
        Option(LENGTH_MEDIUM, "Medium", "A short paragraph, roughly 40–70 words."),
        Option(LENGTH_DETAILED, "Detailed", "Up to about 120 words, with a short list when it helps.")
    )

    fun sanitizeTone(stored: String?): String =
        if (stored != null && TONES.any { it.id == stored }) stored else TONE_MATCH

    fun sanitizeLength(stored: String?): String =
        if (stored != null && LENGTHS.any { it.id == stored }) stored else LENGTH_SHORT

    fun toneName(id: String): String =
        TONES.firstOrNull { it.id == sanitizeTone(id) }?.name ?: "Match me"

    fun lengthName(id: String): String =
        LENGTHS.firstOrNull { it.id == sanitizeLength(id) }?.name ?: "Short"

    /** "Match me · Short" — the one-line summary used in Settings. */
    fun summary(tone: String?, length: String?): String =
        "${toneName(sanitizeTone(tone))} · ${lengthName(sanitizeLength(length))}"
}
