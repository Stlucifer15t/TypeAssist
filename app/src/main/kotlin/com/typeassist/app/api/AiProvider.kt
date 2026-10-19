package com.typeassist.app.api

import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.TokenUsage

/** Handle to a request that is already streaming, so the caller can abort it. */
fun interface AiStream {
    fun cancel()
}

interface AiProvider {

    /**
     * Token counts the provider reported for the most recent request, or null when it did not
     * report any (streaming answers, and providers that stay silent). The service uses these for
     * the usage screen and falls back to an estimate when they are missing.
     */
    fun reportedUsage(): TokenUsage? = null

    /**
     * Runs one request. [image] is an optional screenshot that vision-capable providers send
     * alongside the text; providers without vision ignore it.
     */
    fun generateResponse(
        prompt: String,
        userText: String,
        config: AppConfig,
        image: PromptImage? = null,
        callback: (Result<String>) -> Unit
    )

    /**
     * Streams the answer while it is generated: [onDelta] receives each new piece of text as it
     * arrives and [callback] delivers the complete answer once the stream ends (or the error that
     * stopped it).
     *
     * Returns `null` when this provider cannot stream — or when the request could not even be
     * started — so the caller can fall back to [generateResponse]. Providers that do not support
     * streaming simply keep the default implementation. Deltas and the final result are delivered
     * on a background thread. [image] behaves as in [generateResponse].
     */
    fun streamResponse(
        prompt: String,
        userText: String,
        config: AppConfig,
        image: PromptImage? = null,
        onDelta: (String) -> Unit,
        callback: (Result<String>) -> Unit
    ): AiStream? = null
}
