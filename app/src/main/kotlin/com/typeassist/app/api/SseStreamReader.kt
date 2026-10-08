package com.typeassist.app.api

import okhttp3.Response
import java.io.IOException

/**
 * Reads a Server-Sent Events response body line by line, hands every text delta to the caller and
 * returns the complete answer (or the error that stopped the stream).
 *
 * Shared by the OpenAI-compatible and Gemini clients, which only differ in how a chunk is decoded.
 */
internal object SseStreamReader {

    fun read(
        response: Response,
        onDelta: (String) -> Unit,
        decode: (String) -> String?
    ): Result<String> {
        val source = try {
            response.body?.source()
        } catch (e: Exception) {
            null
        } ?: return Result.failure(IOException("Empty response body"))

        val reader = SseEventReader()
        val answer = StringBuilder()

        fun handle(payload: String) {
            if (payload.trim() == StreamChunkParser.DONE) return
            // Providers report mid-stream problems as a normal chunk carrying an error object.
            StreamChunkParser.errorMessage(payload)?.let { throw IOException(it) }
            val delta = decode(payload) ?: return
            if (delta.isEmpty()) return
            answer.append(delta)
            onDelta(delta)
        }

        return try {
            while (true) {
                val line = source.readUtf8Line() ?: break
                reader.feed(line)?.let(::handle)
            }
            reader.flush()?.let(::handle)
            Result.success(answer.toString())
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { source.close() } catch (e: Exception) { }
        }
    }
}
