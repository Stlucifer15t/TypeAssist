package com.typeassist.app.data

/**
 * ============================== DATA EXPORT ==============================
 *
 * Turns the token-usage counters and the command history into CSV text the user can share, save
 * or paste into a spreadsheet. Nothing here touches the UI or the disk: the caller takes the
 * string and hands it to Android's share sheet.
 *
 * The usage export contains only counters - providers, models, token counts. The history export
 * contains the text the user replaced, which is the user's own data, so it is theirs to export;
 * the share sheet makes it one tap.
 */
object DataExporter {

    /**
     * The all-time usage rows as CSV. Counters only: provider, model, request counts and tokens —
     * the same rows the usage tab shows, and never any message text.
     */
    fun usageCsv(): String {
        val header = "provider,model,requests,prompt_tokens,completion_tokens,estimated_requests"
        val rows = UsageTracker.allTimeRows().joinToString("\n") { row ->
            listOf(
                escape(row.provider),
                escape(row.model),
                row.requests.toString(),
                row.promptTokens.toString(),
                row.completionTokens.toString(),
                row.estimatedRequests.toString()
            ).joinToString(",")
        }
        return if (rows.isBlank()) header else header + "\n" + rows
    }

    /** The history entries as CSV, newest first. Contains the user's own text by design. */
    fun historyCsv(): String {
        val rows = HistoryManager.getHistory().joinToString("\n") { entry ->
            escape(entry.timestamp.toString()) + "," + escape(entry.text)
        }
        return if (rows.isBlank()) "timestamp,text" else "timestamp,text\n" + rows
    }

    /**
     * One CSV field: wrapped in quotes, with embedded quotes doubled. Newlines are replaced so a
     * single history entry stays on one row.
     */
    fun escape(value: String): String =
        "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""
}
