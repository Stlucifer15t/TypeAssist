package com.typeassist.app.data

import java.io.Serializable

/** App-wide appearance options stored in [AppConfig.appThemeMode]. */
object AppThemeMode {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
    const val AMOLED = "amoled"

    val ALL = listOf(SYSTEM, LIGHT, DARK, AMOLED)

    /** Returns a valid mode for any raw value (older configs, manual JSON edits…). */
    fun sanitize(raw: String?): String {
        val value = raw?.lowercase()?.trim()
        return if (value in ALL) value!! else SYSTEM
    }
}

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
    var modelPreferences: MutableList<ModelSelectionPreference> = mutableListOf(),
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
    var loadingIndicatorStyle: String = "classic",
    var loadingIndicatorColor: Int = LoadingIndicatorStyle.DEFAULT_COLOR,
    var loadingIndicatorSizePercent: Int = LoadingIndicatorStyle.DEFAULT_SIZE_PERCENT,
    var enablePreviewDialog: Boolean = false,
    var allowTriggerAnywhere: Boolean = false,
    var ignorePrecedingWhitespace: Boolean = false,
    var apiTimeoutSeconds: Long = 30L,
    var appThemeMode: String = AppThemeMode.SYSTEM,
    var useDynamicColor: Boolean = true
) : Serializable

data class CloudflareConfig(
    var accountId: String = "",
    var apiToken: String = "",
    var model: String = "@cf/meta/llama-3-8b-instruct"
) : Serializable

data class SavedGeminiConfig(
    var apiKey: String = "",
    var model: String = ""
) : Serializable

data class CustomApiConfig(
    var baseUrl: String = "https://api.openai.com/v1",
    var apiKey: String = "",
    var model: String = "gpt-3.5-turbo"
) : Serializable

/** Normalised base-URL part of a custom API identity: same endpoint regardless of trailing path noise. */
private fun normaliseBaseUrl(baseUrl: String): String {
    return baseUrl.trim()
        .removeSuffix("/")
        .removeSuffix("/chat/completions")
        .removeSuffix("/models")
        .removeSuffix("/")
        .lowercase()
}

/** Unique identity of a saved custom profile: same base URL + API key = same profile, model not included. */
fun CustomApiConfig.identityKey(): String =
    normaliseBaseUrl(baseUrl) + "\u0000" + apiKey.trim()

/** Unique identity of a saved Gemini profile: the API key alone identifies it. */
fun SavedGeminiConfig.identityKey(): String = apiKey.trim()

/** Unique identity of a saved Cloudflare profile: account ID + token. */
fun CloudflareConfig.identityKey(): String =
    accountId.trim() + "\u0000" + apiToken.trim()

/**
 * Collapses duplicate saved profiles so that only ONE entry exists per identity
 * (custom: base URL + key, Gemini: key, Cloudflare: account + token, local: model path).
 * When duplicates exist, the LAST entry wins (it is the most recently saved, so its
 * model is the current one) and keeps the position of the first occurrence, so the
 * list order stays stable. Changing the model on the same endpoint therefore just
 * overlays the existing profile instead of adding another row.
 */
fun mergeDuplicateProfiles(config: AppConfig): AppConfig {
    var changed = false

    fun <T> dedupe(entries: List<T>, keyOf: (T) -> String): MutableList<T> {
        val positions = LinkedHashMap<String, T>()
        for (entry in entries) {
            val key = keyOf(entry)
            if (positions.containsKey(key)) changed = true
            // LinkedHashMap.put keeps the original position, overwriting the value.
            positions[key] = entry
        }
        return positions.values.toMutableList()
    }

    val custom = dedupe(config.savedCustomConfigs.orEmpty()) { it.identityKey() }
    val gemini = dedupe(config.savedGeminiConfigs.orEmpty()) { it.identityKey() }
    val cloudflare = dedupe(config.savedCloudflareConfigs.orEmpty()) { it.identityKey() }
    val local = dedupe(config.savedLocalModels.orEmpty()) { it.trim() }

    return if (changed) config.copy(
        savedCustomConfigs = custom,
        savedGeminiConfigs = gemini,
        savedCloudflareConfigs = cloudflare,
        savedLocalModels = local
    ) else config
}

data class LocalLlmConfig(
    var modelPath: String = "",
    var temperature: Float = 0.7f,
    var topP: Float = 0.9f,
    var maxTokens: Int = 128,
    var numThreads: Int = 4,
    var useGpu: Boolean = false,
    var disableReasoning: Boolean = false
) : Serializable

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
        modelPreferences = mutableListOf(),
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
        loadingIndicatorStyle = "classic",
        loadingIndicatorColor = LoadingIndicatorStyle.DEFAULT_COLOR,
        loadingIndicatorSizePercent = LoadingIndicatorStyle.DEFAULT_SIZE_PERCENT,
        enablePreviewDialog = false,
        allowTriggerAnywhere = false,
        ignorePrecedingWhitespace = false,
        apiTimeoutSeconds = 30L,
        appThemeMode = AppThemeMode.SYSTEM,
        useDynamicColor = true
    )
}
