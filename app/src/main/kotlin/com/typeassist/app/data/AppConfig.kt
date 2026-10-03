package com.typeassist.app.data

import java.io.Serializable

data class AppConfig(
    var isAppEnabled: Boolean = false,
    var provider: String = "gemini", // "gemini", "cloudflare", "custom", "local"
    var apiKey: String = "",
    var model: String = "gemini-3.5-flash-lite",
    var cloudflareConfig: CloudflareConfig = CloudflareConfig(),
    var customApiConfig: CustomApiConfig = CustomApiConfig(),
    var localLlmConfig: LocalLlmConfig = LocalLlmConfig(),
    var savedLocalModels: MutableList<String> = mutableListOf(),
    var savedCustomConfigs: MutableList<CustomApiConfig> = mutableListOf(),
    var savedGeminiConfigs: MutableList<SavedGeminiConfig> = mutableListOf(),
    var savedCloudflareConfigs: MutableList<CloudflareConfig> = mutableListOf(),
    var triggerDebounceMs: Long = 400L,
    var generationConfig: GenConfig = GenConfig(),
    var triggers: MutableList<Trigger> = mutableListOf(),
    var inlineCommands: MutableList<InlineCommand> = mutableListOf(),
    var snippets: MutableList<Snippet> = mutableListOf(),
    var undoCommandPattern: String = ".undo",
    var snippetTriggerPrefix: String = "ta#",
    var saveSnippetPattern: String = "(.save:%:%)",
    var globalTriggerPattern: String = "...%...",
    var isHistoryEnabled: Boolean = true,
    var enableUndoOverlay: Boolean = true,
    var enableLoadingOverlay: Boolean = true,
    var enablePreviewDialog: Boolean = false,
    var allowTriggerAnywhere: Boolean = false,
    var ignorePrecedingWhitespace: Boolean = false,
    var apiTimeoutSeconds: Long = 30L,
    // Model list read from the Gemini API, cached so the picker also works offline.
    var cachedGeminiModels: MutableList<String> = defaultGeminiModels()
) : Serializable

data class CloudflareConfig(
    var accountId: String = "",
    var apiToken: String = "",
    var model: String = "@cf/meta/llama-3-8b-instruct",
    // Model list read from the Cloudflare API, seeded with popular text models.
    var cachedModels: MutableList<String> = defaultCloudflareModels()
) : Serializable

data class SavedGeminiConfig(
    var apiKey: String = "",
    var model: String = ""
) : Serializable

data class CustomApiConfig(
    var baseUrl: String = "https://api.openai.com/v1",
    var apiKey: String = "",
    var model: String = "gpt-3.5-turbo",
    // Model list read from {baseUrl}/models, cached so the picker also works offline.
    var cachedModels: MutableList<String> = mutableListOf()
) : Serializable

data class LocalLlmConfig(
    var modelPath: String = "",
    var temperature: Float = 0.7f,
    var topP: Float = 0.9f,
    var maxTokens: Int = 128,
    var numThreads: Int = 4,
    var useGpu: Boolean = false,
    var disableReasoning: Boolean = false
) : Serializable

/** Curated Gemini models used until the live list is fetched. */
fun defaultGeminiModels(): MutableList<String> = mutableListOf(
    "gemini-3.5-flash-lite",
    "gemini-3.5-flash",
    "gemini-3.6-flash",
    "gemini-2.5-flash",
    "gemma-4-31b-it",
    "gemma-4-26b-a4b-it"
)

/** Popular Cloudflare Workers AI text models, used as a fallback and as the starter list. */
fun defaultCloudflareModels(): MutableList<String> = mutableListOf(
    "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
    "@cf/meta/llama-3.1-8b-instruct",
    "@cf/meta/llama-3-8b-instruct",
    "@cf/meta/llama-3.2-3b-instruct",
    "@cf/meta/llama-3.2-1b-instruct",
    "@cf/meta/llama-4-scout-17b-16e-instruct",
    "@cf/qwen/qwen1.5-14b-chat-awq",
    "@cf/qwen/qwq-32b",
    "@cf/mistral/mistral-7b-instruct-v0.1",
    "@cf/google/gemma-3-12b-it",
    "@cf/deepseek-ai/deepseek-r1-distill-qwen-32b",
    "@cf/microsoft/phi-2"
)

/** Returns true if the model filename suggests it is a reasoning/thinking model. */
fun isReasoningModel(modelPath: String): Boolean {
    val name = modelPath.substringAfterLast("/").lowercase()
    return listOf(
        "qwq", "qwen3", "deepseek-r", "-r1", "-r2",
        "thinking", "reasoning", "reflect", "magistral", "phi-4-reasoning"
    ).any { name.contains(it) }
}

data class GenConfig(
    var temperature: Double = 0.2,
    var topP: Double = 0.95
) : Serializable

data class Trigger(
    var pattern: String,
    var prompt: String
) : Serializable

data class InlineCommand(
    var pattern: String,
    var prompt: String
) : Serializable

data class Snippet(
    var trigger: String,
    var content: String = "", // For migration
    var contents: MutableList<String> = mutableListOf()
) : Serializable

fun createDefaultConfig(): AppConfig {
    return AppConfig(
        isAppEnabled = false,
        provider = "gemini",
        apiKey = "", 
        model = "gemini-3.5-flash-lite",
        cloudflareConfig = CloudflareConfig(),
        customApiConfig = CustomApiConfig(),
        localLlmConfig = LocalLlmConfig(),
        savedLocalModels = mutableListOf(),
        savedCustomConfigs = mutableListOf(),
        savedGeminiConfigs = mutableListOf(),
        savedCloudflareConfigs = mutableListOf(),
        triggerDebounceMs = 400L,
        generationConfig = GenConfig(temperature = 0.2, topP = 0.95),
        triggers = mutableListOf(
            Trigger(".ta", "Give only the most relevant and complete answer to the query. Do not explain, do not add introductions, disclaimers, or extra text. Output only the answer."),
            Trigger(".g", "Fix grammar, spelling, and punctuation. Return only the corrected text."),
            Trigger(".polite", "Rewrite the text in a polite and professional tone. Return only the rewritten text."),
            Trigger(".casual", "Rewrite in a casual, friendly tone. Return only the rewritten text."),
            Trigger(".improve", "Improve the writing quality and clarity. Return only the improved text."),
            Trigger(".tr", "Translate to English. Return only the translated text.")
        ),
        inlineCommands = mutableListOf(
            InlineCommand("(%:.ta)", "Give only the most relevant and complete answer to the query. Do not explain, do not add introductions, disclaimers, or extra text. Output only the answer."),
            InlineCommand("(%:.g)", "Fix grammar, spelling, and punctuation. Return only the corrected text."),
            InlineCommand("(%:.polite)", "Rewrite the text in a polite and professional tone. Return only the rewritten text.")
        ),
        snippets = mutableListOf(
            Snippet("email", contents = mutableListOf("user@example.com")),
            Snippet("sign", contents = mutableListOf("Best regards,\nUser"))
        ),
        undoCommandPattern = ".undo",
        snippetTriggerPrefix = "..",
        saveSnippetPattern = "(.save:%:%)",
        globalTriggerPattern = "...%...",
        isHistoryEnabled = true,
        enableUndoOverlay = true,
        enableLoadingOverlay = true,
        enablePreviewDialog = false,
        allowTriggerAnywhere = false,
        ignorePrecedingWhitespace = false,
        apiTimeoutSeconds = 30L
    )
}
