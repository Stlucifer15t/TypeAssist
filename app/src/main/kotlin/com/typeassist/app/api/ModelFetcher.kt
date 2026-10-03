package com.typeassist.app.api

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Reads the list of models a provider currently offers so the user can pick one
 * from a searchable list instead of typing exact model ids by hand.
 *
 * Every method is a plain GET request and never touches the saved configuration,
 * so calling it can not break a working setup.
 */
class ModelFetcher(private val client: OkHttpClient) {

    /**
     * Lists models from any OpenAI compatible endpoint (`GET {baseUrl}/models`).
     * Works with OpenAI, Groq, OpenRouter, Together, DeepSeek, Mistral, Ollama,
     * LM Studio, vLLM and friends.
     */
    fun fetchOpenAiCompatibleModels(
        baseUrl: String,
        apiKey: String,
        timeoutSeconds: Long,
        callback: (Result<List<String>>) -> Unit
    ) {
        val url = modelsUrl(baseUrl)
        if (url == null) {
            callback(Result.failure(IOException("Enter a valid Base URL (starting with http) first")))
            return
        }
        val builder = Request.Builder().url(url).get()
        if (apiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
        }
        enqueue(builder.build(), timeoutSeconds, callback) { body -> parseOpenAiModels(body) }
    }

    /** Lists the Gemini models that support text generation for the given key. */
    fun fetchGeminiModels(
        apiKey: String,
        timeoutSeconds: Long,
        callback: (Result<List<String>>) -> Unit
    ) {
        if (apiKey.isBlank()) {
            callback(Result.failure(IOException("Enter your Gemini API key first")))
            return
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200&key=${apiKey.trim()}"
        enqueue(Request.Builder().url(url).get().build(), timeoutSeconds, callback) { body ->
            parseGeminiModels(body)
        }
    }

    /** Lists the Workers AI models available to a Cloudflare account. */
    fun fetchCloudflareModels(
        accountId: String,
        apiToken: String,
        timeoutSeconds: Long,
        callback: (Result<List<String>>) -> Unit
    ) {
        if (accountId.isBlank() || apiToken.isBlank()) {
            callback(Result.failure(IOException("Enter your Account ID and API token first")))
            return
        }
        val url = "https://api.cloudflare.com/client/v4/accounts/${accountId.trim()}/ai/models/search?per_page=100&page=1"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${apiToken.trim()}")
            .get()
            .build()
        enqueue(request, timeoutSeconds, callback) { body -> parseCloudflareModels(body) }
    }

    // --- internals ---------------------------------------------------------

    private fun enqueue(
        request: Request,
        timeoutSeconds: Long,
        callback: (Result<List<String>>) -> Unit,
        parse: (String) -> List<String>
    ) {
        // Model lists are small, so a tight timeout keeps the UI responsive.
        val timeout = timeoutSeconds.coerceIn(10L, 30L)
        client.newBuilder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .build()
            .newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callback(Result.failure(IOException(e.message ?: "Network error, check your connection")))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        val body = try {
                            resp.body?.string()
                        } catch (e: Exception) {
                            null
                        }
                        if (!resp.isSuccessful) {
                            callback(Result.failure(IOException(readableHttpError(resp.code, body))))
                            return
                        }
                        try {
                            val models = parse(body ?: "")
                            if (models.isEmpty()) {
                                callback(Result.failure(IOException("The provider returned an empty model list")))
                            } else {
                                callback(Result.success(models))
                            }
                        } catch (e: Exception) {
                            callback(Result.failure(IOException(e.message ?: "Could not read the model list")))
                        }
                    }
                }
            })
    }

    companion object {

        /**
         * Turns a base URL into its model listing endpoint.
         * `https://api.openai.com/v1` -> `https://api.openai.com/v1/models`
         */
        fun modelsUrl(baseUrl: String): String? {
            val clean = baseUrl.trim().trimEnd('/')
            if (clean.isBlank()) return null
            if (!clean.startsWith("http://") && !clean.startsWith("https://")) return null
            return when {
                clean.endsWith("/chat/completions") -> clean.removeSuffix("/chat/completions") + "/models"
                clean.endsWith("/models") -> clean
                else -> "$clean/models"
            }
        }

        /** OpenAI compatible shape: {"data": [{"id": "gpt-4o-mini"}, ...]} */
        fun parseOpenAiModels(body: String): List<String> {
            val json = JSONObject(body)
            val ids = mutableListOf<String>()
            val data = json.optJSONArray("data")
            if (data != null) {
                collectIds(data, ids)
            }
            // A few self hosted servers answer with {"models": [...]} instead.
            if (ids.isEmpty()) {
                json.optJSONArray("models")?.let { collectIds(it, ids) }
            }
            return tidy(ids)
        }

        private fun collectIds(array: JSONArray, into: MutableList<String>) {
            for (i in 0 until array.length()) {
                when (val item = array.opt(i)) {
                    is JSONObject -> {
                        val id = readString(item, "id") ?: readString(item, "name")
                        if (id != null) into.add(id)
                    }
                    is String -> if (item.isNotBlank()) into.add(item)
                }
            }
        }

        /** Gemini shape: {"models": [{"name": "models/gemini-2.5-flash", ...}]} */
        fun parseGeminiModels(body: String): List<String> {
            val json = JSONObject(body)
            val models = json.optJSONArray("models") ?: return emptyList()
            val ids = mutableListOf<String>()
            for (i in 0 until models.length()) {
                val model = models.optJSONObject(i) ?: continue
                val methods = model.optJSONArray("supportedGenerationMethods")
                val supportsGenerate = methods == null || (0 until methods.length())
                    .any { methods.optString(it).equals("generateContent", ignoreCase = true) }
                if (!supportsGenerate) continue
                val name = readString(model, "name")?.removePrefix("models/")?.trim()
                if (!name.isNullOrBlank()) ids.add(name)
            }
            return tidy(ids)
        }

        /** Cloudflare shape: {"result": [{"name": "@cf/meta/llama-3.3-70b-instruct-fp8-fast"}]} */
        fun parseCloudflareModels(body: String): List<String> {
            val json = JSONObject(body)
            val result = json.optJSONArray("result") ?: return emptyList()
            val ids = mutableListOf<String>()
            for (i in 0 until result.length()) {
                val model = result.optJSONObject(i) ?: continue
                val name = readString(model, "name")?.trim() ?: continue
                // Workers AI also hosts image/audio/embedding models, drop those.
                if (looksNonText(name)) continue
                ids.add(name)
            }
            return tidy(ids)
        }

        private fun looksNonText(name: String): Boolean {
            val lower = name.lowercase()
            return listOf(
                "embed", "whisper", "bge-", "resnet", "detr", "flux", "stable-diffusion",
                "melotts", "tts", "aura-", "llava-", "uform", "dreamshaper", "sdxl"
            ).any { lower.contains(it) }
        }

        /** Removes duplicates and floats stable models above preview/experimental ones. */
        private fun tidy(models: List<String>): List<String> {
            val cleaned = models
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            return cleaned.sortedWith(
                compareBy(
                    { if (it.contains("preview", true) || it.contains("experimental", true)) 1 else 0 },
                    { it.lowercase() }
                )
            )
        }
    }
}
