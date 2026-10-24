package com.typeassist.app.api

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import com.typeassist.app.data.TokenUsage

/**
 * Shared base for [AiProvider] implementations that talk to an HTTP endpoint.
 *
 * Encapsulates the logic every client duplicates: OkHttp timeout wiring, the one-attempt retry on
 * transient network or HTTP errors, [HttpRetry] scheduling, and the callback dispatch pattern.
 *
 * Subclasses only need to:
 *  * build their [Request] (URL, headers, body already set)
 *  * extract the answer text from a successful [Response]
 *  * turn a failed status code + body into a [Throwable]
 *
 * Streaming clients additionally implement [streamResponse] using [executeStream].
 */
abstract class BaseApiClient(protected val client: OkHttpClient) : AiProvider {

    /** What the provider said this request cost, when it said anything. */
    protected var lastUsage: TokenUsage? = null

    override fun reportedUsage(): TokenUsage? = lastUsage

    /** How long to wait before a [HttpRetry]-scheduled retry, honoring Retry-After when present. */
    protected fun retryAfterSeconds(response: Response): Long? =
        response.header("Retry-After")?.trim()?.toLongOrNull()

    /**
     * Executes a non-streaming HTTP request with automatic retry on transient failures.
     *
     * The request is built once by the caller; the retry re-issues it. Only failures that happen
     * *before any answer arrives* are retried — once text is flowing, the original text is put
     * back and the error is reported instead.
     *
     * @param request the fully built request (URL, headers, body already set)
     * @param timeoutSeconds read/write/connect timeout for this attempt
     * @param onSuccess receives a successful [Response]; returns the raw answer text
     * @param onError receives the HTTP status code and body of a failed response, returns a Throwable
     * @param callback delivered with [Result.success] (text already passed through [cleanModelResponse])
     *                 or [Result.failure]
     */
    protected fun executeRequest(
        request: Request,
        timeoutSeconds: Long,
        onSuccess: (Response) -> String,
        onError: (Int, String?) -> Throwable,
        callback: (Result<String>) -> Unit
    ) {
        val timedClient = HttpClients.withTimeout(timeoutSeconds, client)

        fun send(attempt: Int) {
            val call = timedClient.newCall(request)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // A cancelled call is a user decision, never a failure to retry.
                    if (call.isCanceled()) return
                    if (attempt < HttpRetry.MAX_ATTEMPTS && HttpRetry.shouldRetry(e)) {
                        HttpRetry.schedule(attempt) { send(attempt + 1) }
                        return
                    }
                    callback(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            val body = try { resp.body?.string() } catch (e: Exception) { null }
                            if (attempt < HttpRetry.MAX_ATTEMPTS && HttpRetry.shouldRetryStatus(resp.code)) {
                                HttpRetry.schedule(attempt, retryAfterSeconds(resp)) { send(attempt + 1) }
                                return
                            }
                            callback(Result.failure(onError(resp.code, body)))
                            return
                        }
                        try {
                            val text = cleanModelResponse(onSuccess(resp))
                            callback(Result.success(text))
                        } catch (e: Exception) {
                            callback(Result.failure(e))
                        }
                    }
                }
            })
        }

        send(1)
    }

    /**
     * Executes a streaming HTTP request with automatic retry on transient failures.
     *
     * @param request the fully built streaming request (Accept: text/event-stream already set)
     * @param timeoutSeconds read/write/connect timeout for this attempt
     * @param onDelta called with each new piece of text as it arrives
     * @param parseDelta decodes one SSE event payload into a delta string (or null to skip)
     * @param onError receives the HTTP status code and body of a failed response, returns a Throwable
     * @param callback delivered with the complete answer once the stream ends
     * @return an [AiStream] handle that cancels the in-flight call, or null if the request failed
     *         before it could start
     */
    protected fun executeStream(
        request: Request,
        timeoutSeconds: Long,
        onDelta: (String) -> Unit,
        parseDelta: (String) -> String?,
        onError: (Int, String?) -> Throwable,
        callback: (Result<String>) -> Unit
    ): AiStream? {
        val timedClient = HttpClients.withTimeout(timeoutSeconds, client)
        var inFlight: Call? = null
        var streamedAnything = false

        fun send(attempt: Int) {
            val call = timedClient.newCall(request)
            inFlight = call
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (call.isCanceled()) return
                    if (!streamedAnything && attempt < HttpRetry.MAX_ATTEMPTS && HttpRetry.shouldRetry(e)) {
                        HttpRetry.schedule(attempt) { send(attempt + 1) }
                        return
                    }
                    callback(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            val body = try { resp.body?.string() } catch (e: Exception) { null }
                            if (!streamedAnything && attempt < HttpRetry.MAX_ATTEMPTS &&
                                HttpRetry.shouldRetryStatus(resp.code)
                            ) {
                                HttpRetry.schedule(attempt, retryAfterSeconds(resp)) { send(attempt + 1) }
                                return
                            }
                            callback(Result.failure(onError(resp.code, body)))
                            return
                        }
                        val complete = SseStreamReader.read(resp, { delta ->
                            streamedAnything = true
                            onDelta(delta)
                        }, parseDelta)
                        if (call.isCanceled()) return
                        complete.fold(
                            onSuccess = { text ->
                                val cleaned = cleanModelResponse(text)
                                if (cleaned.isBlank()) {
                                    callback(Result.failure(
                                        IOException("The provider returned an empty response.")
                                    ))
                                } else {
                                    callback(Result.success(cleaned))
                                }
                            },
                            onFailure = { callback(Result.failure(it)) }
                        )
                    }
                }
            })
        }

        send(1)
        return AiStream { inFlight?.cancel() }
    }
}
