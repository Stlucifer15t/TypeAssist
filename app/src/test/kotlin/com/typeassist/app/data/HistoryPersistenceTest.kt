package com.typeassist.app.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The history now survives the accessibility service being restarted: it is written to disk and
 * read back. These tests pin down the round trip - what is written is what comes back, and what
 * has already expired does not come back at all.
 */
class HistoryPersistenceTest {

    private var now = 1_000_000L

    @After
    fun resetHistory() {
        HistoryManager.clock = { System.currentTimeMillis() }
        HistoryManager.clear()
    }

    private fun useClock() {
        now = 1_000_000L
        HistoryManager.clock = { now }
    }

    @Test
    fun whatIsWrittenComesBack() {
        useClock()
        HistoryManager.clear()
        HistoryManager.add("first", 60)
        now += 1_000
        HistoryManager.add("second", 60)

        val stored = HistoryManager.dump()
        HistoryManager.clear()
        assertTrue(HistoryManager.getHistory().isEmpty())

        HistoryManager.load(stored)

        assertEquals(listOf("second", "first"), HistoryManager.getHistory().map { it.text })
    }

    @Test
    fun theTimestampsSurviveTheRoundTrip() {
        useClock()
        HistoryManager.clear()
        HistoryManager.add("kept", 60)

        val stored = HistoryManager.dump()
        HistoryManager.clear()
        HistoryManager.load(stored)

        assertEquals(1_000_000L, HistoryManager.getHistory().first().timestamp)
    }

    @Test
    fun entriesThatAlreadyExpiredDoNotComeBack() {
        useClock()
        HistoryManager.clear()
        HistoryManager.add("old news", 5)

        val stored = HistoryManager.dump()
        // Five minutes and one millisecond later the entry is outside the window.
        now += 5 * 60_000L + 1
        HistoryManager.clear()
        HistoryManager.load(stored)

        assertTrue(HistoryManager.getHistory().isEmpty())
    }

    @Test
    fun aDamagedFileIsIgnoredInsteadOfCrashing() {
        useClock()
        HistoryManager.clear()
        HistoryManager.add("something", 60)

        HistoryManager.load("this is not json at all")
        assertTrue(HistoryManager.getHistory().isEmpty())

        HistoryManager.load("")
        assertTrue(HistoryManager.getHistory().isEmpty())

        HistoryManager.load(null)
        assertTrue(HistoryManager.getHistory().isEmpty())
    }

    @Test
    fun entriesWithoutTextAreSkipped() {
        useClock()
        HistoryManager.clear()

        HistoryManager.load(
            """{"items":[{"timestamp":123},{"text":"   ","timestamp":124},{"text":"real","timestamp":125}]}"""
        )

        assertEquals(listOf("real"), HistoryManager.getHistory().map { it.text })
    }

    @Test
    fun theSizeLimitsStillApplyToWhatWasLoaded() {
        useClock()
        HistoryManager.clear()
        val many = (0 until 250).joinToString(",") { """{"text":"entry $it","timestamp":${1_000_000 + it}}""" }
        HistoryManager.load("""{"items":[$many]}""")

        assertEquals(200, HistoryManager.getHistory().size)
    }
}
