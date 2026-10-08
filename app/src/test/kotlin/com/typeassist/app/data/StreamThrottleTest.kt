package com.typeassist.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamThrottleTest {

    private class FakeClock {
        var now = 0L
        fun advance(ms: Long) { now += ms }
    }

    @Test
    fun firstUpdateIsAlwaysAllowed() {
        val clock = FakeClock()
        val throttle = StreamThrottle(clock = { clock.now })

        assertTrue(throttle.shouldEmit())
    }

    @Test
    fun updatesAreLimitedToAboutTenPerSecond() {
        val clock = FakeClock()
        val throttle = StreamThrottle(minIntervalMs = 100L, clock = { clock.now })

        assertTrue(throttle.shouldEmit())
        // Deltas arriving faster than 100 ms are dropped.
        assertFalse(throttle.shouldEmit())
        clock.advance(40)
        assertFalse(throttle.shouldEmit())
        clock.advance(60)
        assertTrue(throttle.shouldEmit())
        clock.advance(99)
        assertFalse(throttle.shouldEmit())
        clock.advance(1)
        assertTrue(throttle.shouldEmit())
    }

    @Test
    fun resetLetsTheFinalTextThroughImmediately() {
        val clock = FakeClock()
        val throttle = StreamThrottle(minIntervalMs = 100L, clock = { clock.now })

        assertTrue(throttle.shouldEmit())
        assertFalse(throttle.shouldEmit())

        throttle.reset()

        assertTrue(throttle.shouldEmit())
    }
}
