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
        val requestBody = buildBody(prompt, userText, temp, topP)
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
     * Streams the answer with `streamGenerateContent`. Returns null when the request could not be
     * started, so the caller can fall back to [callGemini].
     */
    override fun streamResponse(
        prompt: String,
        userText: String,
        config: AppConfig,
        onDelta: (String) -> Unit,
        callback: (Result<String>) -> Unit
    ): AiStream? {
        val apiKey = config.apiKey
        val model = config.model
        if (apiKey.isBlank() || model.isBlank()) return null

        val request = try {
            Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey")
                .addHeader("Accept", "text/event-stream")
                .post(buildBody(prompt, userText, config.generationConfig.temperature, config.generationConfig.topP))
                .build()
        } catch (e: Exception) {
            return null
        }

        val call = client.newBuilder()
            .connectTimeout(config.apiTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.apiTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(config.apiTimeoutSeconds, TimeUnit.SECONDS)
            .build()
            .newCall(request)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) return
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val body = try { resp.body?.string() } catch (e: Exception) { null }
                        callback(Result.failure(IOException(getErrorMessage(resp.code, body))))
                        return
                    }
                    val complete = SseStreamReader.read(resp, onDelta, StreamChunkParser::geminiDelta)
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

    /** The request body, shared by the streaming and non-streaming calls. */
    private fun buildBody(prompt: String, userText: String, temp: Double, topP: Double): RequestBody {
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

        return jsonBody.toString().toRequestBody("application/json".toMediaType())
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
