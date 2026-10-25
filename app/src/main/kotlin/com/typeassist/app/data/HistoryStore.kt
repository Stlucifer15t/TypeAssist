package com.typeassist.app.data

import android.content.Context

/**
 * Where the command history lives between sessions.
 *
 * Stored in the app's existing preferences file, next to the configuration, because it is the same
 * kind of data: small, written often, and read at start-up.
 *
 * Only the text the user wrote is stored - never which app it was typed into, and never anything
 * the model answered. Entries older than the retention window are dropped when they are loaded, so
 * an old backup cannot bring back text the user has already forgotten about.
 */
class HistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("GeminiConfig", Context.MODE_PRIVATE)

    fun load(): String? = prefs.getString(KEY_HISTORY, null)

    fun save(json: String) {
        prefs.edit().putString(KEY_HISTORY, json).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    companion object {
        const val KEY_HISTORY: String = "history_json"
    }
}
