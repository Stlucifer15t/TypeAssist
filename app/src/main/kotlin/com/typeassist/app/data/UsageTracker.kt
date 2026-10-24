package com.typeassist.app.data

import com.google.gson.Gson
import java.util.Locale
import kotlin.math.ceil

/**
 * Token counts a provider reported for one request. Providers that answer without a `usage` object
 * (or that stream) leave this null, and [UsageTracker] estimates instead.
 */
data class TokenUsage(val promptTokens: Int, val completionTokens: Int) {
    val total: Int get() = promptTokens + completionTokens
}

/**
 * ============================== TOKEN USAGE ==============================
 *
 * Counts what the assistant actually sends and receives: how many requests, and how many tokens,
 * per provider and model. The numbers answer the two questions people actually have — "is this
 * costing me anything?" and "which provider am I really using?" — without keeping any of the text.
 *
 * Two sets of counters:
 *
 *  * **session** — since the service started. Live, in memory, never written anywhere.
 *  * **all time** — the same counters merged into a stored total, so closing the app does not
 *    erase the history of how much you have used it.
 *
 * Exactness: when a provider reports token counts (Gemini, Workers AI and OpenAI-compatible
 * non-streaming answers do) those numbers are used. Everything else is estimated at
 * [CHARS_PER_TOKEN] characters per token, which is the usual rule of thumb for English text and is
 * wrong for other languages by a factor of two or three — hence [Row.estimatedRequests], shown in
 * the UI rather than hidden.
 *
 * Pure Kotlin apart from Gson for the stored form: the counting rules are unit tested, and the
 * screen reads the same objects the service wrote.
 */
object UsageTracker {

    /** The usual English approximation. Cheap, monotonic, and honest about being an estimate. */
    const val CHARS_PER_TOKEN: Int = 4

    const val PROVIDER_GEMINI: String = "gemini"
    const val PROVIDER_OPENAI: String = "custom"
    const val PROVIDER_CLOUDFLARE: String = "cloudflare"
    const val PROVIDER_LOCAL: String = "local"

    /** One provider-and-model line. [estimatedRequests] counts the requests whose numbers were guessed. */
    data class Row(
        var provider: String = PROVIDER_GEMINI,
        var model: String = "",
        var requests: Int = 0,
        var promptTokens: Long = 0,
        var completionTokens: Long = 0,
        var estimatedRequests: Int = 0,
        var updatedAt: Long = 0
    ) {
        val totalTokens: Long get() = promptTokens + completionTokens
        val key: String get() = "$provider|$model"
    }

    /**
     * Most provider-and-model rows kept at once. Every distinct model ID the user ever runs gets a
     * row, and it lives in the stored totals forever; without a cap, years of model experiments
     * would grow the stored JSON (and every screen that lists the rows) without limit. Small rows
     * are dropped first: they matter least on a "where did my tokens go" screen.
     */
    const val MAX_ROWS: Int = 50

    data class Totals(
        val requests: Int,
        val promptTokens: Long,
        val completionTokens: Long,
        val estimatedRequests: Int
    ) {
        val totalTokens: Long get() = promptTokens + completionTokens
        val hasNumbers: Boolean get() = requests > 0

        /** 0f–1f share of requests whose counts were estimated rather than reported. */
        val estimatedShare: Float
            get() = if (requests == 0) 0f else estimatedRequests.toFloat() / requests.toFloat()
    }

    /**
     * The stored form. Every field is nullable/defaulted because the JSON comes from disk: Gson
     * builds the object without running the constructor, so anything can be missing.
     */
    private data class StoredRow(
        val provider: String? = null,
        val model: String? = null,
        val requests: Int = 0,
        val promptTokens: Long = 0,
        val completionTokens: Long = 0,
        val estimatedRequests: Int = 0,
        val updatedAt: Long = 0
    )

    /** Wrapper for the stored JSON, so the file is an object and not a bare array. */
    private data class Persisted(val rows: List<StoredRow> = emptyList())

    private val gson = Gson()

    private val session = LinkedHashMap<String, Row>()
    private val allTime = LinkedHashMap<String, Row>()

    /** Replaceable so the tests do not depend on the wall clock. */
    internal var clock: () -> Long = { System.currentTimeMillis() }

    /**
     * Records one finished request. [reported] is what the provider said it used, or null when the
     * provider said nothing and the counts have to be estimated from the text.
     */
    fun record(
        provider: String,
        model: String,
        promptText: String,
        answerText: String,
        reported: TokenUsage? = null
    ) {
        val promptTokens: Long
        val completionTokens: Long
        val estimated: Boolean
        if (reported != null && reported.total > 0) {
            promptTokens = reported.promptTokens.toLong()
            completionTokens = reported.completionTokens.toLong()
            estimated = false
        } else {
            promptTokens = estimateTokens(promptText).toLong()
            completionTokens = estimateTokens(answerText).toLong()
            estimated = true
        }
        add(session, provider, model, promptTokens, completionTokens, estimated)
        add(allTime, provider, model, promptTokens, completionTokens, estimated)
    }

