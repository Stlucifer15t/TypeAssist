package com.typeassist.app.api

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Turns provider/network errors into short, actionable messages.
 *
 * The rule for every line here: say what the user can do next. "Connection failed" is not an
 * answer — "check the Base URL", "check the key", "wait a minute", or "raise the timeout" is.
 * Messages are matched by error type first and by the provider's own text second, because every
 * provider reports the same problem differently.
 */
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
            // --- Transport -----------------------------------------------------------------
            chain.any { it is UnknownHostException } || lower.contains("unable to resolve host") ->
                "Couldn’t find that server. Check the Base URL for typos, and check that you are online."
            chain.any { it is NoRouteToHostException } ->
                "There is no route to the provider right now. A VPN, a captive-portal login or a " +
                    "switching network usually causes this."
            chain.any { it is SocketTimeoutException } || lower.contains("timed out") || lower.contains("timeout") ->
                "The request timed out. Check your connection, and increase the network timeout in " +
                    "Settings → Behaviour if this provider is consistently slow."
            chain.any { it is ConnectException } || lower.contains("failed to connect") ->
                "Couldn’t connect to the provider. Check the Base URL and try again."
            chain.any { it is SSLException } || lower.contains("ssl") || lower.contains("certificate") ->
                "A secure connection could not be established. Check the provider URL, and check that " +
                    "your device date and time are correct."
            chain.any { it is SocketException } || lower.contains("connection reset") ->
                "The connection dropped. This is usually temporary — try again."

            // --- Credentials and access -----------------------------------------------------
            code == 400 -> "The provider rejected the request. Check that the selected model supports chat completions."
            code == 401 -> "The API key was rejected. Check the key and save it again."
            code == 402 -> "This account has no credit for that model. Add billing with the provider, or pick another model."
            code == 403 -> "Access was denied. Check the key’s permissions, account access, or model access."
            code == 404 && chain.any { it is ModelCatalogHttpException } ->
                "This provider did not return a standard /models list at that URL. Check the Base URL or enter the model ID manually."
            code == 404 -> "The endpoint or model was not found. Check the Base URL and model ID."
            code == 408 -> "The provider timed out. Try again in a moment."
            code == 413 -> "The request is too large for this provider. Try shortening the text."
            code == 422 -> "The provider could not accept those settings. Reset the generation sliders to their defaults."
            code == 429 -> "The provider is rate-limiting requests, or the account quota is used up. " +
                "Wait a minute, then try again, or switch to another model."
            code == 451 -> "The provider refused the request for legal reasons in its region."

            // --- Provider health ------------------------------------------------------------
            code != null && code >= 500 ->
                "The provider is having a server problem (HTTP $code). It usually clears up — try again shortly."

            // --- Answer received, but unusable ----------------------------------------------
            lower.contains("blocked") -> raw
            lower.contains("max_tokens") || lower.contains("maxoutputtokens") || lower.contains("max tokens") ->
                "The answer hit the length limit. Raise Max tokens in Settings → Text generation, or shorten the text."
            lower.contains("empty response") || lower.contains("no text") ->
                "The provider returned nothing. Check the model ID, and try again."
            lower.contains("chat completion") -> raw

            raw.isBlank() -> "The connection failed for an unknown reason. Check your settings and try again."
            else -> "Connection failed: ${raw.take(220)}"
        }
    }

    /** True when the message suggests the answer was cut off by a low token limit. */
    fun suggestsRaisingTokenLimit(error: Throwable): Boolean {
        val raw = generateSequence(error) { it.cause }.mapNotNull { it.message }
            .joinToString(" ").lowercase()
        return raw.contains("ran out of room") || raw.contains("length limit") ||
            raw.contains("max tokens") || raw.contains("max_tokens")
    }
}
