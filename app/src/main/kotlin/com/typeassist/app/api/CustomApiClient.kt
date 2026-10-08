package com.typeassist.app.api

import okhttp3.*
import com.typeassist.app.data.AppConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class CustomApiClient(private val client: OkHttpClient) : AiProvider {

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
        timeoutSeconds: Long,
        callback: (Result<String>) -> Unit
    ) {
        val request = try {
            buildRequest(baseUrl, apiKey, model, prompt, userText, stream = false)
        } catch (e: Exception) {
            callback(Result.failure(e))
            return
        }

        timeoutClient(timeoutSeconds)
            .newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        val errorBody = it.body?.string()
                        val errorCode = it.code
                        val errorMessage = "$errorCode: $errorBody"
                        callback(Result.failure(IOException(errorMessage)))
                        return
                    }
                    try {
                        val responseData = it.body?.string() ?: throw IOException("Empty response body")
                        val jsonResponse = JSONObject(responseData)
                        val choices = jsonResponse.getJSONArray("choices")
                        if (choices.length() > 0) {
                             val resultText = choices.getJSONObject(0).getJSONObject("message").getString("content")
                             callback(Result.success(cleanModelResponse(resultText)))
                        } else {
                            callback(Result.failure(IOException("No choices returned")))
                        }
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    /**
     * Streams the answer with `"stream": true`. Returns null when the request could not even be
     * started, so the caller can fall back to [callCustomApi].
     */
    override fun streamResponse(
        prompt: String,
        userText: String,
        config: AppConfig,
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
                stream = true
            )
        } catch (e: Exception) {
            return null
        }

        val call = timeoutClient(config.apiTimeoutSeconds).newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) return
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val body = try { resp.body?.string() } catch (e: Exception) { null }
                        callback(Result.failure(IOException("${resp.code}: $body")))
                        return
                    }
                    val complete = SseStreamReader.read(resp, onDelta, StreamChunkParser::openAiDelta)
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
        return AiStream { call.cancel() }
    }

    private fun buildRequest(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        userText: String,
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

        jsonBody.put("model", model)
        jsonBody.put("messages", messagesArray)
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

    private fun timeoutClient(timeoutSeconds: Long): OkHttpClient = client.newBuilder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()
}