    private fun add(
        table: LinkedHashMap<String, Row>,
        provider: String,
        model: String,
        promptTokens: Long,
        completionTokens: Long,
        estimated: Boolean
    ) {
        val key = "$provider|$model"
        val row = table.getOrPut(key) { Row(provider = provider, model = model) }
        row.requests += 1
        row.promptTokens += promptTokens
        row.completionTokens += completionTokens
        if (estimated) row.estimatedRequests += 1
        row.updatedAt = clock()
        trim(table)
    }

    /** Keeps the [MAX_ROWS] rows with the most tokens; drops the rest, smallest first. */
    private fun trim(table: LinkedHashMap<String, Row>) {
        if (table.size <= MAX_ROWS) return
        val keep = table.values
            .sortedByDescending { it.totalTokens }
            .take(MAX_ROWS)
            .map { it.key }
            .toSet()
        table.entries.removeIf { it.key !in keep }
    }

    /** Tokens in [text], rounded up, never zero for text that exists. */
    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        return ceil(text.length.toDouble() / CHARS_PER_TOKEN.toDouble()).toInt().coerceAtLeast(1)
    }

    fun sessionRows(): List<Row> = session.values.sortedByDescending { it.totalTokens }

    fun allTimeRows(): List<Row> = allTime.values.sortedByDescending { it.totalTokens }

    fun sessionTotals(): Totals = totals(session.values)

    fun allTimeTotals(): Totals = totals(allTime.values)

    private fun totals(rows: Collection<Row>): Totals = Totals(
        requests = rows.sumOf { it.requests },
        promptTokens = rows.sumOf { it.promptTokens },
        completionTokens = rows.sumOf { it.completionTokens },
        estimatedRequests = rows.sumOf { it.estimatedRequests }
    )

    /** "since the service started" — the session is what the user is looking at right now. */
    fun clearSession() = session.clear()

    fun clearAll() {
        allTime.clear()
        session.clear()
    }

    /** "Gemini", "OpenAI-compatible", "Cloudflare Workers AI", "On-device model". */
    fun providerLabel(provider: String): String = when (provider) {
        PROVIDER_GEMINI -> "Gemini"
        PROVIDER_OPENAI -> "OpenAI-compatible"
        PROVIDER_CLOUDFLARE -> "Cloudflare Workers AI"
        PROVIDER_LOCAL -> "On-device model"
        else -> provider.replaceFirstChar { it.uppercaseChar() }
    }

    /** "1.2k", "12.3k", "1.5M" — a running total is read at a glance, not to four digits. */
    fun formatTokens(tokens: Long): String = when {
        tokens >= 1_000_000 -> String.format(Locale.US, "%.1fM", tokens / 1_000_000.0)
        tokens >= 1_000 -> String.format(Locale.US, "%.1fk", tokens / 1_000.0)
        else -> tokens.toString()
    }

    /** The model that produced a row, shortened so a long path still reads on one line. */
    fun shortModel(model: String): String {
        if (model.isBlank()) return "Not set"
        val tail = model.substringAfterLast('/')
        return if (tail.length > 28) tail.take(26) + "…" else tail
    }

    // --- Storage ---------------------------------------------------------------------------------

    fun dump(): String = gson.toJson(
        Persisted(
            allTime.values.map { row ->
                StoredRow(
                    provider = row.provider,
                    model = row.model,
                    requests = row.requests,
                    promptTokens = row.promptTokens,
                    completionTokens = row.completionTokens,
                    estimatedRequests = row.estimatedRequests,
                    updatedAt = row.updatedAt
                )
            }
        )
    )

    /** Replaces the stored totals. Unknown or damaged rows are dropped instead of trusted. */
    fun load(json: String?) {
        allTime.clear()
        if (json.isNullOrBlank()) return
        val parsed = try {
            gson.fromJson(json, Persisted::class.java)
        } catch (e: Exception) {
            null
        }
        parsed?.rows.orEmpty().forEach { stored ->
            val provider = stored.provider ?: return@forEach
            val model = stored.model.orEmpty()
            val requests = stored.requests.coerceAtLeast(0)
            allTime["$provider|$model"] = Row(
                provider = provider,
                model = model,
                requests = requests,
                promptTokens = stored.promptTokens.coerceAtLeast(0),
                completionTokens = stored.completionTokens.coerceAtLeast(0),
                estimatedRequests = stored.estimatedRequests.coerceIn(0, requests),
                updatedAt = stored.updatedAt.coerceAtLeast(0)
            )
        }
    }
}
