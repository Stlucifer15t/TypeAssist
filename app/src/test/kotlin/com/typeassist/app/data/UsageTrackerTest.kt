package com.typeassist.app.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The counters behind the token-usage tab. They decide what the user sees about their own usage, so
 * both the reported and the estimated paths are checked, along with the stored form.
 */
class UsageTrackerTest {

    @After
    fun reset() {
        UsageTracker.clock = { System.currentTimeMillis() }
        UsageTracker.clearAll()
    }

    @Test
    fun anEstimateRoughlyFollowsTextLength() {
        assertEquals(0, UsageTracker.estimateTokens(""))
        assertEquals(0, UsageTracker.estimateTokens("   "))
        assertEquals(1, UsageTracker.estimateTokens("hey"))
        // 4 characters per token, rounded up: a 41-character text is 11 tokens, not 10.
        assertEquals(11, UsageTracker.estimateTokens("x".repeat(41)))
    }

    @Test
    fun aRequestWithoutReportedNumbersIsEstimated() {
        UsageTracker.record(
            provider = UsageTracker.PROVIDER_GEMINI,
            model = "gemini-3.5-flash-lite",
            promptText = "x".repeat(400),
            answerText = "y".repeat(80)
        )

        val row = UsageTracker.sessionRows().single()
        assertEquals(1, row.requests)
        assertEquals(100L, row.promptTokens)
        assertEquals(20L, row.completionTokens)
        assertEquals(120L, row.totalTokens)
        assertEquals(1, row.estimatedRequests)
    }

    @Test
    fun reportedNumbersWinOverTheEstimate() {
        UsageTracker.record(
            provider = UsageTracker.PROVIDER_OPENAI,
            model = "gpt-4o-mini",
            promptText = "short",
            answerText = "short",
            reported = TokenUsage(promptTokens = 1_234, completionTokens = 567)
        )

        val row = UsageTracker.sessionRows().single()
        assertEquals(1_234L, row.promptTokens)
        assertEquals(567L, row.completionTokens)
        assertEquals(0, row.estimatedRequests)
        assertEquals(0f, UsageTracker.sessionTotals().estimatedShare, 0.0001f)
    }

    @Test
    fun anEmptyReportedUsageFallsBackToTheEstimate() {
        // Some gateways answer with `usage: {prompt_tokens: 0, completion_tokens: 0}`.
        UsageTracker.record(
            provider = UsageTracker.PROVIDER_OPENAI,
            model = "local-proxy",
            promptText = "x".repeat(40),
            answerText = "y".repeat(40),
            reported = TokenUsage(0, 0)
        )

        val row = UsageTracker.sessionRows().single()
        assertEquals(20L, row.totalTokens)
        assertEquals(1, row.estimatedRequests)
    }

    @Test
    fun requestsForTheSameModelAreCumulative() {
        repeat(3) {
            UsageTracker.record(
                provider = UsageTracker.PROVIDER_GEMINI,
                model = "gemini-2.5-flash",
                promptText = "x".repeat(40),
                answerText = "y".repeat(40)
            )
        }
        UsageTracker.record(
            provider = UsageTracker.PROVIDER_LOCAL,
            model = "gemma-3-1b.gguf",
            promptText = "x".repeat(400),
            answerText = "y".repeat(400)
        )

        val rows = UsageTracker.sessionRows()
        assertEquals(2, rows.size)
        // Sorted by tokens, so the heavier provider is first.
        assertEquals(UsageTracker.PROVIDER_LOCAL, rows.first().provider)
        val gemini = rows.first { it.provider == UsageTracker.PROVIDER_GEMINI }
        assertEquals(3, gemini.requests)
        assertEquals(60L, gemini.totalTokens)

        val totals = UsageTracker.sessionTotals()
        assertEquals(4, totals.requests)
        assertTrue(totals.hasNumbers)
    }

    @Test
    fun clearingTheSessionKeepsTheStoredTotals() {
        UsageTracker.record(UsageTracker.PROVIDER_GEMINI, "m", "xxxx", "xxxx")

        UsageTracker.clearSession()

        assertTrue(UsageTracker.sessionRows().isEmpty())
        assertEquals(1, UsageTracker.allTimeTotals().requests)

        UsageTracker.clearAll()
        assertFalse(UsageTracker.allTimeTotals().hasNumbers)
    }

    @Test
    fun storedTotalsSurviveARoundTrip() {
        UsageTracker.record(
            provider = UsageTracker.PROVIDER_CLOUDFLARE,
            model = "@cf/meta/llama-3-8b-instruct",
            promptText = "x".repeat(100),
            answerText = "y".repeat(100),
            reported = TokenUsage(25, 30)
        )
        val dumped = UsageTracker.dump()

        UsageTracker.clearAll()
        UsageTracker.load(dumped)

        val row = UsageTracker.allTimeRows().single()
        assertEquals(UsageTracker.PROVIDER_CLOUDFLARE, row.provider)
        assertEquals(25L, row.promptTokens)
        assertEquals(30L, row.completionTokens)
        assertEquals(1, row.requests)
        assertEquals(0, row.estimatedRequests)
    }

    @Test
    fun damagedStoredTotalsAreIgnoredInsteadOfTrusted() {
        UsageTracker.load("{ this is not json")
        assertTrue(UsageTracker.allTimeRows().isEmpty())

        UsageTracker.load("""{"rows":[{"provider":null,"requests":5},{"provider":"gemini","model":"m","requests":-3,"promptTokens":-9}]}""")

        val rows = UsageTracker.allTimeRows()
        assertEquals(1, rows.size)
        assertEquals(0, rows.single().requests)
        assertEquals(0L, rows.single().promptTokens)
    }

    @Test
    fun missingStoredTotalsAreSimplyEmpty() {
        UsageTracker.load(null)
        UsageTracker.load("")

        assertTrue(UsageTracker.allTimeRows().isEmpty())
        assertEquals(0, UsageTracker.allTimeTotals().requests)
    }

    @Test
    fun providersAndModelsAreLabelledForTheScreen() {
        assertEquals("Gemini", UsageTracker.providerLabel(UsageTracker.PROVIDER_GEMINI))
        assertEquals("OpenAI-compatible", UsageTracker.providerLabel(UsageTracker.PROVIDER_OPENAI))
        assertEquals("Cloudflare Workers AI", UsageTracker.providerLabel(UsageTracker.PROVIDER_CLOUDFLARE))
        assertEquals("On-device model", UsageTracker.providerLabel(UsageTracker.PROVIDER_LOCAL))

        assertEquals("Not set", UsageTracker.shortModel(""))
        assertEquals("model.gguf", UsageTracker.shortModel("/storage/emulated/0/models/model.gguf"))
        assertEquals(27, UsageTracker.shortModel("x".repeat(60)).length)
    }

    @Test
    fun largeNumbersStayReadable() {
        assertEquals("0", UsageTracker.formatTokens(0))
        assertEquals("950", UsageTracker.formatTokens(950))
        assertEquals("1.2k", UsageTracker.formatTokens(1_200))
        assertEquals("12.3k", UsageTracker.formatTokens(12_345))
        assertEquals("1.5M", UsageTracker.formatTokens(1_500_000))
    }
}
