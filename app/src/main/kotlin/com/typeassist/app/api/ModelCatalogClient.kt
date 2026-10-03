package com.typeassist.app.api

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Reads model IDs from standard OpenAI-compatible and Gemini model-list endpoints. */
class ModelCatalogClient(private val client: OkHttpClient) {

    suspend fun fetchOpenAiCompatibleModels(baseUrl: String, apiKey: String): List<String> {
        val url = modelsEndpoint(baseUrl)
        val requestBuilder = Request.Builder().url(url).get()
        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
        }
        return fetch(requestBuilder.build(), ModelCatalogParser::parseOpenAiCompatibleModels)
    }

    suspend fun fetchGeminiModels(apiKey: String): List<String> {
        if (apiKey.isBlank()) throw IllegalArgumentException("Enter your Gemini API key first.")
        val url = "https://generativelanguage.googleapis.com/v1beta/models".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("key", apiKey.trim())
            ?.build()
            ?: throw IOException("Could not build the Gemini model-list URL.")
        val request = Request.Builder().url(url).get().build()
        return fetch(request, ModelCatalogParser::parseGeminiModels)
    }

    private suspend fun fetch(request: Request, parse: (String) -> List<String>): List<String> =
        withContext(Dispatchers.IO) {
            client.newBuilder()
                .callTimeout(25, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
                .use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw ModelCatalogHttpException(response.code, body)
                    }
                    val models = try {
                        parse(body)
                    } catch (e: Exception) {
                        throw IOException("The provider returned a model-list response TypeAssist could not read.", e)
                    }
                    if (models.isEmpty()) {
                        throw IOException("The provider returned no chat-capable models. You can still enter a model ID manually.")
                    }
                    models
                }
        }

    companion object {
        /**
         * Accepts a base URL, a `/models` URL, or the existing chat-completions URL.
         * Query parameters are intentionally omitted so API keys are only sent in headers.
         */
        fun modelsEndpoint(baseUrl: String): HttpUrl {
            val parsed = baseUrl.trim().toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Enter a valid Base URL beginning with http:// or https://.")
            if (parsed.scheme != "https" && parsed.scheme != "http") {
                throw IllegalArgumentException("The Base URL must use HTTP or HTTPS.")
            }

            val segments = parsed.encodedPathSegments.filter { it.isNotBlank() }.toMutableList()
            if (segments.size >= 2 &&
                segments[segments.lastIndex - 1].equals("chat", ignoreCase = true) &&
                segments.last().equals("completions", ignoreCase = true)
            ) {
                segments.removeAt(segments.lastIndex)
                segments.removeAt(segments.lastIndex)
            }
            if (segments.lastOrNull()?.equals("models", ignoreCase = true) != true) {
                segments.add("models")
            }

            val normalizedPath = if (segments.isEmpty()) "/" else "/${segments.joinToString("/")}"
            return parsed.newBuilder()
                .encodedPath(normalizedPath)
                .query(null)
                .fragment(null)
                .build()
        }
    }
}

class ModelCatalogHttpException(val statusCode: Int, responseBody: String) :
    IOException("HTTP $statusCode${responseMessage(responseBody)}") {
    companion object {
        private fun responseMessage(body: String): String {
            val message = try {
                val root = JsonParser.parseString(body)
                when {
                    root.isJsonObject && root.asJsonObject.has("error") -> {
                        val error = root.asJsonObject.get("error")
                        when {
                            error.isJsonObject && error.asJsonObject.has("message") -> error.asJsonObject.get("message").asString
                            error.isJsonPrimitive -> error.asString
                            else -> ""
                        }
                    }
                    else -> ""
                }
            } catch (_: Exception) {
                ""
            }.trim()
            return if (message.isBlank()) "" else ": ${message.take(180)}"
        }
    }
}

/** Small JSON parser kept separate so response formats can be covered by local unit tests. */
object ModelCatalogParser {
    fun parseOpenAiCompatibleModels(json: String): List<String> {
        val root = JsonParser.parseString(json)
        val models = when {
            root.isJsonObject -> {
                val objectRoot = root.asJsonObject
                sequenceOf("data", "models")
                    .mapNotNull { key -> objectRoot.get(key)?.takeIf { it.isJsonArray }?.asJsonArray }
                    .firstOrNull()
            }
            root.isJsonArray -> root.asJsonArray
            else -> null
        } ?: return emptyList()

        return models.mapNotNull { item ->
            if (!item.isJsonObject) return@mapNotNull null
            val model = item.asJsonObject.get("id")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.trim()
            model?.takeIf { it.isNotEmpty() }
        }.distinct().sorted()
    }

    fun parseGeminiModels(json: String): List<String> {
        val root = JsonParser.parseString(json)
        val models: JsonArray = root.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.get("models")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: return emptyList()

        return models.mapNotNull { item ->
            if (!item.isJsonObject) return@mapNotNull null
            val modelObject = item.asJsonObject
            val methods = modelObject.get("supportedGenerationMethods")
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
            if (methods == null || methods.none { it.isJsonPrimitive && it.asString.equals("generateContent", true) }) {
                return@mapNotNull null
            }
            val name = modelObject.get("name")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.trim()
                ?.removePrefix("models/")
            name?.takeIf { it.isNotEmpty() }
        }.distinct().sorted()
    }
}
