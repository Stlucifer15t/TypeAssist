package com.typeassist.app.api

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Turns provider/network errors into short, actionable messages for the settings UI. */
object ApiErrorFormatter {
    fun explain(error: Throwable): String {
        val chain = generateSequence(error) { it.cause }.toList()
        val raw = chain.mapNotNull { it.message }.joinToString(" ").trim()
        val lower = raw.lowercase()
        val code = Regex("(?:http\\s*)?\\b([1-5]\\d\\d)\\b", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        return when {
            chain.any { it is UnknownHostException } || lower.contains("unable to resolve host") ->
                "Couldn’t find that server. Check the Base URL and your internet connection."
            chain.any { it is SocketTimeoutException } || lower.contains("timed out") || lower.contains("timeout") ->
                "The request timed out. Check your connection or increase the API timeout in General settings."
            chain.any { it is ConnectException } || lower.contains("failed to connect") ->
                "Couldn’t connect to the provider. Check the Base URL and try again."
            chain.any { it is SSLException } || lower.contains("ssl") || lower.contains("certificate") ->
                "A secure connection could not be established. Check the provider URL and device date/time."
            code == 400 -> "The provider rejected the request. Check that the selected model supports chat completions."
            code == 401 -> "The API key was rejected. Check the key and save it again."
            code == 403 -> "Access was denied. Check the key’s permissions, account access, or model access."
            code == 404 && chain.any { it is ModelCatalogHttpException } -> "This provider did not return a standard /models list at that URL. Check the Base URL or enter the model ID manually."
            code == 404 -> "The endpoint or model was not found. Check the Base URL and model ID."
            code == 408 -> "The provider timed out. Try again in a moment."
            code == 413 -> "The request is too large for this provider. Try shortening the text."
            code == 429 -> "The provider is rate-limiting requests or the account quota is exhausted. Try again later."
            code != null && code >= 500 -> "The provider is having a server problem (HTTP $code). Try again later."
            lower.contains("empty response") -> "The provider returned an empty response. Check the model and endpoint."
            raw.isBlank() -> "The connection test failed for an unknown reason. Check your settings and try again."
            else -> "Connection test failed: ${raw.take(220)}"
        }
    }
}
