package com.typeassist.app.api

import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * ============================== ONE AUTOMATIC RETRY ==============================
 *
 * Provider connections drop. A phone switches from Wi-Fi to mobile data in the middle of a request,
 * a provider returns "502 Bad Gateway" for a second, a quota endpoint answers 429 once. None of
 * those need the user to retype anything, so a request that failed *before any answer arrived* is
 * sent one more time after a short pause.
 *
 * Two rules keep this safe:
 *
 *  * Only the initial failure is retried. Once the model has produced text, retrying would either
 *    duplicate work or charge for the same answer twice, so mid-answer failures are left alone (the
 *    service puts the original text back and says what happened).
 *  * A cancelled call is never retried. The user pressed Stop, and Stop must mean stop.
 *
 * Pure Java scheduling: no Android dependency, so the rules can be unit tested.
 */
object HttpRetry {

    /** One retry: a second attempt covers the transient cases without turning a failure into a wait. */
    const val MAX_ATTEMPTS: Int = 2

    private const val BASE_DELAY_MS = 700L

    /** Never wait longer than this, even if the provider asks for more: the user is waiting. */
    private const val MAX_DELAY_MS = 5_000L

    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "typeassist-retry").apply { isDaemon = true }
    }

    fun schedule(attempt: Int, retryAfterSeconds: Long? = null, action: () -> Unit) {
        scheduler.schedule(action, delayMs(attempt, retryAfterSeconds), TimeUnit.MILLISECONDS)
    }

    /** Attempt 1 is the retry right after the first try, so the delay grows from [BASE_DELAY_MS]. */
    fun delayMs(attempt: Int, retryAfterSeconds: Long? = null): Long {
        val backoff = BASE_DELAY_MS * (1L shl (attempt - 1).coerceIn(0, 3))
        val fromHeader = retryAfterSeconds?.let { (it * 1000L).coerceAtLeast(BASE_DELAY_MS) }
        return (fromHeader ?: backoff).coerceAtMost(MAX_DELAY_MS)
    }

    /** A status the provider itself describes as temporary. */
    fun shouldRetryStatus(code: Int): Boolean = when {
        code == 408 || code == 425 || code == 429 -> true
        code in 500..599 -> true
        else -> false
    }

    /**
     * A transport-level failure that is worth a second try. A 401 or a wrong URL would fail again
     * identically, so those are returned to the user immediately with a real explanation.
     */
    fun shouldRetry(error: Throwable): Boolean {
        val chain = generateSequence(error) { it.cause }.toList()
        if (chain.any { it is UnknownHostException }) return true
        if (chain.any { it is SocketTimeoutException }) return true
        if (chain.any { it is SocketException }) return true
        return chain.any { cause ->
            cause is IOException && TRANSIENT_MESSAGES.any { marker ->
                cause.message?.contains(marker, ignoreCase = true) == true
            }
        }
    }

    private val TRANSIENT_MESSAGES = listOf(
        "unexpected end of stream",
        "connection reset",
        "connection closed",
        "connection shutdown",
        "broken pipe",
        "stream was reset",
        "timeout",
        "timed out",
        "temporarily unavailable"
    )
}
