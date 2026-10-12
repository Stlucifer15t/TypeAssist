package com.typeassist.app.api

import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.GenConfig
import com.typeassist.app.data.GenerationSettings
import com.typeassist.app.data.TokenUsage
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * ============================== OPENAI-COMPATIBLE API ==============================
 *
 * Sends `temperature`, `top_p` and — only when the user set a limit — `max_tokens`, which is what
 * the settings sliders control. A request that failed before any text arrived (dropped connection,
 * 429, 5xx) is retried once; a failure mid-answer is reported instead, because the answer may
 * already be in the text field.
 */
class CustomApiClient(private val client: OkHttpClient) : AiProvider {

    /** What the endpoint said this request cost, when it said anything. */
    private var lastUsage: TokenUsage? = null

    override fun reportedUsage(): TokenUsage? = lastUsage

    override fun generateResponse(
        prompt: String,
        userText: String,
        config: AppConfig,
        callback: (Result<String>) -> Unit
    ) {
        callCustomApi(
            baseUrl = config.customApiConfig.baseUrl,
            apiKey = config.customApiConfig.apiKey,
            model = config.customApiConfig.model,
            prompt = prompt,
            userText = userText,
            generation = GenerationSettings.sanitize(config.generationConfig),
            timeoutSeconds = config.apiTimeoutSeconds,
            callback = callback
        )
    }

    fun callCustomApi(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        userText: String,
        generation: GenConfig,
        timeoutSeconds: Long,
        callback: (Result<String>) -> Unit
    ) {
        lastUsage = null
        val request = try {
            buildRequest(baseUrl, apiKey, model, prompt, userText, generation, stream = false)
        } catch (e: Exception) {
            callback(Result.failure(e))
            return
        }

        val timedClient = HttpClients.withTimeout(timeoutSeconds, client)

        fun send(attempt: Int) {
            val call = timedClient.newCall(request)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
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
                            val body = try {
                                resp.body?.string()
                            } catch (e: Exception) {
                                null
                            }
                            if (attempt < HttpRetry.MAX_ATTEMPTS && HttpRetry.shouldRetryStatus(resp.code)) {
                                HttpRetry.schedule(attempt, retryAfterSeconds(resp)) { send(attempt + 1) }
                                return
                            }
                            callback(Result.failure(IOException("${resp.code}: ${body ?: ""}".trim())))
                            return
                        }
                        try {
                            val responseData = resp.body?.string() ?: throw IOException("Empty response body")
                            val json = JSONObject(responseData)
                            lastUsage = usageOf(json)
                            val choices = json.optJSONArray("choices")
                                ?: throw IOException(
                                    "The provider did not return a chat completion. Check that the " +
                                        "Base URL points at an OpenAI-compatible endpoint."
                                )
                            if (choices.length() == 0) {
                                throw IOException("The provider returned no choices.")
                            }
                            val message = choices.getJSONObject(0).optJSONObject("message")
                            val resultText = message?.optString("content").orEmpty().ifBlank {
                                choices.getJSONObject(0).optString("text")
                            }
                            if (resultText.isBlank()) {
                                throw IOException(
                                    "The provider returned an empty response. Check the model ID and " +
                                        "that the account can use it."
                                )
                            }
                            callback(Result.success(cleanModelResponse(resultText)))
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
     * Streams the answer with `"stream": true`. Returns null when the request could not even be
     * started, so the caller can fall back to [callCustomApi].
     *
     * The handle cancels the attempt currently in flight, since a retry replaces the first call.
     */
    override fun streamResponse(
        prompt: String,
        userText: String,
        config: AppConfig,
        onDelta: (String) -> Unit,
        callback: (Result<String>) -> Unit
    ): AiStream? {
        val generation = GenerationSettings.sanitize(config.generationConfig)
        val request = try {
            buildRequest(
                baseUrl = config.customApiConfig.baseUrl,
                apiKey = config.customApiConfig.apiKey,
                model = config.customApiConfig.model,
                prompt = prompt,
                userText = userText,
                generation = generation,
                stream = true
            )
        } catch (e: Exception) {
            return null
        }

        val timedClient = HttpClients.withTimeout(config.apiTimeoutSeconds, client)
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
                            val body = try {
                                resp.body?.string()
                            } catch (e: Exception) {
                                null
                            }
                            if (!streamedAnything && attempt < HttpRetry.MAX_ATTEMPTS &&
                                HttpRetry.shouldRetryStatus(resp.code)
                            ) {
                                HttpRetry.schedule(attempt, retryAfterSeconds(resp)) { send(attempt + 1) }
                                return
                            }
                            callback(Result.failure(IOException("${resp.code}: ${body ?: ""}".trim())))
                            return
                        }
                        val complete = SseStreamReader.read(resp, { delta ->
                            streamedAnything = true
                            onDelta(delta)
                        }, StreamChunkParser::openAiDelta)
                        if (call.isCanceled()) return
                        complete.fold(
                            onSuccess = { text ->
                                if (text.isBlank()) {
                                    callback(Result.failure(IOException("The provider returned an empty response.")))
                                } else {
                                    callback(Result.success(cleanModelResponse(text)))
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

    internal fun buildRequest(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        userText: String,
        generation: GenConfig,
        stream: Boolean
    ): Request {
        val jsonBody = JSONObject()
        val messagesArray = JSONArray()

        // System message (prompt)
        val systemMessage = JSONObject()
        systemMessage.put("role", "system")
        systemMessage.put("content", prompt)
        messagesArray.put(systemMessage)

        // User message
        val userMessage = JSONObject()
        userMessage.put("role", "user")
        userMessage.put("content", userText)
        messagesArray.put(userMessage)

        val wire = GenerationSettings.wireParameters(generation)
        jsonBody.put("model", model)
        jsonBody.put("messages", messagesArray)
        jsonBody.put("temperature", wire.temperature)
        jsonBody.put("top_p", wire.topP)
        wire.maxTokens?.let { jsonBody.put("max_tokens", it) }
        if (stream) jsonBody.put("stream", true)

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder()
            .url(chatCompletionsUrl(baseUrl))
            .post(requestBody)

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }
        if (stream) {
            requestBuilder.addHeader("Accept", "text/event-stream")
        }
        return requestBuilder.build()
    }

    /**
     * Base URL plus /chat/completions. A Base URL is usually something like
     * "https://api.groq.com/openai/v1"; a full or /models URL is accepted as well.
     */
    private fun chatCompletionsUrl(baseUrl: String): String {
        val cleanBaseUrl = baseUrl.trim().removeSuffix("/")
        val normalizedBaseUrl = cleanBaseUrl.removeSuffix("/models")
        return when {
            cleanBaseUrl.endsWith("/chat/completions") -> cleanBaseUrl
            cleanBaseUrl.endsWith("/models") -> "$normalizedBaseUrl/chat/completions"
            else -> "$cleanBaseUrl/chat/completions"
        }
    }

    /**
     * The `usage` object OpenAI-compatible endpoints return on non-streaming answers. Plenty of
     * gateways omit it, which is why the caller falls back to an estimate.
     */
    private fun usageOf(json: JSONObject): TokenUsage? {
        val usage = json.optJSONObject("usage") ?: return null
        val prompt = usage.optInt("prompt_tokens", 0)
        val completion = usage.optInt("completion_tokens", 0)
        if (prompt <= 0 && completion <= 0) return null
        return TokenUsage(prompt, completion)
    }

    private fun retryAfterSeconds(response: Response): Long? =
        response.header("Retry-After")?.trim()?.toLongOrNull()

}
