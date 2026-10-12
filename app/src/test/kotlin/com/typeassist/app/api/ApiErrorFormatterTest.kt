package com.typeassist.app.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Every failure the user sees has to say what to do next. These are the messages behind the error
 * chips and the connection-test dialog, so the wording is pinned down here.
 */
class ApiErrorFormatterTest {

    private fun explain(error: Throwable): String = ApiErrorFormatter.explain(error)

    @Test
    fun beingOfflineNamesTheBaseUrlAndTheConnection() {
        val message = explain(UnknownHostException("api.example.com"))

        assertTrue(message.contains("Base URL"))
        assertTrue(message.contains("online"))
    }

    @Test
    fun aTimeoutPointsAtTheTimeoutSetting() {
        val message = explain(SocketTimeoutException("timeout"))

        assertTrue(message.contains("timed out"))
        assertTrue(message.contains("Behaviour"))
    }

    @Test
    fun aBlockedRouteMentionsTheUsualCulprits() {
        val message = explain(NoRouteToHostException("No route to host"))

        assertTrue(message.contains("VPN"))
    }

    @Test
    fun aRefusedConnectionSaysToCheckTheUrl() {
        assertTrue(explain(ConnectException("Connection refused")).contains("Base URL"))
    }

    @Test
    fun httpCodesGetTheirOwnAdvice() {
        assertTrue(explain(IOException("401: {\"error\":\"invalid key\"}")).contains("API key"))
        assertTrue(explain(IOException("402: payment required")).contains("credit"))
        assertTrue(explain(IOException("403: forbidden")).contains("denied"))
        assertTrue(explain(IOException("404: not found")).contains("Base URL"))
        assertTrue(explain(IOException("429: too many requests")).contains("rate-limiting"))
        assertTrue(explain(IOException("451: unavailable for legal reasons")).contains("legal"))
        assertTrue(explain(IOException("500: internal error")).contains("server problem"))
    }

    @Test
    fun aRateLimitIsExplainedAsTemporary() {
        val message = explain(IOException("429: quota exceeded"))

        assertTrue(message.contains("Wait a minute"))
    }

    @Test
    fun aModelListFailureIsExplainedSeparately() {
        val message = explain(ModelCatalogHttpException(404, "not found"))

        assertTrue(message.contains("model list") || message.contains("/models"))
        assertTrue(message.contains("manually"))
    }

    @Test
    fun hittingTheLengthLimitPointsAtMaxTokens() {
        val message = explain(
            IOException("The model ran out of room before answering. Raise Max tokens in Settings")
        )

        assertTrue(message.contains("Max tokens"))
        assertTrue(ApiErrorFormatter.suggestsRaisingTokenLimit(IOException("ran out of room")))
    }

    @Test
    fun aShortAnswerIsNotMistakenForATruncatedOne() {
        assertFalse(ApiErrorFormatter.suggestsRaisingTokenLimit(IOException("401: invalid key")))
        assertFalse(ApiErrorFormatter.suggestsRaisingTokenLimit(IOException("empty response")))
    }

    @Test
    fun anEmptyResponseIsDescribedAsOne() {
        val message = explain(IOException("The provider returned an empty response."))

        assertTrue(message.contains("returned nothing") || message.contains("empty"))
    }

    @Test
    fun anUnknownFailureStillSaysSomethingUsable() {
        val message = explain(IOException())

        assertTrue(message.contains("unknown reason"))
    }

    @Test
    fun aProviderSafetyBlockIsPassedThroughAsIs() {
        val providerText = "The request was blocked by the provider (SAFETY). Rephrase the text."

        assertTrue(explain(IOException(providerText)) == providerText)
    }
}
