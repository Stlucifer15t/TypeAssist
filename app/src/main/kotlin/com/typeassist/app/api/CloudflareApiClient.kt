package com.typeassist.app.api

import okhttp3.*
import com.typeassist.app.data.AppConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class CloudflareApiClient(private val client: OkHttpClient) : AiProvider {

    override fun generateResponse(
        prompt: String,
        userText: String,
        config: AppConfig,
        callback: (Result<String>) -> Unit
    ) {
        callCloudflare(
            accountId = config.cloudflareConfig.accountId,
            apiToken = config.cloudflareConfig.apiToken,
            model = config.cloudflareConfig.model,
            prompt = prompt,
            userText = userText,
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
        
        jsonBody.put("messages", messagesArray)

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val url = "https://api.cloudflare.com/client/v4/accounts/$accountId/ai/run/$model"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiToken")
            .post(requestBody)
            .build()

        client.newBuilder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
            .newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        val errorBody = it.body?.string()
                        val errorCode = it.code
                        val errorMessage = getErrorMessage(errorCode, errorBody)
                        callback(Result.failure(IOException(errorMessage)))
                        return
                    }
                    try {
                        val responseData = it.body?.string() ?: throw IOException("Empty response body")
                        val jsonResponse = JSONObject(responseData)
                        val resultText = jsonResponse.getJSONObject("result").getString("response")
                        callback(Result.success(cleanModelResponse(resultText)))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    /**
     * Streams Workers AI output with "stream": true (SSE, one `data: {"response": …}`
     * frame per token), reporting accumulated text via [onChunk] (OkHttp worker thread).
     */
    fun streamCloudflare(
        accountId: String,
        apiToken: String,
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

        jsonBody.put("messages", messagesArray)
        jsonBody.put("stream", true)

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val url = "https://api.cloudflare.com/client/v4/accounts/$accountId/ai/run/$model"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiToken")
            .post(requestBody)
            .build()

        client.newBuilder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
            .newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        val errorBody = it.body?.string()
                        callback(Result.failure(IOException(getErrorMessage(it.code, errorBody))))
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
                                        val piece = json.optString("response").orEmpty()
                                        if (piece.isNotEmpty()) {
                                            accumulated += piece
                                            delivered = true
                                            onChunk(accumulated)
                                        }
                                    } catch (_: Exception) {
                                        // Skip metadata frames.
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
            401 -> "Unauthorized (Check API Token)"
            403 -> "Forbidden (Check Account ID or Permissions)"
            404 -> "Not Found (Check Model ID)"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            else -> "Unexpected error"
        }
    }
}
