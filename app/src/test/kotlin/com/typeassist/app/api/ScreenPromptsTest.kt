package com.typeassist.app.api

import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.ReplyStyle
import com.typeassist.app.data.createDefaultConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The instructions behind `.reply`, `.sum` and `.ta`. These strings are the entire difference
 * between a generic draft and one that reads like the user wrote it, so the rules the settings
 * screen promises are asserted here.
 */
class ScreenPromptsTest {

    private fun config(tone: String? = null, length: String? = null): AppConfig =
        createDefaultConfig().copy(
            replyTone = tone ?: ReplyStyle.TONE_MATCH,
            replyLength = length ?: ReplyStyle.LENGTH_SHORT
        )

    @Test
    fun theReplyPromptExplainsTheLabelledScreenText() {
        val prompt = ScreenPrompts.reply(config())

        assertTrue(prompt.contains("Them:"))
        assertTrue(prompt.contains("Me:"))
        assertTrue(prompt.contains("most recent Them: line"))
        // The answer is inserted straight into the field, so nothing decorative may come back.
        assertTrue(prompt.contains("no labels"))
        assertTrue(prompt.contains("No markdown"))
    }

    @Test
    fun everyToneHasItsOwnLine() {
        val match = ScreenPrompts.reply(config(tone = ReplyStyle.TONE_MATCH))
        val warm = ScreenPrompts.reply(config(tone = ReplyStyle.TONE_WARM))
        val formal = ScreenPrompts.reply(config(tone = ReplyStyle.TONE_FORMAL))
        val direct = ScreenPrompts.reply(config(tone = ReplyStyle.TONE_DIRECT))

        assertTrue(match.contains("the user's own tone"))
        assertTrue(warm.contains("warm and friendly"))
        assertTrue(formal.contains("formal and professional"))
        assertTrue(direct.contains("direct"))
        // Tone is a choice, not a suggestion: the three never share the same instruction.
        assertEquals(4, setOf(match, warm, formal, direct).size)
    }

    @Test
    fun lengthChangesHowLongTheDraftMayBe() {
        val short = ScreenPrompts.reply(config(length = ReplyStyle.LENGTH_SHORT))
        val medium = ScreenPrompts.reply(config(length = ReplyStyle.LENGTH_MEDIUM))
        val detailed = ScreenPrompts.reply(config(length = ReplyStyle.LENGTH_DETAILED))

        assertTrue(short.contains("about 25 words"))
        assertTrue(medium.contains("40-70 words"))
        assertTrue(detailed.contains("120 words"))
        assertEquals(3, setOf(short, medium, detailed).size)
    }

    @Test
    fun anUnknownStoredToneOrLengthFallsBackToTheDefault() {
        // What an older backup or a hand-edited config can contain.
        val prompt = ScreenPrompts.reply(config(tone = "shouty", length = "epic"))

        assertTrue(prompt.contains("the user's own tone"))
        assertTrue(prompt.contains("about 25 words"))
    }

    @Test
    fun theTypedInstructionIsAddedAsItsOwnRule() {
        val plain = ScreenPrompts.reply(config())
        val steered = ScreenPrompts.reply(config(), instruction = "keep it short and apologetic")

        assertFalse(plain.contains("keep it short and apologetic"))
        assertTrue(steered.contains("keep it short and apologetic"))
        assertTrue(steered.contains("takes priority"))
        // Nothing else about the prompt moves: the rule is inserted, not substituted.
        val withoutRule = steered.replace(
            "9. The user also asked for this, and it takes priority over the tone rules: " +
                "keep it short and apologetic",
            ""
        )
        assertEquals(plain, withoutRule)
    }

    @Test
    fun aBlankInstructionChangesNothing() {
        assertEquals(ScreenPrompts.reply(config()), ScreenPrompts.reply(config(), instruction = "   "))
    }

    @Test
    fun theSummaryLengthFollowsTheSameSetting() {
        val short = ScreenPrompts.summary(config(length = ReplyStyle.LENGTH_SHORT))
        val medium = ScreenPrompts.summary(config(length = ReplyStyle.LENGTH_MEDIUM))
        val detailed = ScreenPrompts.summary(config(length = ReplyStyle.LENGTH_DETAILED))

        assertTrue(short.contains("Two or three points"))
        assertTrue(medium.contains("Three to four points"))
        assertTrue(detailed.contains("Up to six points"))
        assertTrue(short.contains("Return only the summary"))
    }

    @Test
    fun theAnswerPromptRefusesToGuess() {
        val prompt = ScreenPrompts.answer(config())

        assertTrue(prompt.contains("only the screen content"))
        assertTrue(prompt.contains("instead of guessing"))
    }

    @Test
    fun theVoiceSampleIsQuotedBackAtTheModel() {
        // A real two-line conversation: the short "ok" says nothing about how somebody writes.
        val sample = ScreenPrompts.voiceSample(
            "Them: are we still on for six?\nMe: ok\nMe: hey, are we still on for 6? running 5 late"
        )

        assertEquals("hey, are we still on for 6? running 5 late", sample)

        val prompt = ScreenPrompts.reply(config(), voiceSample = sample)

        assertTrue(prompt.contains("Here is how the user actually writes"))
        assertTrue(prompt.contains("hey, are we still on for 6? running 5 late"))
    }

    @Test
    fun aVoiceSampleNeedsSomethingToCopy() {
        assertNull(ScreenPrompts.voiceSample(null))
        assertNull(ScreenPrompts.voiceSample(""))
        // Only the other person has spoken so far.
        assertNull(ScreenPrompts.voiceSample("Them: hello there, how are you doing today"))
        // Too short to be a style.
        assertNull(ScreenPrompts.voiceSample("Me: ok"))
    }

    @Test
    fun theLongestUserLinesAreUsedAndTheSampleStaysSmall() {
        val text = (1..6).joinToString("\n") { "Me: sentence number $it, long enough to describe a style" }

        val sample = ScreenPrompts.voiceSample(text)

        assertEquals(2, sample?.lines?.size)
    }

    @Test
    fun theTypedInstructionKeepsItsPriorityBesideASample() {
        val prompt = ScreenPrompts.reply(
            config(),
            instruction = "keep it short",
            voiceSample = "hey, are we still on for 6? running 5 late"
        )

        // The instruction comes after the style sample, so the rules and the copy style are both
        // in place before the model reads what the user asked for.
        val sampleIndex = prompt.indexOf("Here is how the user actually writes")
        val instructionIndex = prompt.indexOf("keep it short")
        assertTrue(sampleIndex >= 0)
        assertTrue(instructionIndex > sampleIndex)
    }

    @Test
    fun promptsSurviveAMissingConfig() {
        listOf(
            ScreenPrompts.reply(null),
            ScreenPrompts.reply(null, "do not mention the delay"),
            ScreenPrompts.summary(null),
            ScreenPrompts.answer(null)
        ).forEach { prompt ->
            assertTrue(prompt.isNotBlank())
        }
    }
}
