package com.typeassist.app.api

import com.typeassist.app.data.AppConfig

/** Handle to a request that is already streaming, so the caller can abort it. */
fun interface AiStream {
    fun cancel()
}

interface AiProvider {
    fun generateResponse(
        prompt: String,
        userText: String,
        config: AppConfig,
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
     * on a background thread.
     */
    fun streamResponse(
        prompt: String,
        userText: String,
        config: AppConfig,
        onDelta: (String) -> Unit,
        callback: (Result<String>) -> Unit
    ): AiStream? = null
}
