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

        // Standard OpenAI SDK behavior: baseURL + "/chat/completions"
        val cleanBaseUrl = baseUrl.trim().removeSuffix("/")
        val normalizedBaseUrl = cleanBaseUrl.removeSuffix("/models")
        val url = when {
            cleanBaseUrl.endsWith("/chat/completions") -> cleanBaseUrl
            cleanBaseUrl.endsWith("/models") -> "$normalizedBaseUrl/chat/completions"
            else -> "$cleanBaseUrl/chat/completions"
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        client.newBuilder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
            .newCall(requestBuilder.build()).enqueue(object : Callback {
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
     * Streams an OpenAI-compatible chat completion with "stream": true, reporting the
     * accumulated text via [onChunk] (OkHttp worker thread). Servers that ignore the
     * stream flag and answer with a normal JSON body are handled too (message.content).
     */
    fun streamCustomApi(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        userText: String,
        timeoutSeconds: Long,
        onChunk: (String) -> Unit,
        callback: (Result<String>) -> Unit
    ) {
        val jsonBody = JSONObject()
        val messagesArray = JSONArray()

        val systemMessage = JSONObject()
        systemMessage.put("role", "system")
        systemMessage.put("content", prompt)
        messagesArray.put(systemMessage)

        val userMessage = JSONObject()
        userMessage.put("role", "user")
        userMessage.put("content", userText)
        messagesArray.put(userMessage)

        jsonBody.put("model", model)
        jsonBody.put("messages", messagesArray)
        jsonBody.put("stream", true)

        val cleanBaseUrl = baseUrl.trim().removeSuffix("/")
        val normalizedBaseUrl = cleanBaseUrl.removeSuffix("/models")
        val url = when {
            cleanBaseUrl.endsWith("/chat/completions") -> cleanBaseUrl
            cleanBaseUrl.endsWith("/models") -> "$normalizedBaseUrl/chat/completions"
            else -> "$cleanBaseUrl/chat/completions"
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        client.newBuilder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
            .newCall(requestBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        val errorBody = it.body?.string()
                        val code = it.code
                        callback(Result.failure(IOException("$code: ${errorBody?.take(300) ?: "Request failed"}")))
                        return
                    }
                    var accumulated = ""
                    var delivered = false
                    try {
                        val reader = it.body?.charStream()?.buffered() ?: throw IOException("Empty response body")
                        var line = reader.readLine()
                        while (line != null) {
                            if (line.startsWith("data:")) {
                                val payload = line.removePrefix("data:").trim()
                                if (payload.isNotEmpty()) {
                                    if (payload == "[DONE]") break
                                    try {
                                        val json = JSONObject(payload)
                                        val choices = json.optJSONArray("choices")
                                        if (choices != null && choices.length() > 0) {
                                            val choice = choices.getJSONObject(0)
                                            // Streaming shape: choices[].delta.content
                                            val delta = choice.optJSONObject("delta")?.optString("content").orEmpty()
                                            if (delta.isNotEmpty()) {
                                                accumulated += delta
                                                delivered = true
                                                onChunk(accumulated)
                                            } else {
                                                // Non-streaming shape: choices[].message.content
                                                val full = choice.optJSONObject("message")?.optString("content").orEmpty()
                                                if (full.isNotEmpty() && accumulated.isEmpty()) {
                                                    accumulated = full
                                                    delivered = true
                                                    onChunk(accumulated)
                                                }
                                            }
                                        }
                                    } catch (_: Exception) {
                                        // Skip malformed frames.
                                    }
                                }
                            }
                            line = reader.readLine()
                        }
                        if (!delivered) throw IOException("No content received from stream")
                        callback(Result.success(cleanModelResponse(accumulated)))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }
}
