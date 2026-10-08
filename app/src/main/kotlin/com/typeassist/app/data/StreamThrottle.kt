package com.typeassist.app.data

/**
 * Rate limit for writing streamed text into a text field.
 *
 * A model can emit dozens of deltas per second; rewriting the field that often fights with the
 * keyboard and wastes work, so updates are limited to roughly ten per second. The clock is
 * injectable so the rule can be unit tested without waiting in real time.
 */
class StreamThrottle(
    private val minIntervalMs: Long = DEFAULT_INTERVAL_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private var lastEmitAt: Long = Long.MIN_VALUE

    /** True when enough time has passed since the last accepted update; records the update. */
    fun shouldEmit(): Boolean {
        val now = clock()
        if (lastEmitAt != Long.MIN_VALUE && now - lastEmitAt < minIntervalMs) return false
        lastEmitAt = now
        return true
    }

    /** Allows the next call to [shouldEmit] immediately, e.g. for the final text of a stream. */
    fun reset() {
        lastEmitAt = Long.MIN_VALUE
    }

    companion object {
        /** ~10 field updates per second. */
        const val DEFAULT_INTERVAL_MS = 100L
    }
}
