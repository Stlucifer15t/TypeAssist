package com.typeassist.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.CopyOnWriteArrayList

data class HistoryItem(
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Keeps recently processed text so it can be recovered or copied later.
 *
 * Retention: 1 hour (raised from 5 minutes), and the list is persisted to disk so
 * entries survive the app process being killed — the hour is a real hour now.
 */
object HistoryManager {
    private const val EXPIRATION_TIME_MS = 60L * 60L * 1000L // 1 hour
    private const val MAX_ITEMS = 200
    private const val PREFS_NAME = "typeassist_history"
    private const val KEY_ITEMS = "history_items"

    private val gson = Gson()
    private val history = CopyOnWriteArrayList<HistoryItem>()
    @Volatile private var loaded = false
    @Volatile private var appContext: Context? = null

    /** Call once per process (MainActivity + accessibility service) to enable persistence. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun add(text: String) {
        if (text.isBlank()) return
        ensureLoaded()
        history.add(0, HistoryItem(text))
        cleanup()
        trimToLimit()
        persist()
    }

    fun getHistory(): List<HistoryItem> {
        ensureLoaded()
        cleanup()
        return history.toList()
    }

    fun clear() {
        history.clear()
        persist()
    }

    private fun cleanup() {
        val now = System.currentTimeMillis()
        history.removeAll { now - it.timestamp > EXPIRATION_TIME_MS }
    }

    private fun trimToLimit() {
        while (history.size > MAX_ITEMS) {
            history.removeAt(history.lastIndex)
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val context = appContext ?: return
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val json = prefs.getString(KEY_ITEMS, null)
                if (json != null) {
                    val type = object : TypeToken<List<HistoryItem>>() {}.type
                    val items: List<HistoryItem> = gson.fromJson(json, type)
                    items.filter { it.text.isNotBlank() }
                        .sortedByDescending { it.timestamp }
                        .forEach { if (history.none { h -> h.timestamp == it.timestamp && h.text == it.text }) history.add(it) }
                }
            } catch (_: Exception) {
                // Corrupt or unreadable history — start fresh rather than crash.
            }
            loaded = true
            cleanup()
        }
    }

    private fun persist() {
        val context = appContext ?: return
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_ITEMS, gson.toJson(history.toList())).apply()
        } catch (_: Exception) {
        }
    }
}
