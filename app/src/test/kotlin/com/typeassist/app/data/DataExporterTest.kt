package com.typeassist.app.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The export format, pinned down here: one row per line, every field quoted, embedded newlines
 * and quotes kept inside their field. The usage export carries counters only; the history export
 * carries the user's own text.
 */
class DataExporterTest {

    @After
    fun reset() {
        HistoryManager.clock = { System.currentTimeMillis() }
        HistoryManager.clear()
        UsageTracker.clearAll()
    }

    @Test
    fun theUsageExportContainsOnlyCounters() {
        UsageTracker.record(
            provider = "gemini",
            model = "gemini-3.5-flash-lite",
            promptText = "some prompt text",
            answerText = "some answer text",
            reported = TokenUsage(120, 300)
        )

        val csv = DataExporter.usageCsv()

        assertTrue(
            csv.startsWith("provider,model,requests,prompt_tokens,completion_tokens,estimated_requests")
        )
        assertTrue(csv.contains("gemini"))
        assertTrue(csv.contains("gemini-3.5-flash-lite"))
        assertTrue(csv.contains("120,300"))
        // Counters only - none of the text that was sent may appear.
        assertFalse(csv.contains("some prompt text"))
        assertFalse(csv.contains("some answer text"))
    }

    @Test
    fun anEmptyUsageTableIsJustTheHeader() {
        assertEquals(
            "provider,model,requests,prompt_tokens,completion_tokens,estimated_requests",
            DataExporter.usageCsv()
        )
    }

    @Test
    fun historyRowsAreOneLineEach() {
        HistoryManager.clock = { 5_000L }
        HistoryManager.clear()
        HistoryManager.add("first entry", 60)
        HistoryManager.add("second entry", 60)

        val csv = DataExporter.historyCsv()

        // Header plus one line per entry, whatever the text contained.
        assertEquals(3, csv.lines().size)
        assertTrue(csv.startsWith("timestamp,text"))
        assertTrue(csv.contains("\"second entry\""))
        assertTrue(csv.contains("5000,\"first entry\""))
    }

    @Test
    fun quotesAreDoubledAndNewlinesAreFlattened() {
        assertEquals("\"say \"\"hi\"\" now\"", DataExporter.escape("say \"hi\" now"))
        assertEquals("\"line one line two\"", DataExporter.escape("line one\nline two"))
    }

    @Test
    fun anEmptyHistoryExportsJustTheHeader() {
        HistoryManager.clear()

        assertEquals("timestamp,text", DataExporter.historyCsv())
    }
}
