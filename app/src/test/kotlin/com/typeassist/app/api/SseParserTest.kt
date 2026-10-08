package com.typeassist.app.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseEventReaderTest {

    @Test
    fun emitsOnePayloadPerBlankLineTerminatedEvent() {
        val reader = SseEventReader()

        assertNull(reader.feed("data: {\"a\":1}"))
        assertEquals("{\"a\":1}", reader.feed(""))
        assertNull(reader.feed("data: {\"b\":2}"))
        assertEquals("{\"b\":2}", reader.feed(""))
    }

    @Test
    fun handlesCrlfLineEndingsAndMissingSpace() {
        val reader = SseEventReader()

        assertNull(reader.feed("data:{\"a\":1}\r"))
        assertEquals("{\"a\":1}", reader.feed("\r"))
    }

    @Test
    fun ignoresCommentsEmptyLinesAndOtherFields() {
        val reader = SseEventReader()

        assertNull(reader.feed(": keep-alive"))
        assertNull(reader.feed("event: message"))
        assertNull(reader.feed("id: 42"))
        assertNull(reader.feed(""))
        assertNull(reader.feed("data: {\"a\":1}"))
        assertEquals("{\"a\":1}", reader.feed(""))
    }

    @Test
    fun joinsMultiLineDataIntoASinglePayload() {
        val reader = SseEventReader()

        assertNull(reader.feed("data: first"))
        assertNull(reader.feed("data: second"))
        assertEquals("first\nsecond", reader.feed(""))
    }

    @Test
    fun flushReturnsTheLastEventWhenTheServerOmitsTheBlankLine() {
        val reader = SseEventReader()

        assertNull(reader.feed("data: {\"a\":1}"))
        assertEquals("{\"a\":1}", reader.flush())
        assertNull(reader.flush())
    }
}

class StreamChunkParserTest {

    @Test
    fun readsOpenAiDeltaContent() {
        val payload = """{"id":"1","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}"""

        assertEquals("Hello", StreamChunkParser.openAiDelta(payload))
    }

    @Test
    fun openAiRoleOnlyChunksCarryNoText() {
        val payload = """{"choices":[{"delta":{"role":"assistant"}}]}"""

        assertNull(StreamChunkParser.openAiDelta(payload))
    }

    @Test
    fun readsOpenAiErrorChunks() {
        val payload = """{"error":{"message":"Quota exceeded","type":"rate_limit"}}"""

        assertEquals("Quota exceeded", StreamChunkParser.errorMessage(payload))
        assertNull(StreamChunkParser.openAiDelta(payload))
    }

    @Test
    fun readsGeminiDeltaAndJoinsParts() {
        val single = """{"candidates":[{"content":{"parts":[{"text":"Bonjour"}],"role":"model"}}]}"""
        val multi = """{"candidates":[{"content":{"parts":[{"text":"Bon"},{"text":"jour"}]}}]}"""

        assertEquals("Bonjour", StreamChunkParser.geminiDelta(single))
        assertEquals("Bonjour", StreamChunkParser.geminiDelta(multi))
    }

    @Test
    fun incompleteGeminiChunksYieldNothing() {
        // Gemini sends prompt feedback first, and a candidate with no parts when it is blocked.
        assertNull(StreamChunkParser.geminiDelta("""{"candidates":[]}"""))
        assertNull(StreamChunkParser.geminiDelta("""{"candidates":[{"content":{"parts":[]}}]}"""))
        assertNull(StreamChunkParser.geminiDelta("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
    }

    @Test
    fun malformedChunksNeverThrow() {
        assertNull(StreamChunkParser.openAiDelta("not json at all"))
        assertNull(StreamChunkParser.openAiDelta(""))
        assertNull(StreamChunkParser.openAiDelta("""{"choices":"nope"}"""))
        assertNull(StreamChunkParser.geminiDelta("[1,2,3]"))
        assertNull(StreamChunkParser.errorMessage("{}"))
    }

    @Test
    fun aWholeOpenAiStreamIsParsedEndToEnd() {
        val stream = listOf(
            """data: {"choices":[{"delta":{"role":"assistant"}}]}""",
            "",
            """data: {"choices":[{"delta":{"content":"Hel"}}]}""",
            "",
            """data: {"choices":[{"delta":{"content":"lo"}}]}""",
            "",
            "data: [DONE]",
            ""
        )

        val reader = SseEventReader()
        val answer = StringBuilder()
        for (line in stream) {
            val payload = reader.feed(line) ?: continue
            if (payload == StreamChunkParser.DONE) break
            StreamChunkParser.openAiDelta(payload)?.let { answer.append(it) }
        }

        assertEquals("Hello", answer.toString())
    }
}
