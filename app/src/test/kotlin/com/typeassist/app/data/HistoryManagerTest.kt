package com.typeassist.app.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The retention setting the user picks in Settings → Behaviour, and the two size limits that keep a
 * long session from growing without bound. The clock is replaced so the one-hour default can be
 * checked in milliseconds instead of an hour.
 */
class HistoryManagerTest {

    private var now = 1_000_000L

    @After
    fun resetHistory() {
        // HistoryManager is a singleton, so each test starts from an empty list and the real clock.
        HistoryManager.clock = { System.currentTimeMillis() }
        HistoryManager.clear()
    }

    private fun useClock() {
        now = 1_000_000L
        HistoryManager.clock = { now }
    }

    @Test
    fun theDefaultWindowIsOneHour() {
        assertEquals(60, HistoryManager.DEFAULT_RETENTION_MINUTES)
        assertEquals("1 hour", HistoryManager.describeRetention(60))
        assertEquals("5 minutes", HistoryManager.describeRetention(5))
        assertEquals("30 minutes", HistoryManager.describeRetention(30))
        assertEquals("4 hours", HistoryManager.describeRetention(240))
        assertEquals("90 minutes", HistoryManager.describeRetention(90))
    }

    @Test
    fun retentionFallsBackToTheDefaultWhenUnset() {
        assertEquals(60, HistoryManager.sanitizeRetentionMinutes(0))
        assertEquals(60, HistoryManager.sanitizeRetentionMinutes(-30))
        assertEquals(1, HistoryManager.sanitizeRetentionMinutes(1))
        assertEquals(HistoryManager.MAX_RETENTION_MINUTES, HistoryManager.sanitizeRetentionMinutes(99_999))
    }

    @Test
    fun theDefaultKeepsTextThatTheOldFiveMinuteWindowDropped() {
        useClock()
        HistoryManager.clear()
        HistoryManager.add("half an hour ago", HistoryManager.DEFAULT_RETENTION_MINUTES)

        now += 30 * 60_000L

        assertEquals(1, HistoryManager.getHistory().size)
    }

    @Test
    fun entriesDisappearOnceTheWindowHasPassed() {
        useClock()
        HistoryManager.clear()
        HistoryManager.add("recent", 5)

        now += 5 * 60_000L + 1

        assertTrue(HistoryManager.getHistory().isEmpty())
        assertEquals(0, HistoryManager.size())
    }

    @Test
    fun theWindowFromTheLatestAddDecidesWhatIsKept() {
        useClock()
        HistoryManager.clear()
        HistoryManager.add("kept for four hours", 240)

        now += 60 * 60_000L
        assertEquals(1, HistoryManager.getHistory().size)

        // Switching the setting to five minutes takes effect from the next command onwards.
        HistoryManager.add("kept for five minutes", 5)
        now += 6 * 60_000L

        assertTrue(HistoryManager.getHistory().isEmpty())
    }

    @Test
    fun newestTextComesFirst() {
        useClock()
        HistoryManager.clear()
        HistoryManager.add("first", 60)
        now += 1_000
        HistoryManager.add("second", 60)

        val items = HistoryManager.getHistory()

        assertEquals(listOf("second", "first"), items.map { it.text })
    }

    @Test
    fun blankTextIsNotRecorded() {
        useClock()
        HistoryManager.clear()
        HistoryManager.add("   ", 60)
        HistoryManager.add("", 60)

        assertEquals(0, HistoryManager.size())
    }

    @Test
    fun atMostTwoHundredEntriesAreKept() {
        useClock()
        HistoryManager.clear()
        repeat(250) { index ->
            now += 10
            HistoryManager.add("entry $index", 60)
        }

        val items = HistoryManager.getHistory()

        assertEquals(200, items.size)
        assertEquals("entry 249", items.first().text)
        assertEquals("entry 50", items.last().text)
    }

    @Test
    fun oversizedEntriesAreDroppedRatherThanKeptInMemory() {
        useClock()
        HistoryManager.clear()
        HistoryManager.add("x".repeat(250_000), 60)
        now += 10
        HistoryManager.add("small", 60)

        val items = HistoryManager.getHistory()

        assertEquals(listOf("small"), items.map { it.text })
    }

    @Test
    fun clearingRemovesEverything() {
        useClock()
        HistoryManager.clear()
        HistoryManager.add("something", 60)

        HistoryManager.clear()

        assertTrue(HistoryManager.getHistory().isEmpty())
    }
}
