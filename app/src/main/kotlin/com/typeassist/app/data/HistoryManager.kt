package com.typeassist.app.data

import com.google.gson.Gson

/**
 * In-memory record of the text Prompt AI replaced, so nothing the user wrote is really gone.
 *
 * Two independent limits keep it honest: an age limit (how long an entry stays) and a size limit
 * (how much is kept at all). The age limit is a setting, because the old five-minute window was
 * too short to undo a mistake noticed later in the day, while the size limits are fixed: at most
 * [MAX_ITEMS] entries and [MAX_TOTAL_CHARS] characters, so a long session on a big screen cannot
 * grow the service's memory without bound.
 *
 * The entries are written to disk as well (see [HistoryStore]), so the history survives the
 * accessibility service being restarted - which happens whenever Android reclaims the app, and
 * used to empty the History tab without warning. The disk copy is only touched when a [HistoryStore]
 * is attached; without one the manager stays purely in memory, which is what the unit tests use.
 */
object HistoryManager {

    data class HistoryItem(
        val text: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    /** One hour: long enough to fix a mistake, short enough to stay private. */
    const val DEFAULT_RETENTION_MINUTES: Int = 60

    /** Retention choices offered in Settings → Behaviour → History. */
    val RETENTION_CHOICES: List<Int> = listOf(5, 30, 60, 240)

    const val MIN_RETENTION_MINUTES: Int = 1
    const val MAX_RETENTION_MINUTES: Int = 24 * 60

    private const val MAX_ITEMS = 200
    private const val MAX_TOTAL_CHARS = 200_000

    private val history = mutableListOf<HistoryItem>()
    private var retentionMinutes: Int = DEFAULT_RETENTION_MINUTES

    /** Where the history is written; null while nothing has attached one (the unit tests). */
    private var store: HistoryStore? = null

    /** Replaceable so the retention and size rules can be tested without waiting an hour. */
    internal var clock: () -> Long = { System.currentTimeMillis() }

    private val gson = Gson()

    /** The stored shape: a plain text/timestamp pair, so a damaged entry can be skipped. */
    private data class StoredItem(
        val text: String? = null,
        val timestamp: Long? = null
    )

    /** Wrapper for the stored JSON, so the file is an object and not a bare array. */
    private data class Persisted(val items: List<StoredItem> = emptyList())

    fun sanitizeRetentionMinutes(minutes: Int): Int =
        if (minutes <= 0) DEFAULT_RETENTION_MINUTES else minutes.coerceIn(MIN_RETENTION_MINUTES, MAX_RETENTION_MINUTES)

    /** Human wording for the retention setting: "1 hour", "5 minutes", "4 hours". */
    fun describeRetention(minutes: Int): String {
        val safe = sanitizeRetentionMinutes(minutes)
        return when {
            safe < 60 -> "$safe minutes"
            safe == 60 -> "1 hour"
            safe % 60 == 0 -> "${safe / 60} hours"
            else -> "$safe minutes"
        }
    }

    /**
     * Attaches the place the history is written, and reads back what is already there. Called by
     * the service when it starts, so the History tab is not empty after a restart.
     */
    fun attachStore(store: HistoryStore) {
        this.store = store
        load(store.load())
    }

    /**
     * Records the text a command replaced. [retentionMinutes] comes from the user's settings; an
     * unknown value falls back to the default instead of throwing.
     */
    fun add(text: String, retentionMinutes: Int = DEFAULT_RETENTION_MINUTES) {
        if (text.isBlank()) return
        this.retentionMinutes = sanitizeRetentionMinutes(retentionMinutes)
        history.add(0, HistoryItem(text, clock()))
        cleanup()
        store?.save(dump())
    }

    fun getHistory(): List<HistoryItem> {
        cleanup()
        return history.toList()
    }

    fun clear() {
        history.clear()
        store?.clear()
    }

    /** Entries currently held — used by the settings screen to show what is being kept. */
    fun size(): Int {
        cleanup()
        return history.size
    }

    fun currentRetentionMinutes(): Int = retentionMinutes

    fun expirationMs(): Long = retentionMinutes * 60_000L

    /** The history as a JSON string, ready to be written to disk. */
    fun dump(): String = gson.toJson(Persisted(history.map { StoredItem(it.text, it.timestamp) }))

    /**
     * Replaces the history with what was read back from disk. Damaged entries are skipped rather
     * than trusted, and entries that are already past the retention window are dropped right away.
     */
    fun load(json: String?) {
        history.clear()
        if (json.isNullOrBlank()) return
        val parsed = try {
            gson.fromJson(json, Persisted::class.java)?.items.orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        parsed.forEach { item ->
            val text = item.text ?: return@forEach
            if (text.isBlank()) return@forEach
            history.add(HistoryItem(text, item.timestamp ?: 0L))
        }
        // Newest first, and only what is still inside the retention window.
        history.sortByDescending { it.timestamp }
        cleanup()
    }

    private fun cleanup() {
        val now = clock()
        val cutoff = now - expirationMs()
        history.removeAll { it.timestamp <= cutoff }

        if (history.size > MAX_ITEMS) {
            history.subList(MAX_ITEMS, history.size).clear()
        }

        var total = 0
        var keep = 0
        for (item in history) {
            total += item.text.length
            if (total > MAX_TOTAL_CHARS) break
            keep++
        }
        if (keep < history.size) {
            history.subList(keep, history.size).clear()
        }
    }
}
