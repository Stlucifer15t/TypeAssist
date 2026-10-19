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
 * ============================== CLOUDFLARE WORKERS AI ==============================
 *
 * Workers AI takes `temperature`, `top_p` and `max_tokens` next to `messages`, so the sliders in
 * Settings → Text generation apply here too. This endpoint has no streaming mode, so it is always
 * one request — which is also why a retry is cheap and safe when nothing came back.
 */
class CloudflareApiClient(private val client: OkHttpClient) : AiProvider {

    /** What Workers AI said this request cost, when it said anything. */
    private var lastUsage: TokenUsage? = null

    override fun reportedUsage(): TokenUsage? = lastUsage

    override fun generateResponse(
        prompt: String,
        userText: String,
        config: AppConfig,
        image: PromptImage?,
        callback: (Result<String>) -> Unit
    ) {
        // Workers AI text models do not take the screenshot; the text path is unchanged.
        callCloudflare(
            accountId = config.cloudflareConfig.accountId,
            apiToken = config.cloudflareConfig.apiToken,
            model = config.cloudflareConfig.model,
            prompt = prompt,
            userText = userText,
            generation = GenerationSettings.sanitize(config.generationConfig),
            timeoutSeconds = config.apiTimeoutSeconds,
            callback = callback
        )
    }

    fun callCloudflare(
        accountId: String,
        apiToken: String,
        model: String,
        prompt: String,
        userText: String,
        generation: GenConfig,
        timeoutSeconds: Long,
        callback: (Result<String>) -> Unit
    ) {
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
        jsonBody.put("messages", messagesArray)
        jsonBody.put("temperature", wire.temperature)
        jsonBody.put("top_p", wire.topP)
        wire.maxTokens?.let { jsonBody.put("max_tokens", it) }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val url = "https://api.cloudflare.com/client/v4/accounts/$accountId/ai/run/$model"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiToken")
            .post(requestBody)
            .build()

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
                            val result = JSONObject(responseData).optJSONObject("result")
                            lastUsage = usageOf(result)
                            val resultText = result?.optString("response").orEmpty()
                            if (resultText.isBlank()) {
                                throw IOException(
                                    "Cloudflare returned an empty answer. Check that the model ID is a " +
                                        "text-generation model such as @cf/meta/llama-3-8b-instruct."
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

    /** `result.usage.prompt_tokens` / `completion_tokens`, when Workers AI reports them. */
    private fun usageOf(result: JSONObject?): TokenUsage? {
        val usage = result?.optJSONObject("usage") ?: return null
        val prompt = usage.optInt("prompt_tokens", 0)
        val completion = usage.optInt("completion_tokens", 0)
        if (prompt <= 0 && completion <= 0) return null
        return TokenUsage(prompt, completion)
    }

    private fun retryAfterSeconds(response: Response): Long? =
        response.header("Retry-After")?.trim()?.toLongOrNull()

    private fun getErrorMessage(code: Int, body: String?): String {
        return try {
            val json = JSONObject(body ?: "")
            val errors = json.getJSONArray("errors")
            if (errors.length() > 0) {
                val error = errors.getJSONObject(0)
                "$code: ${error.getString("message")}"
            } else {
                "$code: ${getErrorMessageForCode(code)}"
            }
        } catch (e: Exception) {
            "$code: ${getErrorMessageForCode(code)}"
        }
    }

    private fun getErrorMessageForCode(code: Int): String {
        return when (code) {
            400 -> "Bad Request"
            401 -> "Unauthorized (check the API token)"
            403 -> "Forbidden (check the account ID and token permissions)"
            404 -> "Not Found (check the model ID)"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            else -> "Unexpected error"
        }
    }
}
