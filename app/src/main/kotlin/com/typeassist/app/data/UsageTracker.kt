package com.typeassist.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Lightweight local usage analytics: every AI request is recorded with its provider,
 * model, the command/trigger that caused it, the size of the result and whether it
 * succeeded. Stored as a rolling JSON list in SharedPreferences (no network, no tracking).
 */
object UsageTracker {
    data class UsageEvent(
        val timestamp: Long = 0L,
        val provider: String = "",
        val model: String = "",
        val command: String = "",
        val words: Int = 0,
        val success: Boolean = true
    )

    data class Bucket(val label: String, val count: Int)

    data class Summary(
        val totalRequests: Int,
        val successfulRequests: Int,
        val totalWords: Int,
        val todayRequests: Int,
        val topCommands: List<Bucket>,
        val topModels: List<Bucket>,
        val retainedDays: Int
    )

    private const val PREFS_NAME = "typeassist_usage"
    private const val KEY_EVENTS = "usage_events"
    private const val MAX_EVENTS = 1000
    private const val RETAINED_DAYS = 30

    private val gson = Gson()
    private val events = mutableListOf<UsageEvent>()
    private val lock = Any()
    @Volatile private var loaded = false
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun record(provider: String, model: String, command: String, words: Int, success: Boolean) {
        synchronized(lock) {
            ensureLoadedLocked()
            events.add(UsageEvent(System.currentTimeMillis(), provider, model, command, words.coerceAtLeast(0), success))
            pruneLocked()
            persistLocked()
        }
    }

    fun summary(): Summary {
        synchronized(lock) {
            ensureLoadedLocked()
            pruneLocked()
            val startOfToday = startOfTodayMillis()
            val successful = events.count { it.success }
            val today = events.count { it.timestamp >= startOfToday }
            val commands = events.filter { it.success }
                .groupingBy { it.command.ifBlank { "other" } }
                .eachCount()
            val models = events.filter { it.success }
                .groupingBy { it.model.ifBlank { "unknown" } }
                .eachCount()
            return Summary(
                totalRequests = events.size,
                successfulRequests = successful,
                totalWords = events.filter { it.success }.sumOf { it.words },
                todayRequests = today,
                topCommands = commands.entries.sortedByDescending { it.value }.take(6)
                    .map { Bucket(it.key, it.value) },
                topModels = models.entries.sortedByDescending { it.value }.take(6)
                    .map { Bucket(it.key, it.value) },
                retainedDays = RETAINED_DAYS
            )
        }
    }

    fun clear() {
        synchronized(lock) {
            events.clear()
            persistLocked()
        }
    }

    private fun pruneLocked() {
        val cutoff = System.currentTimeMillis() - RETAINED_DAYS * 24L * 60L * 60L * 1000L
        events.removeAll { it.timestamp < cutoff }
        while (events.size > MAX_EVENTS) {
            events.removeAt(0)
        }
    }

    private fun startOfTodayMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun ensureLoadedLocked() {
        if (loaded) return
        val context = appContext
        if (context != null) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val json = prefs.getString(KEY_EVENTS, null)
                if (json != null) {
                    val type = object : TypeToken<List<UsageEvent>>() {}.type
                    val stored: List<UsageEvent> = gson.fromJson(json, type)
                    events.clear()
                    events.addAll(stored.filterNotNull())
                }
            } catch (_: Exception) {
                events.clear()
            }
        }
        loaded = true
    }

    private fun persistLocked() {
        val context = appContext ?: return
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_EVENTS, gson.toJson(events.toList())).apply()
        } catch (_: Exception) {
        }
    }
}
