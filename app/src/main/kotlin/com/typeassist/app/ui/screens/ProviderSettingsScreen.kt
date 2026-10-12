package com.typeassist.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.typeassist.app.api.ApiErrorFormatter
import com.typeassist.app.api.CloudflareApiClient
import com.typeassist.app.api.CustomApiClient
import com.typeassist.app.api.ModelCatalogClient
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.CloudflareConfig
import com.typeassist.app.data.CustomApiConfig
import com.typeassist.app.data.GenerationSettings
import com.typeassist.app.data.ModelSelectionPreferences
import com.typeassist.app.data.SavedGeminiConfig
import com.typeassist.app.data.identityKey
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.components.AppCard
import com.typeassist.app.ui.components.AppDialog
import com.typeassist.app.ui.components.AppScaffold
import com.typeassist.app.ui.components.AppTextField
import com.typeassist.app.ui.components.AppTopBar
import com.typeassist.app.ui.components.CardFact
import com.typeassist.app.ui.components.ChoiceRow
import com.typeassist.app.ui.components.GroupCard
import com.typeassist.app.ui.components.ModelPicker
import com.typeassist.app.ui.components.NoticeCard
import com.typeassist.app.ui.components.NoticeTone
import com.typeassist.app.ui.components.PrimaryButton
import com.typeassist.app.ui.components.RowDivider
import com.typeassist.app.ui.components.ScreenIntro
import com.typeassist.app.ui.components.SectionHeader
import com.typeassist.app.ui.components.SecondaryButton
import com.typeassist.app.ui.components.SettingRow
import com.typeassist.app.ui.components.StatusPill
import com.typeassist.app.ui.components.StatusTone
import com.typeassist.app.ui.extra
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.Locale
import kotlin.coroutines.coroutineContext

private data class ConnectionTestResult(
    val succeeded: Boolean,
    val title: String,
    val message: String,
    /** Round-trip time of the test request, so a slow provider is visible rather than guessed at. */
    val latencyMs: Long? = null,
    /** The failure was a timeout, which the network-timeout setting can fix. */
    val suggestsLongerTimeout: Boolean = false
)

/**
 * ============================== PROVIDER & MODEL ==============================
 *
 * Four providers, one at a time. The choice is a list of rows rather than a dropdown so the
 * alternatives are visible without a tap, and each provider gets exactly the fields it needs:
 * a key and a model for Gemini, account + token + model for Cloudflare, base URL + optional key +
 * model for OpenAI-compatible services, and a link to the local model screen for on-device models.
 *
 * Saving runs a real connection test and reports what happened in plain language. The test is the
 * point: a provider that silently fails is the most common source of confusion in an app like this.
 */
