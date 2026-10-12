package com.typeassist.app.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * The one automatic retry that keeps a dropped Wi-Fi handover from losing a request — and the rules
 * that stop it from retrying things that would fail identically, or from sending the same prompt
 * twice after the model already answered.
 */
class HttpRetryTest {

    @Test
    fun oneRetryIsEnough() {
        assertEquals(2, HttpRetry.MAX_ATTEMPTS)
    }

    @Test
    fun theDelayIsShortAndCapped() {
        assertEquals(700L, HttpRetry.delayMs(1))
        assertEquals(1_400L, HttpRetry.delayMs(2))
        assertEquals(2_800L, HttpRetry.delayMs(3))
        assertEquals(5_000L, HttpRetry.delayMs(4))
        // Nothing in a request path may park the user for longer than a few seconds.
        assertEquals(5_000L, HttpRetry.delayMs(9))
    }

    @Test
    fun aProvidersOwnRetryAfterWinsWhenItIsLonger() {
        assertEquals(3_000L, HttpRetry.delayMs(1, retryAfterSeconds = 3))
        // A provider that asks for less than the backoff still gets the backoff.
        assertEquals(700L, HttpRetry.delayMs(1, retryAfterSeconds = 0))
        // ...but never more than the cap.
        assertEquals(5_000L, HttpRetry.delayMs(1, retryAfterSeconds = 600))
    }

    @Test
    fun onlyTemporaryStatusesAreRetried() {
        assertTrue(HttpRetry.shouldRetryStatus(408))
        assertTrue(HttpRetry.shouldRetryStatus(425))
        assertTrue(HttpRetry.shouldRetryStatus(429))
        assertTrue(HttpRetry.shouldRetryStatus(500))
        assertTrue(HttpRetry.shouldRetryStatus(502))
        assertTrue(HttpRetry.shouldRetryStatus(599))

        // Repeating these would only waste the user's time: the request itself is the problem.
        assertFalse(HttpRetry.shouldRetryStatus(400))
        assertFalse(HttpRetry.shouldRetryStatus(401))
        assertFalse(HttpRetry.shouldRetryStatus(403))
        assertFalse(HttpRetry.shouldRetryStatus(404))
        assertFalse(HttpRetry.shouldRetryStatus(451))
        assertFalse(HttpRetry.shouldRetryStatus(200))
    }

    @Test
    fun networkFailuresAreRetried() {
        assertTrue(HttpRetry.shouldRetry(UnknownHostException("api.example.com")))
        assertTrue(HttpRetry.shouldRetry(SocketTimeoutException("timeout")))
        assertTrue(HttpRetry.shouldRetry(SocketException("Connection reset")))
        assertTrue(HttpRetry.shouldRetry(IOException("unexpected end of stream")))
        assertTrue(HttpRetry.shouldRetry(IOException("Broken pipe")))
    }

    @Test
    fun aWrappedNetworkFailureIsStillRecognised() {
        val wrapped = IOException("request failed", SocketTimeoutException("timeout"))

        assertTrue(HttpRetry.shouldRetry(wrapped))
    }

    @Test
    fun ordinaryErrorsAreNotRetried() {
        assertFalse(HttpRetry.shouldRetry(IOException("401: invalid API key")))
        assertFalse(HttpRetry.shouldRetry(IllegalStateException("no model selected")))
        // An empty response is a configuration problem (wrong model ID), not a dropped connection.
        assertFalse(HttpRetry.shouldRetry(IOException("The provider returned an empty response.")))
    }
}
