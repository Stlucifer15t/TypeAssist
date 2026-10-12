package com.typeassist.app.data

/**
 * In-memory record of the text Prompt AI replaced, so nothing the user wrote is really gone.
 *
 * Two independent limits keep it honest: an age limit (how long an entry stays) and a size limit
 * (how much is kept at all). The age limit is a setting, because the old five-minute window was
 * too short to undo a mistake noticed later in the day, while the size limits are fixed: at most
 * [MAX_ITEMS] entries and [MAX_TOTAL_CHARS] characters, so a long session on a big screen cannot
 * grow the service's memory without bound.
 *
 * Nothing here touches the disk: history lives in the accessibility service's process and is gone
 * when the service is gone.
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

    /** Replaceable so the retention and size rules can be tested without waiting an hour. */
    internal var clock: () -> Long = { System.currentTimeMillis() }

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
     * Records the text a command replaced. [retentionMinutes] comes from the user's settings; an
     * unknown value falls back to the default instead of throwing.
     */
    fun add(text: String, retentionMinutes: Int = DEFAULT_RETENTION_MINUTES) {
        if (text.isBlank()) return
        this.retentionMinutes = sanitizeRetentionMinutes(retentionMinutes)
        history.add(0, HistoryItem(text, clock()))
        cleanup()
    }

    fun getHistory(): List<HistoryItem> {
        cleanup()
        return history.toList()
    }

    fun clear() {
        history.clear()
    }

    /** Entries currently held — used by the settings screen to show what is being kept. */
    fun size(): Int {
        cleanup()
        return history.size
    }

    fun currentRetentionMinutes(): Int = retentionMinutes

    fun expirationMs(): Long = retentionMinutes * 60_000L

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
