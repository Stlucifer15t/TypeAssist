package com.typeassist.app.api

import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.GenConfig
import com.typeassist.app.data.GenerationSettings
import com.typeassist.app.data.TokenUsage
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
class CustomApiClient(client: OkHttpClient) : BaseApiClient(client) {

    override fun generateResponse(
        prompt: String,
        userText: String,
        config: AppConfig,
        image: PromptImage?,
        callback: (Result<String>) -> Unit
    ) {
        val request = try {
            buildRequest(
                baseUrl = config.customApiConfig.baseUrl,
                apiKey = config.customApiConfig.apiKey,
                model = config.customApiConfig.model,
                prompt = prompt,
                userText = userText,
                generation = GenerationSettings.sanitize(config.generationConfig),
                image = image,
                stream = false
            )
        } catch (e: Exception) {
            callback(Result.failure(e))
            return
        }

        executeRequest(
            request = request,
            timeoutSeconds = config.apiTimeoutSeconds,
            onSuccess = { resp ->
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
                resultText
            },
            onError = { code, body -> IOException("$code: ${body ?: ""}".trim()) },
            callback = callback
        )
    }

    /**
     * Streams the answer with `"stream": true`. Returns null when the request could not even be
     * started, so the caller can fall back to [generateResponse].
     *
     * The handle cancels the attempt currently in flight, since a retry replaces the first call.
     */
    override fun streamResponse(
        prompt: String,
        userText: String,
        config: AppConfig,
        image: PromptImage?,
        onDelta: (String) -> Unit,
        callback: (Result<String>) -> Unit
    ): AiStream? {
        val request = try {
            buildRequest(
                baseUrl = config.customApiConfig.baseUrl,
                apiKey = config.customApiConfig.apiKey,
                model = config.customApiConfig.model,
                prompt = prompt,
                userText = userText,
                generation = GenerationSettings.sanitize(config.generationConfig),
                image = image,
                stream = true
            )
        } catch (e: Exception) {
            return null
        }

        return executeStream(
            request = request,
            timeoutSeconds = config.apiTimeoutSeconds,
            onDelta = onDelta,
            parseDelta = StreamChunkParser::openAiDelta,
            onError = { code, body -> IOException("$code: ${body ?: ""}".trim()) },
            callback = callback
        )
    }

    /** Builds the chat-completions request, shared by the streaming and non-streaming calls. */
    internal fun buildRequest(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        userText: String,
        generation: GenConfig,
        image: PromptImage? = null,
        stream: Boolean
    ): Request {
        val jsonBody = JSONObject()
        val messagesArray = JSONArray()

        // System message (prompt)
        val systemMessage = JSONObject()
        systemMessage.put("role", "system")
        systemMessage.put("content", prompt)
        messagesArray.put(systemMessage)

        // User message. With an image the content becomes the OpenAI multimodal array; otherwise a
        // plain string, which every OpenAI-compatible endpoint understands.
        val userMessage = JSONObject()
        userMessage.put("role", "user")
        if (image == null) {
            userMessage.put("content", userText)
        } else {
            val contentArray = JSONArray()
            val textPart = JSONObject()
            textPart.put("type", "text")
            textPart.put("text", userText)
            contentArray.put(textPart)
            val imageUrl = JSONObject()
            imageUrl.put("url", "data:${image.mimeType};base64,${image.base64}")
            val imagePart = JSONObject()
            imagePart.put("type", "image_url")
            imagePart.put("image_url", imageUrl)
            contentArray.put(imagePart)
            userMessage.put("content", contentArray)
        }
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
}
