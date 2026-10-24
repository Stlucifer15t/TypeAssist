package com.typeassist.app.data

import android.content.SharedPreferences
import com.google.gson.Gson

/**
 * Persists the fact that an AI request is in flight, so a process death (Android reclaiming the
 * service under memory pressure) does not silently swallow the user's request.
 *
 * Only request metadata is saved — provider, prompt, user text, whether a screenshot was attached —
 * never the streaming answer itself. The accessibility node the answer was being written into
 * cannot survive the process, so there is nothing meaningful to resume into: on restart the app
 * reports that the previous request was interrupted and drops the record. The value is honesty:
 * without this, a crash mid-answer looks exactly like "nothing happened".
 *
 * Lifecycle: [savePendingStream] when a request starts, [clearPendingStream] when it ends for any
 * reason (success, failure, cancel, interruption), [pendingStream] read on the next service start.
 */
class StreamRecoveryManager(private val prefs: android.content.SharedPreferences) {

    /** The metadata of one in-flight request. */
    data class PendingStream(
        val provider: String,
        val prompt: String,
        val userText: String,
        val hasImage: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val gson = Gson()
    private val key = "pending_stream"

    /** Records that a request has been sent and may still be running. */
    fun savePendingStream(provider: String, prompt: String, userText: String, hasImage: Boolean) {
        val pending = PendingStream(
            provider = provider,
            prompt = prompt,
            userText = userText,
            hasImage = hasImage
        )
        prefs.edit().putString(key, gson.toJson(pending)).apply()
    }

    /** The request was answered, failed, or cancelled: nothing is in flight any more. */
    fun clearPendingStream() {
        prefs.edit().remove(key).apply()
    }

    /** The saved in-flight request, or null when nothing is in flight. */
    fun pendingStream(): PendingStream? {
        val json = prefs.getString(key, null) ?: return null
        return try {
            gson.fromJson(json, PendingStream::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /** True when a request was in flight when this process last died. */
    fun hasPendingStream(): Boolean = pendingStream() != null
}
