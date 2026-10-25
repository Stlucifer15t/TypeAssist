package com.typeassist.app.data

import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The record of an in-flight request, so a crash mid-answer can be reported instead of looking
 * like nothing happened. The preferences are replaced by an in-memory map, because the rules are
 * "what is written, what is read back, and what is cleared" - not how Android stores them.
 */
class StreamRecoveryManagerTest {

    /** A SharedPreferences that only keeps strings in a map. */
    private class FakePrefs : SharedPreferences {
        val values = mutableMapOf<String, String?>()

        override fun getString(key: String?, defValue: String?): String? = values[key] ?: defValue
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, String?>()
            private val removals = mutableSetOf<String>()
            override fun putString(key: String, value: String?): SharedPreferences.Editor =
                apply { pending[key] = value }
            override fun remove(key: String): SharedPreferences.Editor = apply { removals.add(key) }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                pending.forEach { (k, v) -> values[k] = v }
                removals.forEach { values.remove(it) }
                pending.clear()
                removals.clear()
            }
            override fun putInt(key: String, value: Int) = this
            override fun putLong(key: String, value: Long) = this
            override fun putFloat(key: String, value: Float) = this
            override fun putBoolean(key: String, value: Boolean) = this
            override fun putStringSet(key: String, values: MutableSet<String>?) = this
            override fun clear() = this
        }
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = emptySet<String>()
        override fun getInt(key: String?, defValue: Int) = defValue
        override fun getLong(key: String?, defValue: Long) = defValue
        override fun getFloat(key: String?, defValue: Float) = defValue
        override fun getBoolean(key: String?, defValue: Boolean) = defValue
        override fun contains(key: String?) = values.containsKey(key)
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit
    }

    private fun manager(prefs: FakePrefs = FakePrefs()): StreamRecoveryManager =
        StreamRecoveryManager(prefs)

    @Test
    fun nothingIsInFlightToBeginWith() {
        assertFalse(manager().hasPendingStream())
        assertNull(manager().pendingStream())
    }

    @Test
    fun aStartedRequestIsRemembered() {
        val prefs = FakePrefs()
        val subject = manager(prefs)

        subject.savePendingStream(
            provider = "gemini",
            prompt = "Fix this",
            userText = "teh cat",
            hasImage = true
        )

        assertTrue(subject.hasPendingStream())
        val pending = subject.pendingStream()
        assertNotNull(pending)
        assertEquals("gemini", pending!!.provider)
        assertEquals("Fix this", pending.prompt)
        assertEquals("teh cat", pending.userText)
        assertTrue(pending.hasImage)
        assertTrue("The timestamp must be recorded", pending.timestamp > 0)
    }

    @Test
    fun aFinishedRequestIsForgotten() {
        val subject = manager()
        subject.savePendingStream("custom", "p", "u", false)

        subject.clearPendingStream()

        assertFalse(subject.hasPendingStream())
    }

    @Test
    fun aStoredValueSurvivesANewManagerOnTheSamePreferences() {
        // The point of the class: the record outlives the object that wrote it.
        val prefs = FakePrefs()
        manager(prefs).savePendingStream("cloudflare", "prompt", "text", false)

        val reopened = StreamRecoveryManager(prefs)

        assertTrue(reopened.hasPendingStream())
        assertEquals("cloudflare", reopened.pendingStream()?.provider)
    }

    @Test
    fun aDamagedRecordIsTreatedAsNothing() {
        val prefs = FakePrefs()
        prefs.values["pending_stream"] = "not json at all"

        val subject = StreamRecoveryManager(prefs)

        assertFalse(subject.hasPendingStream())
        assertNull(subject.pendingStream())
    }
}
