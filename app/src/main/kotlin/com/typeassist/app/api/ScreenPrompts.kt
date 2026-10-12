package com.typeassist.app.api

import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.ReplyStyle
import com.typeassist.app.utils.ScreenTextExtractor

/**
 * The instructions behind the three commands that read the screen: `.reply`, `.sum` and a `.ta`
 * question marked with `@screen`.
 *
 * Built from the configuration rather than hardcoded, so the reply tone and length chosen in
 * Settings → Behaviour actually change what the model is told. Pure Kotlin: the strings can be
 * asserted in unit tests, which is the only way to keep prompt wording from drifting.
 */
object ScreenPrompts {

    /**
     * The instruction for `.reply`.
     *
     * [voiceSample] is the user's own writing, taken from the screen by [voiceSample] and quoted
     * back at the model. Describing a tone in the abstract ("be casual") produces a generic draft;
     * showing the model two real sentences it must imitate is what makes the answer sound like the
     * person who is about to send it.
     */
    fun reply(config: AppConfig?, instruction: String = "", voiceSample: String? = null): String {
        val tone = ReplyStyle.sanitizeTone(config?.replyTone)
        val length = ReplyStyle.sanitizeLength(config?.replyLength)
        val voice = voiceSample?.trim().orEmpty()

        // The rules are numbered as they are added, so inserting one never leaves a gap or a
        // duplicate in what the model reads.
        val rules = mutableListOf<String>()
        rules += "Reply to the most recent Them: line. Use the older lines only to understand it."
        rules += "Write in the same language as the message you are replying to."
        rules += "Copy the user's voice from the Me: lines: vocabulary, sentence length, punctuation, " +
            "capitalisation, emoji and level of formality. Do not upgrade their wording, and do not " +
            "use words they would not use."
        rules += "Match the length of the message you are answering. A one-line message gets a " +
            "one-line reply; never pad a short answer to look more thorough."
        rules += "Never invent facts, times, names, prices or commitments that are not in the " +
            "conversation. If something is needed but unknown, leave it out rather than guessing."
        rules += "Return only the message text, ready to send: no labels, no quotes, no subject " +
            "line, no signature and no placeholders such as [name]."
        rules += "No markdown. No bullet lists unless the user's own messages use them."
        rules += "Avoid stiff or inflated phrasing (\"I hope this finds you well\", \"I would like " +
            "to kindly inform you\", \"delve into\"), and do not explain your reasoning."
        if (voice.isNotBlank()) {
            rules += "Here is how the user actually writes, copied from their own messages. Match " +
                "this style, not a general idea of a helpful assistant:\n---\n" + voice + "\n---"
        }
        if (instruction.isNotBlank()) {
            rules += "The user also asked for this, and it takes priority over the tone rules: " +
                instruction.trim()
        }

        val builder = StringBuilder()
        builder.append(
            "You are drafting a single message that the user will send as their reply in an " +
                "ongoing conversation. The screen content is provided below. Lines starting with " +
                "Them: were written by the other person; lines starting with Me: were written by " +
                "the user."
        )
        builder.append("\n\nRules:")
        rules.forEachIndexed { index, rule ->
            builder.append("\n").append(index + 1).append(". ").append(rule)
        }
        builder.append("\n\n").append(toneInstruction(tone))
        builder.append("\n").append(lengthInstruction(length))
        if (voice.isBlank()) {
            builder.append(
                "\nNo sample of the user's own writing was available: keep the wording plain and " +
                    "natural, and do not invent details to fill it out."
            )
        }
        return builder.toString()
    }

    /**
     * The user's own lines, pulled out of the labelled screen text for [reply].
     *
     * Longest first, because a one-word "Ok" says nothing about how somebody writes, and capped at
     * [VOICE_SAMPLE_LINES] lines and [VOICE_SAMPLE_CHARS] characters so the sample never grows the
     * prompt more than the conversation itself does. Returns null when the screen has no `Me:`
     * lines — an unlabelled screen, or a conversation the user has not spoken in yet.
     */
    fun voiceSample(screenText: String?): String? {
        val text = screenText ?: return null
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith(ME_LABEL, ignoreCase = true) }
            .map { it.substring(ME_LABEL.length).trim() }
            .filter { it.length >= MIN_VOICE_LINE_CHARS }
            .sortedByDescending { it.length }
            .take(VOICE_SAMPLE_LINES)
            .toList()
        if (lines.isEmpty()) return null
        return lines.joinToString("\n").take(VOICE_SAMPLE_CHARS)
    }

    fun summary(config: AppConfig?): String =
        "Summarize the content on the screen for someone who has not seen it. Keep only what " +
            "matters: decisions, names, numbers, dates and anything being asked for. Use one short " +
            "line per point, in the same language as the content. " +
            summaryStyleInstruction(ReplyStyle.sanitizeLength(config?.replyLength)) +
            " Return only the summary."

    fun answer(config: AppConfig?): String =
        "Answer the question using only the screen content provided. Be concise and return only " +
            "the answer. If the screen does not contain the answer, say so in one short line " +
            "instead of guessing. Write in the same language as the question."

    /**
     * The label the screen reader puts before the user's own messages. Taken from the extractor, so
     * the two can never drift apart and leave `.reply` looking for a label nothing writes.
     */
    val ME_LABEL: String = ScreenTextExtractor.LABEL_ME

    /** A line shorter than this is a "thanks" and tells the model nothing about style. */
    private const val MIN_VOICE_LINE_CHARS = 12
    private const val VOICE_SAMPLE_LINES = 2
    private const val VOICE_SAMPLE_CHARS = 400

    private fun toneInstruction(tone: String): String = when (tone) {
        ReplyStyle.TONE_WARM -> "Tone: warm and friendly, without being over-familiar."
        ReplyStyle.TONE_FORMAL -> "Tone: formal and professional."
        ReplyStyle.TONE_DIRECT -> "Tone: direct and to the point. Skip pleasantries."
        else -> "Tone: exactly the user's own tone, taken from the Me: lines."
    }

    private fun lengthInstruction(length: String): String = when (length) {
        ReplyStyle.LENGTH_MEDIUM -> "Length: a short paragraph, roughly 40-70 words."
        ReplyStyle.LENGTH_DETAILED -> "Length: up to about 120 words. Use a short list only if the content really is a list."
        else -> "Length: one or two short sentences, about 25 words at most."
    }

    /** The summary gets a matching cap, since a three-line summary is rarely what people want. */
    private fun summaryStyleInstruction(length: String): String = when (length) {
        ReplyStyle.LENGTH_DETAILED -> "Up to six points if the content is long enough to need them."
        ReplyStyle.LENGTH_MEDIUM -> "Three to four points at most."
        else -> "Two or three points."
    }
}
