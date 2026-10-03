package com.typeassist.app.api

import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns an HTTP error response into a short, human readable message.
 *
 * Providers are not consistent about error shapes:
 *  - OpenAI / Groq / OpenRouter / most compatible APIs:  {"error": {"message": "..."}}
 *  - Gemini:                                             {"error": {"message": "..."}}
 *  - Cloudflare Workers AI:                              {"errors": [{"message": "..."}]}
 *  - Self hosted servers (vLLM, LM Studio, Ollama):      {"detail": "..."} or plain text
 *
 * Instead of dumping raw JSON into a toast we extract the provider message and
 * append a short hint for the most common failure codes.
 */
internal fun readableHttpError(code: Int, body: String?): String {
    val providerMessage = extractProviderMessage(body)
    val hint = hintForCode(code)
    return when {
        providerMessage != null && hint != null -> "$code: $providerMessage ($hint)"
        providerMessage != null -> "$code: $providerMessage"
        hint != null -> "$code: $hint"
        else -> "$code: ${defaultMessageForCode(code)}"
    }
}

/**
 * Reads a string field, treating missing / JSON null / literal "null" values as absent.
 * (org.json turns an explicit JSON null into the string "null" through optString.)
 */
internal fun readString(json: JSONObject, key: String): String? {
    if (!json.has(key) || json.isNull(key)) return null
    val value = json.optString(key)
    return value.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
}

private fun extractProviderMessage(body: String?): String? {
    val trimmed = body?.trim()
    if (trimmed.isNullOrEmpty()) return null

    // Some servers answer with plain text or an HTML error page.
    if (!trimmed.startsWith("{")) {
        if (trimmed.startsWith("<")) return null
        return trimmed.take(200).takeIf { it.isNotBlank() }
    }

    return try {
        val json = JSONObject(trimmed)
        val errorObject = json.optJSONObject("error")
        val errorMessage = errorObject?.let { readString(it, "message") }
        val errors = json.optJSONArray("errors")
        when {
            errorMessage != null -> errorMessage
            errors != null && errors.length() > 0 -> readArrayMessage(errors)
            readString(json, "message") != null -> readString(json, "message")
            readString(json, "detail") != null -> readString(json, "detail")
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

private fun readArrayMessage(errors: JSONArray): String? {
    for (i in 0 until errors.length()) {
        when (val item = errors.opt(i)) {
            is JSONObject -> readString(item, "message")?.let { return it }
            is String -> if (item.isNotBlank()) return item
        }
    }
    return null
}

private fun hintForCode(code: Int): String? = when (code) {
    401 -> "check your API key"
    403 -> "access denied, check the key permissions"
    404 -> "check the Base URL and model name"
    429 -> "rate limit reached, try again shortly"
    500, 502, 503, 504 -> "provider server error, try again shortly"
    else -> null
}

private fun defaultMessageForCode(code: Int): String = when (code) {
    400 -> "Bad Request"
    401 -> "Unauthorized, check your API key"
    403 -> "Forbidden, check the key permissions"
    404 -> "Not Found, check the Base URL and model name"
    408 -> "Request timed out"
    429 -> "Too Many Requests, rate limit reached"
    in 500..599 -> "Provider server error"
    else -> "Unexpected error"
}
