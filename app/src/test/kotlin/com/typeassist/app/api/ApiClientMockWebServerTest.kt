package com.typeassist.app.api

import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.CloudflareConfig
import com.typeassist.app.data.CustomApiConfig
import com.typeassist.app.data.createDefaultConfig
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tests for the AI API clients using [MockWebServer]: no live backend is required, every request is
 * answered by the mock server enqueued here.
 *
 * These run as plain JVM unit tests (not instrumentation tests) because MockWebServer is a normal
 * Java library and needs no device - which is also why they now run on every pull request instead
 * of only on a phone.
 *
 * The clients deliver their results on callbacks, so each test blocks on a [CountDownLatch] until
 * the callback fires.
 */
class ApiClientMockWebServerTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun customApiClientReturnsAnswer() {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"choices":[{"message":{"content":"Hello, world!"}}]}""")
            .setHeader("Content-Type", "application/json"))

        val client = CustomApiClient(OkHttpClient())
        val result = callClient(client, customConfig())

        assertEquals("Hello, world!", result)
    }

    @Test
    fun customApiClientSendsAuthorizationAndModel() {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"choices":[{"message":{"content":"ok"}}]}"""))

        val client = CustomApiClient(OkHttpClient())
        callClient(client, customConfig())

        val recorded = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("The client sent no request", recorded)
        assertEquals("Bearer test-key", recorded!!.getHeader("Authorization"))
        val sentBody = recorded.body.readUtf8()
        assertTrue(sentBody.contains("\"model\":\"gpt-3.5-turbo\""))
        assertTrue(sentBody.contains("\"messages\""))
    }

    @Test
    fun customApiClientAppendsChatCompletionsToBaseUrl() {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"choices":[{"message":{"content":"ok"}}]}"""))

        val client = CustomApiClient(OkHttpClient())
        callClient(client, customConfig())

        assertEquals("/v1/chat/completions", server.takeRequest(5, TimeUnit.SECONDS)?.path)
    }

    @Test
    fun customApiClientHandlesUnauthorized() {
        server.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody("""{"error":{"message":"Invalid API key"}}"""))

        val client = CustomApiClient(OkHttpClient())
        val result = callClientExpectFailure(client, customConfig())

        assertTrue(result.contains("401"))
    }

    @Test
    fun customApiClientHandlesEmptyChoices() {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"choices":[]}"""))

        val client = CustomApiClient(OkHttpClient())
        val result = callClientExpectFailure(client, customConfig())

        assertTrue(result.contains("no choices"))
    }

    @Test
    fun customApiClientHandlesAnEmptyAnswer() {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"choices":[{"message":{"content":"   "}}]}"""))

        val client = CustomApiClient(OkHttpClient())
        val result = callClientExpectFailure(client, customConfig())

        assertTrue(result.contains("empty response"))
    }

    @Test
    fun geminiApiClientReturnsAnswer() {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"candidates":[{"content":{"parts":[{"text":"The Gemini answer"}]}}]}"""))

        val config = createDefaultConfig().copy(
            provider = "gemini",
            apiKey = "gemini-key",
            model = "gemini-3.5-flash-lite"
        )

        // Gemini's URL is hardcoded to googleapis, so rewrite it to the mock server.
        val client = GeminiApiClient(rewritingClient())

        assertEquals("The Gemini answer", callClient(client, config))
    }

    @Test
    fun geminiApiClientReportsUsage() {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(
                """{"candidates":[{"content":{"parts":[{"text":"answer"}]}}],""" +
                    """"usageMetadata":{"promptTokenCount":12,"candidatesTokenCount":34}}"""
            ))

        val config = createDefaultConfig().copy(
            provider = "gemini",
            apiKey = "gemini-key",
            model = "gemini-3.5-flash-lite"
        )

        val client = GeminiApiClient(rewritingClient())
        callClient(client, config)

        assertEquals(12, client.reportedUsage()?.promptTokens)
        assertEquals(34, client.reportedUsage()?.completionTokens)
    }

    @Test
    fun geminiApiClientSurfacesSafetyBlocks() {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))

        val config = createDefaultConfig().copy(
            provider = "gemini",
            apiKey = "gemini-key",
            model = "gemini-3.5-flash-lite"
        )

        val client = GeminiApiClient(rewritingClient())
        val message = callClientExpectFailure(client, config)

        assertTrue(message.contains("blocked") || message.contains("SAFETY"))
    }

    @Test
    fun cloudflareApiClientReturnsAnswer() {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(
                """{"result":{"response":"The Cloudflare answer",""" +
                    """"usage":{"prompt_tokens":10,"completion_tokens":5}}}"""
            ))

        val config = cloudflareConfig()

        // Cloudflare's URL is hardcoded to api.cloudflare.com, so rewrite it to the mock server.
        val client = CloudflareApiClient(rewritingClient())

        assertEquals("The Cloudflare answer", callClient(client, config))
    }

    @Test
    fun cloudflareApiClientHandlesServerError() {
        server.enqueue(MockResponse()
            .setResponseCode(500)
            .setBody("""{"errors":[{"message":"Something went wrong"}]}"""))

        val client = CloudflareApiClient(rewritingClient())
        val message = callClientExpectFailure(client, cloudflareConfig())

        assertTrue(message.contains("500"))
    }

    @Test
    fun cloudflareApiClientHandlesAnEmptyAnswer() {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"result":{"response":"   "}}"""))

        val client = CloudflareApiClient(rewritingClient())
        val message = callClientExpectFailure(client, cloudflareConfig())

        assertTrue(message.contains("empty answer"))
    }

    @Test
    fun aRateLimitedFirstAttemptIsRetriedOnce() {
        // First answer: 429. Second answer: success. The client retries transient statuses once.
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"errors":[{"message":"rate limited"}]}"""))
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"choices":[{"message":{"content":"after retry"}}]}"""))

        val client = CustomApiClient(OkHttpClient())
        val result = callClient(client, customConfig())

        assertEquals("after retry", result)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun aBadRequestIsNotRetried() {
        // A 400 would fail again in exactly the same way, so it is reported straight away.
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"bad request"}}"""))

        val client = CustomApiClient(OkHttpClient())
        val message = callClientExpectFailure(client, customConfig())

        assertTrue(message.contains("400"))
        assertEquals(1, server.requestCount)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun customConfig() = createDefaultConfig().copy(
        provider = "custom",
        customApiConfig = CustomApiConfig(
            baseUrl = server.url("/v1").toString(),
            apiKey = "test-key",
            model = "gpt-3.5-turbo"
        )
    )

    private fun cloudflareConfig() = createDefaultConfig().copy(
        provider = "cloudflare",
        cloudflareConfig = CloudflareConfig(
            accountId = "test-account",
            apiToken = "test-token",
            model = "@cf/meta/llama-3-8b-instruct"
        )
    )

    /**
     * Calls [client.generateResponse] and blocks until the callback fires, returning the success
     * value or throwing if the result was a failure.
     */
    private fun callClient(client: AiProvider, config: AppConfig): String {
        val latch = CountDownLatch(1)
        var answer: String? = null
        var failure: Throwable? = null

        client.generateResponse(
            prompt = "Answer the question.",
            userText = "What is 2 + 2?",
            config = config,
            image = null,
            callback = { result ->
                answer = result.getOrNull()
                failure = result.exceptionOrNull()
                latch.countDown()
            }
        )

        assertTrue("Callback never fired", latch.await(30, TimeUnit.SECONDS))
        failure?.let { throw AssertionError("Expected success but got failure: $it") }
        return answer ?: throw AssertionError("No answer value")
    }

    /** Like [callClient] but expects a failure and returns the error message. */
    private fun callClientExpectFailure(client: AiProvider, config: AppConfig): String {
        val latch = CountDownLatch(1)
        var message: String? = null

        client.generateResponse(
            prompt = "Answer the question.",
            userText = "What is 2 + 2?",
            config = config,
            image = null,
            callback = { result ->
                if (result.isFailure) {
                    message = result.exceptionOrNull()?.message ?: "unknown failure"
                }
                latch.countDown()
            }
        )

        assertTrue("Callback never fired", latch.await(30, TimeUnit.SECONDS))
        return message ?: throw AssertionError("Expected failure but got success")
    }

    /**
     * Builds an [OkHttpClient] with a network interceptor that rewrites every outgoing request's
     * host to point at [server]. This tests clients whose URLs are hardcoded (Gemini, Cloudflare)
     * without touching the real network.
     */
    private fun rewritingClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                val original = chain.request()
                val rewritten = original.newBuilder()
                    .url(server.url(original.url.encodedPath))
                    .build()
                chain.proceed(rewritten)
            }
            .build()
    }
}
