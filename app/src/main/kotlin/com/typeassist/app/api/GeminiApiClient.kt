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
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * ============================== GOOGLE GEMINI ==============================
 *
 * Sends the temperature, top-p and answer-length chosen in Settings → Text generation inside
 * `generationConfig`, and retries once when the connection dropped or the service answered 429/5xx
 * before any text arrived.
 */
class GeminiApiClient(private val client: OkHttpClient) : AiProvider {

    /** What Gemini said this request cost, when it said anything. */
    private var lastUsage: TokenUsage? = null

    override fun reportedUsage(): TokenUsage? = lastUsage

    override fun generateResponse(
        prompt: String,
        userText: String,
        config: AppConfig,
        image: PromptImage?,
        callback: (Result<String>) -> Unit
    ) {
        callGemini(
            apiKey = config.apiKey,
            model = config.model,
            prompt = prompt,
            userText = userText,
            generation = GenerationSettings.sanitize(config.generationConfig),
            timeoutSeconds = config.apiTimeoutSeconds,
            image = image,
            callback = callback
        )
    }

    fun callGemini(
        apiKey: String,
        model: String,
        prompt: String,
        userText: String,
        generation: GenConfig,
        timeoutSeconds: Long,
        image: PromptImage? = null,
        callback: (Result<String>) -> Unit
    ) {
        lastUsage = null
        val requestBody = buildBody(prompt, userText, generation, image)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val request = Request.Builder().url(url).post(requestBody).build()
        // Shared client: the connection to generativelanguage.googleapis.com is usually already open.
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
                            val errorBody = try {
                                resp.body?.string()
                            } catch (e: Exception) {
                                null
                            }
                            if (attempt < HttpRetry.MAX_ATTEMPTS && HttpRetry.shouldRetryStatus(resp.code)) {
                                HttpRetry.schedule(attempt, retryAfterSeconds(resp)) { send(attempt + 1) }
                                return
                            }
                            callback(Result.failure(IOException(getErrorMessage(resp.code, errorBody))))
                            return
                        }
                        try {
                            val responseData = resp.body?.string() ?: throw IOException("Empty response body")
                            val json = JSONObject(responseData)
                            lastUsage = usageOf(json)
                            val text = textOf(json)
                            callback(Result.success(cleanModelResponse(text)))
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
     * Streams the answer with `streamGenerateContent`. Returns null when the request could not be
     * started, so the caller can fall back to [callGemini].
     *
     * The returned handle cancels whichever attempt is currently in flight, which matters because a
     * retry replaces the first call.
     */
    override fun streamResponse(
        prompt: String,
        userText: String,
        config: AppConfig,
        image: PromptImage?,
        onDelta: (String) -> Unit,
        callback: (Result<String>) -> Unit
    ): AiStream? {
        val apiKey = config.apiKey
        val model = config.model
        if (apiKey.isBlank() || model.isBlank()) return null

        val generation = GenerationSettings.sanitize(config.generationConfig)
        val request = try {
            Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey")
                .addHeader("Accept", "text/event-stream")
                .post(buildBody(prompt, userText, generation, image))
                .build()
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
                            // Nothing has been written into the field yet, so starting over is safe.
                            if (!streamedAnything && attempt < HttpRetry.MAX_ATTEMPTS &&
                                HttpRetry.shouldRetryStatus(resp.code)
                            ) {
                                HttpRetry.schedule(attempt, retryAfterSeconds(resp)) { send(attempt + 1) }
                                return
                            }
                            callback(Result.failure(IOException(getErrorMessage(resp.code, body))))
                            return
                        }
                        val complete = SseStreamReader.read(resp, { delta ->
                            streamedAnything = true
                            onDelta(delta)
                        }, StreamChunkParser::geminiDelta)
                        if (call.isCanceled()) return
                        complete.fold(
                            onSuccess = { text ->
                                if (text.isBlank()) {
                                    callback(
                                        Result.failure(
                                            IOException(
                                                "The model returned no text. The request may have been " +
                                                    "blocked by the provider's safety settings."
                                            )
                                        )
                                    )
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

    /** The request body, shared by the streaming and non-streaming calls. */
    internal fun buildBody(
        prompt: String,
        userText: String,
        generation: GenConfig,
        image: PromptImage? = null
    ): RequestBody {
        val jsonBody = JSONObject()
        val contentsArray = JSONArray()
        val contentObject = JSONObject()
        val partsArray = JSONArray()
        // The screenshot goes first so the model sees what the question is about before the text.
        if (image != null) {
            val inline = JSONObject()
            inline.put("mime_type", image.mimeType)
            inline.put("data", image.base64)
            val imagePart = JSONObject()
            imagePart.put("inlineData", inline)
            partsArray.put(imagePart)
        }
        val partObject = JSONObject()
        partObject.put("text", "$prompt\n\nInput: $userText")
        partsArray.put(partObject)
        contentObject.put("parts", partsArray)
        contentsArray.put(contentObject)
        jsonBody.put("contents", contentsArray)

        val wire = GenerationSettings.wireParameters(generation)
        val genConfig = JSONObject()
        genConfig.put("temperature", wire.temperature)
        genConfig.put("topP", wire.topP)
        // Absent when the user left the length unlimited: the provider then applies its own default.
        wire.maxTokens?.let { genConfig.put("maxOutputTokens", it) }
        jsonBody.put("generationConfig", genConfig)

        return jsonBody.toString().toRequestBody("application/json".toMediaType())
    }

    /**
     * Pulls the answer out of a Gemini reply, and explains the two ways this call can legitimately
     * come back without one: a safety block, or a candidate that hit the token limit before writing.
     */
    private fun textOf(json: JSONObject): String {
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val reason = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw IOException(
                if (reason.isNotBlank()) {
                    "The request was blocked by the provider ($reason). Rephrase the text and try again."
                } else {
                    "The provider returned no answer. Check the model ID and try again."
                }
            )
        }

        val candidate = candidates.getJSONObject(0)
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        val text = buildString {
            if (parts != null) {
                for (index in 0 until parts.length()) {
                    append(parts.getJSONObject(index).optString("text"))
                }
            }
        }

        if (text.isBlank()) {
            val finish = candidate.optString("finishReason")
            if (finish.equals("MAX_TOKENS", ignoreCase = true)) {
                throw IOException(
                    "The model ran out of room before answering. Raise Max tokens in " +
                        "Settings → Text generation, or shorten the text."
                )
            }
            throw IOException(
                if (finish.isNotBlank() && !finish.equals("STOP", ignoreCase = true)) {
                    "The model stopped without an answer ($finish). Rephrase the text and try again."
                } else {
                    "The provider returned an empty response."
                }
            )
        }
        return text
    }

    /**
     * `usageMetadata.promptTokenCount` / `candidatesTokenCount`. Absent on older models and on
     * safety blocks, in which case the caller estimates instead.
     */
    private fun usageOf(json: JSONObject): TokenUsage? {
        val metadata = json.optJSONObject("usageMetadata") ?: return null
        val prompt = metadata.optInt("promptTokenCount", 0)
        val completion = metadata.optInt("candidatesTokenCount", 0)
        if (prompt <= 0 && completion <= 0) return null
        return TokenUsage(prompt, completion)
    }

    private fun retryAfterSeconds(response: Response): Long? =
        response.header("Retry-After")?.trim()?.toLongOrNull()

    private fun getErrorMessage(code: Int, body: String?): String {
        val message = try {
            JSONObject(body ?: "").getJSONObject("error").getString("message")
        } catch (e: Exception) {
            getErrorMessageForCode(code)
        }
        return "$code: $message"
    }

    private fun getErrorMessageForCode(code: Int): String {
        return when (code) {
            400 -> "Bad Request"
            401 -> "Unauthorized"
            402 -> "Billing required for this project"
            403 -> "Forbidden"
            404 -> "Not Found"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            503 -> "Service Unavailable"
            else -> "Unexpected code $code"
        }
    }
}
