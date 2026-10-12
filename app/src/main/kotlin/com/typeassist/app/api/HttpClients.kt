package com.typeassist.app.api

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * ============================== CONNECTION REUSE ==============================
 *
 * One place that builds OkHttp clients, so every request in the app can share the same connection
 * pool, dispatcher and thread pool.
 *
 * Why this matters for speed: building a client per request (`.newBuilder()...build()` on every
 * call) gives that client its own connection pool, so every request pays for a DNS lookup, a TCP
 * handshake and a full TLS handshake again. Sharing one pool means the second and later requests
 * to the same provider are sent over the connection that is already open — usually the difference
 * between a noticeable pause and an answer that starts immediately. HTTP/2 multiplexing comes with
 * it, since OkHttp reuses one connection for parallel calls when the server supports it.
 *
 * Connections are kept for [KEEP_ALIVE_MINUTES] idle minutes, which covers the way the app is
 * really used: type a command, read the answer, type the next one.
 */
object HttpClients {

    /**
     * How long a request may take to *establish* a connection. Deliberately shorter than a
     * generation timeout: a dead network should say so in seconds, while a slow model is still
     * allowed the full timeout the user configured.
     */
    const val CONNECT_TIMEOUT_SECONDS: Long = 15L

    private const val KEEP_ALIVE_MINUTES = 5L
    private const val MAX_IDLE_CONNECTIONS = 5

    /**
     * Key for the model-catalogue client below. Negative on purpose: it can never collide with a
     * real timeout, which is always a positive number of seconds.
     */
    private const val CATALOG_KEY = -25L

    /** Used when a caller passes a nonsensical timeout, so the app never sends an unbounded request. */
    const val DEFAULT_TIMEOUT_SECONDS: Long = 60L

    /**
     * The shared client. Built once, with the timeouts that apply when nothing else is specified:
     * a short connect timeout, and generous read/write timeouts that [withTimeout] narrows for the
     * providers that report their own timeout setting.
     */
    private val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_MINUTES, TimeUnit.MINUTES))
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // A dropped keep-alive connection is retried on a fresh one by OkHttp itself, which is
            // what makes a reused connection as safe as a new one.
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * One client per distinct timeout, created from [base] so the connection pool is common to
     * all of them. Cached because the alternative — a new client per request — silently throws the
     * pool away.
     */
    private val byTimeout = ConcurrentHashMap<Long, OkHttpClient>()

    /**
     * The shared client itself, for callers that only need the default timeouts — the app shell and
     * the accessibility service, so both talk to providers over the same pooled connections.
     */
    fun shared(): OkHttpClient = base

    /**
     * A client whose read/write timeout is [seconds], never longer than the value stored in config.
     *
     * [from] is the client the caller was constructed with. In the app that is always [shared], and
     * the result is cached so that every request keeps using the same pool. Anything else (a test
     * double, a caller with its own client) gets a variant of *its* pool instead of ours, because
     * silently ignoring the injected client would be worse than paying for one extra object.
     */
    fun withTimeout(seconds: Long, from: OkHttpClient = base): OkHttpClient {
        val safe = if (seconds <= 0) DEFAULT_TIMEOUT_SECONDS else seconds
        if (from !== base) {
            return from.newBuilder()
                .connectTimeout(minOf(safe, CONNECT_TIMEOUT_SECONDS), TimeUnit.SECONDS)
                .readTimeout(safe, TimeUnit.SECONDS)
                .writeTimeout(safe, TimeUnit.SECONDS)
                .build()
        }
        return byTimeout.getOrPut(safe) {
            base.newBuilder()
                .connectTimeout(minOf(safe, CONNECT_TIMEOUT_SECONDS), TimeUnit.SECONDS)
                .readTimeout(safe, TimeUnit.SECONDS)
                .writeTimeout(safe, TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * For listing models: a short call timeout, because a catalogue is either quick or the user
     * should be typing a model ID by hand instead of waiting.
     */
    fun catalog(): OkHttpClient = byTimeout.getOrPut(CATALOG_KEY) {
        shared.newBuilder()
            .callTimeout(25, TimeUnit.SECONDS)
            .connectTimeout(minOf(15L, CONNECT_TIMEOUT_SECONDS), TimeUnit.SECONDS)
            .build()
    }

    private const val CATALOG_KEY = -25L
}
