package com.typeassist.app.api


import okhttp3.*
import com.typeassist.app.data.AppConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiApiClient(private val client: OkHttpClient) : AiProvider {

    override fun generateResponse(
        prompt: String,
        userText: String,
        config: AppConfig,
        callback: (Result<String>) -> Unit
    ) {
        callGemini(
            apiKey = config.apiKey,
            model = config.model,
            prompt = prompt,
            userText = userText,
            temp = config.generationConfig.temperature,
            topP = config.generationConfig.topP,
            timeoutSeconds = config.apiTimeoutSeconds,
            callback = callback
        )
    }

    fun callGemini(
        apiKey: String,
        model: String,
        prompt: String,
        userText: String,
        temp: Double,
        topP: Double,
        timeoutSeconds: Long,
        callback: (Result<String>) -> Unit
    ) {
        val jsonBody = JSONObject()
        val contentsArray = JSONArray()
        val contentObject = JSONObject()
        val partsArray = JSONArray()
        val partObject = JSONObject()
        partObject.put("text", "$prompt\n\nInput: $userText")
        partsArray.put(partObject)
        contentObject.put("parts", partsArray)
        contentsArray.put(contentObject)
        jsonBody.put("contents", contentsArray)

        val genConfig = JSONObject()
        genConfig.put("temperature", temp)
        genConfig.put("topP", topP)
        jsonBody.put("generationConfig", genConfig)

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val request = Request.Builder().url(url).post(requestBody).build()

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
                        val resultText = jsonResponse.getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                            .getString("text")
                        callback(Result.success(cleanModelResponse(resultText)))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    /**
     * Streams a Gemini response over Server-Sent Events, reporting the accumulated text
     * after every chunk via [onChunk] (called on an OkHttp worker thread). The final
     * callback fires once with the fully assembled (cleaned) response.
     */
    fun streamGemini(
        apiKey: String,
        model: String,
        prompt: String,
        userText: String,
        temp: Double,
        topP: Double,
        timeoutSeconds: Long,
        onChunk: (String) -> Unit,
        callback: (Result<String>) -> Unit
    ) {
        val jsonBody = JSONObject()
        val contentsArray = JSONArray()
        val contentObject = JSONObject()
        val partsArray = JSONArray()
        val partObject = JSONObject()
        partObject.put("text", "$prompt\n\nInput: $userText")
        partsArray.put(partObject)
        contentObject.put("parts", partsArray)
        contentsArray.put(contentObject)
        jsonBody.put("contents", contentsArray)

        val genConfig = JSONObject()
        genConfig.put("temperature", temp)
        genConfig.put("topP", topP)
        jsonBody.put("generationConfig", genConfig)

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"

        val request = Request.Builder().url(url).post(requestBody).build()

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
                                if (payload.isNotEmpty() && payload != "[DONE]") {
                                    try {
                                        val json = JSONObject(payload)
                                        val candidates = json.optJSONArray("candidates")
                                        if (candidates != null && candidates.length() > 0) {
                                            val parts = candidates.getJSONObject(0)
                                                .optJSONObject("content")?.optJSONArray("parts")
                                            if (parts != null) {
                                                for (i in 0 until parts.length()) {
                                                    val piece = parts.optJSONObject(i)?.optString("text").orEmpty()
                                                    if (piece.isNotEmpty()) accumulated += piece
                                                }
                                                if (accumulated.isNotEmpty()) {
                                                    delivered = true
                                                    onChunk(accumulated)
                                                }
                                            }
                                        }
                                    } catch (_: Exception) {
                                        // Ignore malformed keep-alive frames; keep reading.
                                    }
                                }
                            }
                            line = reader.readLine()
                        }
                        if (!delivered && accumulated.isBlank()) {
                            throw IOException("No content received from stream")
                        }
                        callback(Result.success(cleanModelResponse(accumulated)))
                    } catch (e: Exception) {
                        // A mid-stream failure after content was shown is reported as failure;
                        // the overlay hides and the error toast explains it.
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

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
            403 -> "Forbidden"
            404 -> "Not Found"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            503 -> "Service Unavailable"
            else -> "Unexpected code $code"
        }
    }
}
