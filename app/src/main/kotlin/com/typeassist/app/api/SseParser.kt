package com.typeassist.app.api

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Incremental reader for Server-Sent Events, the format OpenAI-compatible endpoints and Gemini
 * use for streaming answers.
 *
 * Feed it one line at a time; it returns the payload of an event once that event is complete
 * (a blank line, or the end of the stream via [flush]). Comment lines (`: keep-alive`), other
 * fields and the trailing `\r` of CRLF line endings are handled here, so callers only see the
 * JSON they care about.
 */
class SseEventReader {

    private val data = StringBuilder()

    /** Consumes one line; returns the event payload when the line completes an event. */
    fun feed(line: String): String? {
        val clean = line.removeSuffix("\r")
        if (clean.isEmpty()) return takeEvent()
        // ":" starts a comment; anything that is not "data:" is a field this app does not use.
        if (clean.startsWith(":")) return null
        if (!clean.startsWith("data:")) return null

        val value = clean.removePrefix("data:").let { if (it.startsWith(" ")) it.substring(1) else it }
        if (data.isNotEmpty()) data.append('\n')
        data.append(value)
        return null
    }

    /** Called when the stream ends: some servers do not send a final blank line. */
    fun flush(): String? = takeEvent()

    private fun takeEvent(): String? {
        if (data.isEmpty()) return null
        val payload = data.toString()
        data.setLength(0)
        return payload
    }
}

/**
 * Pulls the text out of one streamed chunk. Both formats are parsed defensively: a malformed or
 * unexpected chunk yields `null` instead of killing the stream.
 */
object StreamChunkParser {

    /** Sentinel OpenAI-compatible endpoints send to mark the end of a stream. */
    const val DONE = "[DONE]"

    /** `choices[0].delta.content` from an OpenAI-compatible chat-completions stream. */
    fun openAiDelta(payload: String): String? {
        val root = parseObject(payload) ?: return null
        val choices = root.get("choices") as? JsonArray ?: return null
        val first = choices.firstObjectOrNull() ?: return null
        val delta = first.get("delta") as? JsonObject ?: return null
        return textOf(delta.get("content"))
    }

    /** `candidates[0].content.parts[*].text` from a Gemini `streamGenerateContent` chunk. */
    fun geminiDelta(payload: String): String? {
        val root = parseObject(payload) ?: return null
        val candidates = root.get("candidates") as? JsonArray ?: return null
        val first = candidates.firstObjectOrNull() ?: return null
        val content = first.get("content") as? JsonObject ?: return null
        val parts = content.get("parts") as? JsonArray ?: return null
        val text = parts.joinText { part ->
            textOf((part as? JsonObject)?.get("text"))
        }
        return text.ifEmpty { null }
    }

    /** The `error.message` of a streamed error chunk, when the provider sends one. */
    fun errorMessage(payload: String): String? {
        val root = parseObject(payload) ?: return null
        val error = root.get("error") as? JsonObject ?: return null
        return textOf(error.get("message"))
    }

    private fun parseObject(payload: String): JsonObject? = try {
        JsonParser.parseString(payload) as? JsonObject
    } catch (e: Exception) {
        null
    }

    private fun JsonArray.firstObjectOrNull(): JsonObject? =
        if (size() == 0) null else get(0) as? JsonObject

    private fun JsonArray.joinText(extract: (JsonElement) -> String?): String {
        val builder = StringBuilder()
        forEach { element -> extract(element)?.let { builder.append(it) } }
        return builder.toString()
    }

    private fun textOf(element: JsonElement?): String? {
        if (element == null || element.isJsonNull) return null
        return try {
            element.asString
        } catch (e: Exception) {
            null
        }
    }
}
