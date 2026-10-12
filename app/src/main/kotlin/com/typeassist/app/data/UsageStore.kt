package com.typeassist.app.data

import android.content.Context

/**
 * Where the all-time token counters live between sessions.
 *
 * Stored in the app's existing preferences file, next to the configuration, because it is the same
 * kind of data: small, written often, and read at start-up. Only counters are stored — never the
 * text that was sent, and never which app it was typed into.
 *
 * The session counters are deliberately *not* stored: "since this service started" is what the
 * usage screen means by that word, and writing them on every keystroke-sized command would be
 * pointless traffic for a number nobody reads after a restart.
 */
class UsageStore(context: Context) {

    private val prefs = context.getSharedPreferences("GeminiConfig", Context.MODE_PRIVATE)

    fun load(): String? = prefs.getString(KEY_USAGE, null)

    fun save(json: String) {
        prefs.edit().putString(KEY_USAGE, json).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_USAGE).apply()
    }

    companion object {
        const val KEY_USAGE: String = "token_usage_json"
    }
}
