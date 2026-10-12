package com.typeassist.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tone and length behind `.reply`. Stored as plain strings so a backup from an older version
 * cannot break the screen, which means anything unknown has to fall back to the documented default.
 */
class ReplyStyleTest {

    @Test
    fun theDefaultsMatchTheSettingsScreen() {
        assertEquals(ReplyStyle.TONE_MATCH, ReplyStyle.sanitizeTone(null))
        assertEquals(ReplyStyle.LENGTH_SHORT, ReplyStyle.sanitizeLength(null))
    }

    @Test
    fun knownValuesAreKept() {
        ReplyStyle.TONES.forEach { option ->
            assertEquals(option.id, ReplyStyle.sanitizeTone(option.id))
        }
        ReplyStyle.LENGTHS.forEach { option ->
            assertEquals(option.id, ReplyStyle.sanitizeLength(option.id))
        }
    }

    @Test
    fun unknownValuesBecomeTheDefault() {
        assertEquals(ReplyStyle.TONE_MATCH, ReplyStyle.sanitizeTone("shouty"))
        assertEquals(ReplyStyle.TONE_MATCH, ReplyStyle.sanitizeTone(""))
        assertEquals(ReplyStyle.LENGTH_SHORT, ReplyStyle.sanitizeLength("epic"))
    }

    @Test
    fun everyOptionIsDescribedForTheSettingsScreen() {
        (ReplyStyle.TONES + ReplyStyle.LENGTHS).forEach { option ->
            assertTrue(option.name.isNotBlank())
            assertTrue(option.description.isNotBlank())
        }
    }

    @Test
    fun namesAreResolvedThroughTheSameSanitising() {
        assertEquals("Warm", ReplyStyle.toneName(ReplyStyle.TONE_WARM))
        assertEquals("Match me", ReplyStyle.toneName("nonsense"))
        assertEquals("Detailed", ReplyStyle.lengthName(ReplyStyle.LENGTH_DETAILED))
        assertEquals("Short", ReplyStyle.lengthName("nonsense"))
    }

    @Test
    fun theSummaryReadsLikeASettingsRow() {
        assertEquals("Match me · Short", ReplyStyle.summary(null, null))
        assertEquals("Formal · Medium", ReplyStyle.summary("formal", "medium"))
        assertFalse(ReplyStyle.summary("formal", "medium").contains("  "))
    }
}