@Composable
fun ProviderSettingsScreen(
    config: AppConfig,
    client: OkHttpClient,
    onSave: (AppConfig) -> Unit,
    onBack: () -> Unit,
    onNavigateLocal: () -> Unit,
    onNavigateGeneration: () -> Unit,
    onNavigateBehaviour: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelCatalog = remember(client) { ModelCatalogClient(client) }

    var selectedProvider by remember(config.provider) {
        mutableStateOf(if (config.provider == "local") "local" else config.provider)
    }
    var geminiKey by remember(config.apiKey) { mutableStateOf(config.apiKey) }
    var geminiModel by remember(config.model) { mutableStateOf(config.model) }
    var customBaseUrl by remember(config.customApiConfig.baseUrl) { mutableStateOf(config.customApiConfig.baseUrl) }
    var customApiKey by remember(config.customApiConfig.apiKey) { mutableStateOf(config.customApiConfig.apiKey) }
    var customModel by remember(config.customApiConfig.model) { mutableStateOf(config.customApiConfig.model) }
    var cfAccountId by remember(config.cloudflareConfig.accountId) { mutableStateOf(config.cloudflareConfig.accountId) }
    var cfApiToken by remember(config.cloudflareConfig.apiToken) { mutableStateOf(config.cloudflareConfig.apiToken) }
    var cfModel by remember(config.cloudflareConfig.model) { mutableStateOf(config.cloudflareConfig.model) }
    var keyVisible by remember { mutableStateOf(false) }

    var geminiModels by remember { mutableStateOf(emptyList<String>()) }
    var geminiModelsLoading by remember { mutableStateOf(false) }
    var geminiModelsMessage by remember { mutableStateOf<String?>(null) }
    var geminiModelsError by remember { mutableStateOf(false) }
    var geminiModelLoadJob by remember { mutableStateOf<Job?>(null) }

    var customModels by remember { mutableStateOf(emptyList<String>()) }
    var customModelsLoading by remember { mutableStateOf(false) }
    var customModelsMessage by remember { mutableStateOf<String?>(null) }
    var customModelsError by remember { mutableStateOf(false) }
    var customModelLoadJob by remember { mutableStateOf<Job?>(null) }

    var isTesting by remember { mutableStateOf(false) }
    var connectionResult by remember { mutableStateOf<ConnectionTestResult?>(null) }

    val preferences = config.modelPreferences ?: mutableListOf()
    val geminiHistory = ModelSelectionPreferences.forEndpoint(preferences, "gemini", "gemini")
    val customHistory = ModelSelectionPreferences.forEndpoint(preferences, "custom", customBaseUrl)
    val geminiFavorites = geminiHistory.filter { it.isFavorite }.map { it.modelId }.distinct()
    val geminiRecents = geminiHistory.filter { it.lastSelectedAt > 0L }
        .sortedByDescending { it.lastSelectedAt }.map { it.modelId }.distinct()
    val customFavorites = customHistory.filter { it.isFavorite }.map { it.modelId }.distinct()
    val customRecents = customHistory.filter { it.lastSelectedAt > 0L }
        .sortedByDescending { it.lastSelectedAt }.map { it.modelId }.distinct()

    fun recordModelSelection(provider: String, endpoint: String, model: String) {
        if (model.isBlank()) return
        onSave(
            config.copy(
                modelPreferences = ModelSelectionPreferences.recordSelection(
                    config.modelPreferences,
                    provider,
                    endpoint,
                    model
                )
            )
        )
    }

    fun toggleModelFavorite(provider: String, endpoint: String, model: String) {
        onSave(
            config.copy(
                modelPreferences = ModelSelectionPreferences.toggleFavorite(
                    config.modelPreferences,
                    provider,
                    endpoint,
                    model
                )
            )
        )
    }

    fun loadGeminiModels() {
        if (geminiKey.isBlank()) {
            geminiModelsMessage = "Enter a Gemini API key before loading models."
            geminiModelsError = true
            return
        }
        geminiModelLoadJob?.cancel()
        geminiModelsLoading = true
        geminiModelsMessage = null
        geminiModelLoadJob = scope.launch {
            try {
                geminiModels = modelCatalog.fetchGeminiModels(geminiKey.trim())
                geminiModelsMessage = "Loaded ${geminiModels.size} models that support text generation."
                geminiModelsError = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                geminiModelsMessage = ApiErrorFormatter.explain(e)
                geminiModelsError = true
            } finally {
                if (geminiModelLoadJob === coroutineContext[Job]) geminiModelsLoading = false
            }
        }
    }

    fun loadCustomModels() {
        if (customBaseUrl.isBlank()) {
            customModelsMessage = "Enter the provider base URL first."
            customModelsError = true
            return
        }
        customModelLoadJob?.cancel()
        customModelsLoading = true
        customModelsMessage = null
        customModelLoadJob = scope.launch {
            try {
                customModels = modelCatalog.fetchOpenAiCompatibleModels(
                    customBaseUrl.trim(),
                    customApiKey.trim()
                )
                customModelsMessage = "Loaded ${customModels.size} model IDs. Pick one, or keep typing."
                customModelsError = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                customModelsMessage = ApiErrorFormatter.explain(e)
                customModelsError = true
            } finally {
                if (customModelLoadJob === coroutineContext[Job]) customModelsLoading = false
            }
        }
    }

    fun finishTest(
        succeeded: Boolean,
        title: String,
        message: String,
        startedAt: Long? = null,
        error: Throwable? = null
    ) {
        isTesting = false
        val elapsed = startedAt?.let { System.currentTimeMillis() - it }
        val timeoutHint = error != null && message.contains("timed out", ignoreCase = true)
        connectionResult = ConnectionTestResult(
            succeeded = succeeded,
            title = title,
            message = message,
            latencyMs = elapsed,
            suggestsLongerTimeout = timeoutHint
        )
    }

    fun runGeminiTest(apiKey: String, model: String) {
        if (apiKey.isBlank()) {
            finishTest(
                false,
                "Gemini setup needs attention",
                "Enter your Gemini API key, then try the connection test again."
            )
            return
        }
        isTesting = true
        val startedAt = System.currentTimeMillis()
        scope.launch {
            try {
                val available = modelCatalog.fetchGeminiModels(apiKey.trim())
                geminiModels = available
                geminiModelsMessage = "Loaded ${available.size} models that support text generation."
                geminiModelsError = false
                if (model.isBlank()) {
                    finishTest(
                        true,
                        "Gemini connection successful",
                        "The API key works and ${available.size} text-generation models are available.",
                        startedAt
                    )
                } else if (available.any { it.equals(model.trim(), ignoreCase = true) }) {
                    finishTest(
                        true,
                        "Gemini connection successful",
                        "The API key works and “${model.trim()}” is available for text generation.",
                        startedAt
                    )
                } else {
                    finishTest(
                        false,
                        "Gemini connected, model not found",
                        "The key is valid, but “${model.trim()}” was not in the available text-generation " +
                            "models. Refresh the list and choose an available model, or enter a model ID " +
                            "your account supports.",
                        startedAt
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                finishTest(false, "Gemini connection failed", ApiErrorFormatter.explain(e), startedAt, e)
            }
        }
    }

    fun runCustomTest(baseUrl: String, apiKey: String, model: String) {
        if (baseUrl.isBlank() || model.isBlank()) {
            finishTest(
                false,
                "Custom API setup needs attention",
                "Enter both a base URL and a model ID before testing this provider."
            )
            return
        }
        isTesting = true
        val startedAt = System.currentTimeMillis()
        try {
            CustomApiClient(client).callCustomApi(
                baseUrl = baseUrl.trim(),
                apiKey = apiKey.trim(),
                model = model.trim(),
                prompt = "You are a connection-test assistant. Reply with exactly: Connected.",
                userText = "Reply with exactly: Connected.",
                generation = GenerationSettings.sanitize(config.generationConfig),
                timeoutSeconds = config.apiTimeoutSeconds
            ) { result ->
                scope.launch {
                    result.fold(
                        onSuccess = {
                            finishTest(
                                true,
                                "Custom API connection successful",
                                "The endpoint accepted the request and returned a model response.",
                                startedAt
                            )
                        },
                        onFailure = {
                            finishTest(
                                false,
                                "Custom API connection failed",
                                ApiErrorFormatter.explain(it),
                                startedAt,
                                it
                            )
                        }
                    )
                }
            }
        } catch (e: Exception) {
            finishTest(false, "Custom API connection failed", ApiErrorFormatter.explain(e), startedAt, e)
        }
    }

    fun runCloudflareTest(accountId: String, token: String, model: String) {
        if (accountId.isBlank() || token.isBlank() || model.isBlank()) {
            finishTest(
                false,
                "Cloudflare setup needs attention",
                "Enter the account ID, API token and model ID before testing."
            )
            return
        }
        isTesting = true
        val startedAt = System.currentTimeMillis()
        try {
            CloudflareApiClient(client).callCloudflare(
                accountId = accountId.trim(),
                apiToken = token.trim(),
                model = model.trim(),
                prompt = "You are a connection-test assistant. Reply with exactly: Connected.",
                userText = "Reply with exactly: Connected.",
                generation = GenerationSettings.sanitize(config.generationConfig),
                timeoutSeconds = config.apiTimeoutSeconds
            ) { result ->
                scope.launch {
                    result.fold(
                        onSuccess = {
                            finishTest(
                                true,
                                "Cloudflare connection successful",
                                "The account accepted the request and returned a model response.",
                                startedAt
                            )
                        },
                        onFailure = {
                            finishTest(
                                false,
                                "Cloudflare connection failed",
                                ApiErrorFormatter.explain(it),
                                startedAt,
                                it
                            )
                        }
                    )
                }
            }
        } catch (e: Exception) {
            finishTest(false, "Cloudflare connection failed", ApiErrorFormatter.explain(e), startedAt, e)
        }
    }

    AppScaffold(
        topBar = { AppTopBar(title = "Provider & model", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = Spacing.sm,
                bottom = Spacing.xxxl
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item {
                ScreenIntro(
                    "Choose where the AI runs. Prompts, snippets and utilities work without a " +
                        "provider; only the AI commands need one."
                )
            }

            item {
                SettingRow(
                    title = "Text generation",
                    description = GenerationSettings.summary(config),
                    icon = Icons.Default.Tune,
                    onClick = onNavigateGeneration
                ) {
                    StatusPill(
                        text = when (selectedProvider) {
                            "gemini" -> "Gemini"
                            "cloudflare" -> "Cloudflare"
                            "custom" -> "OpenAI-compatible"
                            else -> "On-device"
                        },
                        tone = if (config.provider == selectedProvider) StatusTone.Active else StatusTone.Idle
                    )
                }
            }

            item { SectionHeader(title = "Provider") }
            item {
                GroupCard {
                    ChoiceRow(
                        title = "Google Gemini",
                        description = "Model list is fetched from your key and filtered to text generation.",
                        selected = selectedProvider == "gemini",
                        useRadio = false,
                        onSelect = { selectedProvider = "gemini" }
                    )
                    RowDivider()
                    ChoiceRow(
                        title = "Cloudflare Workers AI",
                        description = "Account ID, API token and a model such as @cf/meta/llama-3-8b-instruct.",
                        selected = selectedProvider == "cloudflare",
                        useRadio = false,
                        onSelect = { selectedProvider = "cloudflare" }
                    )
                    RowDivider()
                    ChoiceRow(
                        title = "OpenAI-compatible API",
                        description = "Anything exposing /chat/completions and optionally /models.",
                        selected = selectedProvider == "custom",
                        useRadio = false,
                        onSelect = { selectedProvider = "custom" }
                    )
                    RowDivider()
                    ChoiceRow(
                        title = "On-device model",
                        description = "Runs a GGUF file locally with llama.cpp. No key, no network.",
                        selected = selectedProvider == "local",
                        useRadio = false,
                        onSelect = { selectedProvider = "local" }
                    )
                }
            }

            when (selectedProvider) {
                "gemini" -> {
                    if (config.savedGeminiConfigs.isNotEmpty()) {
                        item { SectionHeader(title = "Saved Gemini profiles") }
                        item {
                            GroupCard {
                                config.savedGeminiConfigs.forEachIndexed { index, saved ->
                                    SavedProfileRow(
                                        title = saved.model.ifBlank { "Gemini profile ${index + 1}" },
                                        subtitle = "Key ending ${saved.apiKey.takeLast(4).ifBlank { "••••" }}",
                                        onLoad = {
                                            geminiKey = saved.apiKey
                                            geminiModel = saved.model
                                            geminiModelLoadJob?.cancel()
                                            geminiModels = emptyList()
                                            geminiModelsMessage = null
                                            geminiModelsLoading = false
                                        },
                                        onDelete = {
                                            onSave(
                                                config.copy(
                                                    savedGeminiConfigs = config.savedGeminiConfigs
                                                        .toMutableList().apply { remove(saved) }
                                                )
                                            )
                                        }
                                    )
                                    if (index != config.savedGeminiConfigs.lastIndex) RowDivider()
                                }
                            }
                        }
                    }
                    item { SectionHeader(title = "Gemini") }
                    item {
                        AppCard {
                            SecureKeyField(
                                value = geminiKey,
                                label = "Gemini API key",
                                visible = keyVisible,
                                onValueChange = {
                                    geminiKey = it
                                    geminiModelLoadJob?.cancel()
                                    geminiModels = emptyList()
                                    geminiModelsMessage = null
                                    geminiModelsLoading = false
                                },
                                onToggleVisibility = { keyVisible = !keyVisible }
                            )
                            Spacer(Modifier.height(Spacing.md))
                            ModelPicker(
                                value = geminiModel,
                                onValueChange = { geminiModel = it },
                                availableModels = geminiModels,
                                favoriteModels = geminiFavorites,
                                recentModels = geminiRecents,
                                isLoading = geminiModelsLoading,
                                statusMessage = geminiModelsMessage,
                                statusIsError = geminiModelsError,
                                onLoadModels = { loadGeminiModels() },
                                onSelectModel = {
                                    geminiModel = it
                                    recordModelSelection("gemini", "gemini", it)
                                },
                                onToggleFavorite = { toggleModelFavorite("gemini", "gemini", it) },
                                description = "Manual entry always works, even before loading the list."
                            )
                        }
                    }
                }
                "custom" -> {
                    if (config.savedCustomConfigs.isNotEmpty()) {
                        item { SectionHeader(title = "Saved API profiles") }
                        item {
                            GroupCard {
                                config.savedCustomConfigs.forEachIndexed { index, saved ->
                                    SavedProfileRow(
                                        title = saved.model.ifBlank { "Custom profile ${index + 1}" },
                                        subtitle = saved.baseUrl,
                                        onLoad = {
                                            customBaseUrl = saved.baseUrl
                                            customApiKey = saved.apiKey
                                            customModel = saved.model
                                            customModelLoadJob?.cancel()
                                            customModels = emptyList()
                                            customModelsMessage = null
                                            customModelsLoading = false
                                        },
                                        onDelete = {
                                            onSave(
                                                config.copy(
                                                    savedCustomConfigs = config.savedCustomConfigs
                                                        .toMutableList().apply { remove(saved) }
                                                )
                                            )
                                        }
                                    )
                                    if (index != config.savedCustomConfigs.lastIndex) RowDivider()
                                }
                            }
                        }
                    }
                    item { SectionHeader(title = "Endpoint") }
                    item {
                        AppCard {
                            AppTextField(
                                value = customBaseUrl,
                                onValueChange = {
                                    customBaseUrl = it
                                    customModelLoadJob?.cancel()
                                    customModels = emptyList()
                                    customModelsMessage = null
                                    customModelsLoading = false
                                },
                                label = "Base URL",
                                placeholder = "https://api.openai.com/v1",
                                monospace = true
                            )
                            Spacer(Modifier.height(Spacing.md))
                            SecureKeyField(
                                value = customApiKey,
                                label = "API key (optional for some endpoints)",
                                visible = keyVisible,
                                onValueChange = {
                                    customApiKey = it
                                    customModelLoadJob?.cancel()
                                    customModels = emptyList()
                                    customModelsMessage = null
                                    customModelsLoading = false
                                },
                                onToggleVisibility = { keyVisible = !keyVisible }
                            )
                            Spacer(Modifier.height(Spacing.md))
                            ModelPicker(
                                value = customModel,
                                onValueChange = { customModel = it },
                                availableModels = customModels,
                                favoriteModels = customFavorites,
                                recentModels = customRecents,
                                isLoading = customModelsLoading,
                                statusMessage = customModelsMessage,
                                statusIsError = customModelsError,
                                onLoadModels = { loadCustomModels() },
                                onSelectModel = {
                                    customModel = it
                                    recordModelSelection("custom", customBaseUrl, it)
                                },
                                onToggleFavorite = { toggleModelFavorite("custom", customBaseUrl, it) },
                                description = "Some providers do not publish /models; typing the ID works too."
                            )
                        }
                    }
                }
                "cloudflare" -> {
                    if (config.savedCloudflareConfigs.isNotEmpty()) {
                        item { SectionHeader(title = "Saved Cloudflare profiles") }
                        item {
                            GroupCard {
                                config.savedCloudflareConfigs.forEachIndexed { index, saved ->
                                    SavedProfileRow(
                                        title = saved.model.ifBlank { "Cloudflare profile ${index + 1}" },
                                        subtitle = "Account ${saved.accountId.take(6)}…",
                                        onLoad = {
                                            cfAccountId = saved.accountId
                                            cfApiToken = saved.apiToken
                                            cfModel = saved.model
                                        },
                                        onDelete = {
                                            onSave(
                                                config.copy(
                                                    savedCloudflareConfigs = config.savedCloudflareConfigs
                                                        .toMutableList().apply { remove(saved) }
                                                )
                                            )
                                        }
                                    )
                                    if (index != config.savedCloudflareConfigs.lastIndex) RowDivider()
                                }
                            }
                        }
                    }
                    item { SectionHeader(title = "Cloudflare Workers AI") }
                    item {
                        AppCard {
                            AppTextField(
                                value = cfAccountId,
                                onValueChange = { cfAccountId = it },
                                label = "Account ID",
                                monospace = true
                            )
                            Spacer(Modifier.height(Spacing.md))
                            SecureKeyField(
                                value = cfApiToken,
                                label = "API token",
                                visible = keyVisible,
                                onValueChange = { cfApiToken = it },
                                onToggleVisibility = { keyVisible = !keyVisible }
                            )
                            Spacer(Modifier.height(Spacing.md))
                            AppTextField(
                                value = cfModel,
                                onValueChange = { cfModel = it },
                                label = "Model ID",
                                placeholder = "@cf/meta/llama-3-8b-instruct",
                                monospace = true
                            )
                        }
                    }
                }
                else -> {
                    if (config.savedLocalModels.isNotEmpty()) {
                        item { SectionHeader(title = "Previously used models") }
                        item {
                            GroupCard {
                                config.savedLocalModels.forEachIndexed { index, savedPath ->
                                    SavedProfileRow(
                                        title = savedPath.substringAfterLast('/').ifBlank { "Local GGUF model" },
                                        subtitle = "Tap to activate this model",
                                        onLoad = {
                                            onSave(
                                                config.copy(
                                                    localLlmConfig = config.localLlmConfig
                                                        .copy(modelPath = savedPath)
                                                )
                                            )
                                            Toast.makeText(context, "Local model selected.", Toast.LENGTH_SHORT).show()
                                        },
                                        onDelete = {
                                            onSave(
                                                config.copy(
                                                    savedLocalModels = config.savedLocalModels
                                                        .toMutableList().apply { remove(savedPath) }
                                                )
                                            )
                                        }
                                    )
                                    if (index != config.savedLocalModels.lastIndex) RowDivider()
                                }
                            }
                        }
                    }
                    item {
                        NoticeCard(
                            title = "On-device model",
                            message = "Local inference uses no API key and no network, but it is slower " +
                                "and uses more battery than a cloud provider. Choose a GGUF file and " +
                                "tune it on the local model screen.",
                            tone = NoticeTone.Info,
                            icon = Icons.Default.Memory,
                            actionLabel = "Open local model settings",
                            onAction = onNavigateLocal
                        )
                    }
                }
            }

            item {
                AppCard {
                    PrimaryButton(
                        text = if (selectedProvider == "local") "Save provider" else "Test connection & save",
                        onClick = {
                            val newCustomConfig = CustomApiConfig(
                                baseUrl = customBaseUrl.trim(),
                                apiKey = customApiKey.trim(),
                                model = customModel.trim()
                            )
                            val newGeminiConfig = SavedGeminiConfig(
                                apiKey = geminiKey.trim(),
                                model = geminiModel.trim()
                            )
                            val newCloudflareConfig = CloudflareConfig(
                                accountId = cfAccountId.trim(),
                                apiToken = cfApiToken.trim(),
                                model = cfModel.trim()
                            )

                            // One profile per identity: saving the same endpoint + key again overlays the
                            // existing row (updating its model) instead of adding a duplicate.
                            val savedCustom = config.savedCustomConfigs.toMutableList()
                            if (selectedProvider == "custom" && customBaseUrl.isNotBlank() && customModel.isNotBlank()) {
                                val index = savedCustom.indexOfFirst { it.identityKey() == newCustomConfig.identityKey() }
                                if (index >= 0) savedCustom[index] = newCustomConfig else savedCustom.add(newCustomConfig)
                            }
                            val savedGemini = config.savedGeminiConfigs.toMutableList()
                            if (selectedProvider == "gemini" && geminiKey.isNotBlank()) {
                                val index = savedGemini.indexOfFirst { it.identityKey() == newGeminiConfig.identityKey() }
                                if (index >= 0) savedGemini[index] = newGeminiConfig else savedGemini.add(newGeminiConfig)
                            }
                            val savedCloudflare = config.savedCloudflareConfigs.toMutableList()
                            if (selectedProvider == "cloudflare" && cfApiToken.isNotBlank()) {
                                val index = savedCloudflare.indexOfFirst { it.identityKey() == newCloudflareConfig.identityKey() }
                                if (index >= 0) savedCloudflare[index] = newCloudflareConfig else savedCloudflare.add(newCloudflareConfig)
                            }
                            val savedLocal = config.savedLocalModels
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .toMutableList()
                            if (selectedProvider == "local" && config.localLlmConfig.modelPath.isNotBlank()) {
                                val path = config.localLlmConfig.modelPath.trim()
                                if (path !in savedLocal) savedLocal.add(path)
                            }

                            var updatedPreferences = config.modelPreferences ?: mutableListOf()
                            when (selectedProvider) {
                                "gemini" -> updatedPreferences = ModelSelectionPreferences.recordSelection(
                                    updatedPreferences, "gemini", "gemini", geminiModel
                                )
                                "custom" -> updatedPreferences = ModelSelectionPreferences.recordSelection(
                                    updatedPreferences, "custom", customBaseUrl, customModel
                                )
                            }

                            var updatedConfig = config.copy(
                                provider = selectedProvider,
                                apiKey = geminiKey.trim(),
                                model = geminiModel.trim(),
                                cloudflareConfig = newCloudflareConfig,
                                customApiConfig = newCustomConfig,
                                savedCustomConfigs = savedCustom,
                                savedGeminiConfigs = savedGemini,
                                savedCloudflareConfigs = savedCloudflare,
                                savedLocalModels = savedLocal,
                                modelPreferences = updatedPreferences
                            )
                            val configured = when (selectedProvider) {
                                "gemini" -> geminiKey.isNotBlank()
                                "cloudflare" -> cfApiToken.isNotBlank()
                                "custom" -> customBaseUrl.isNotBlank() && customModel.isNotBlank()
                                "local" -> config.localLlmConfig.modelPath.isNotBlank()
                                else -> false
                            }
                            if (!config.isAppEnabled && configured) {
                                updatedConfig = updatedConfig.copy(isAppEnabled = true)
                            }
                            onSave(updatedConfig)
                            Toast.makeText(context, "Provider settings saved.", Toast.LENGTH_SHORT).show()

                            // Fail fast with a real reason instead of a slow timeout when the
                            // device has no connection at all.
                            if (selectedProvider != "local" && !hasInternet(context)) {
                                connectionResult = ConnectionTestResult(
                                    succeeded = false,
                                    title = "No internet connection",
                                    message = "Settings were saved, but the connection test needs a " +
                                        "network. Connect to Wi-Fi or mobile data and test again."
                                )
                                return@PrimaryButton
                            }

                            when (selectedProvider) {
                                "gemini" -> runGeminiTest(geminiKey.trim(), geminiModel.trim())
                                "cloudflare" -> runCloudflareTest(cfAccountId.trim(), cfApiToken.trim(), cfModel.trim())
                                "custom" -> runCustomTest(customBaseUrl.trim(), customApiKey.trim(), customModel.trim())
                                else -> connectionResult = ConnectionTestResult(
                                    true,
                                    "Local provider saved",
                                    "Your local model selection has been saved."
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        loading = isTesting,
                        enabled = !isTesting,
                        icon = Icons.Default.Cloud
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = "The test sends one short sample request to the provider, so provider " +
                            "usage or billing may apply. Nothing is sent until you press this button. " +
                            "A dropped connection is retried once automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                NoticeCard(
                    title = "Where your keys are stored",
                    message = "API keys and tokens live in this app's private settings on your device. " +
                        "They are included in backups you create, so keep those files somewhere safe.",
                    tone = NoticeTone.Info,
                    icon = Icons.Default.Lock
                )
            }
        }
    }

    connectionResult?.let { result ->
        AppDialog(
            title = result.title,
            message = result.message,
            onDismiss = { connectionResult = null },
            confirmLabel = "Done",
            onConfirm = { connectionResult = null },
            icon = if (result.succeeded) Icons.Default.CheckCircle else Icons.Default.Error,
            iconTint = if (result.succeeded) MaterialTheme.extra.success else MaterialTheme.colorScheme.error
        ) {
            if (result.latencyMs != null) {
                CardFact(
                    label = "Round trip",
                    value = if (result.latencyMs < 1000) {
                        "${result.latencyMs} ms"
                    } else {
                        String.format(Locale.US, "%.1f s", result.latencyMs / 1000.0)
                    }
                )
            }
            if (result.suggestsLongerTimeout) {
                SecondaryButton(
                    text = "Open network timeout",
                    onClick = {
                        connectionResult = null
                        onNavigateBehaviour()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** A saved provider profile: tap to load it into the fields, or delete it. */
@Composable
private fun SavedProfileRow(
    title: String,
    subtitle: String,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    SettingRow(
        title = title,
        description = subtitle,
        icon = Icons.Default.Key,
        onClick = onLoad
    ) {
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete profile",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A key field: masked by default, with an explicit show/hide control. */
@Composable
private fun SecureKeyField(
    value: String,
    label: String,
    visible: Boolean,
    onValueChange: (String) -> Unit,
    onToggleVisibility: () -> Unit
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        monospace = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Hide key" else "Show key",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

/**
 * Whether the device currently has a usable connection. No permission is needed for the capability
 * check, and a wrong answer here only means the test runs and reports the real error.
 */
private fun hasInternet(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return true
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
