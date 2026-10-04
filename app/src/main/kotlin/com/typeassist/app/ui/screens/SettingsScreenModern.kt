package com.typeassist.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.typeassist.app.BuildConfig
import com.typeassist.app.api.ApiErrorFormatter
import com.typeassist.app.api.CloudflareApiClient
import com.typeassist.app.api.CustomApiClient
import com.typeassist.app.api.ModelCatalogClient
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.CloudflareConfig
import com.typeassist.app.data.CustomApiConfig
import com.typeassist.app.data.LoadingIndicatorStyle
import com.typeassist.app.data.ModelSelectionPreferences
import com.typeassist.app.data.SavedGeminiConfig
import com.typeassist.app.data.repository.UpdateRepository
import com.typeassist.app.ui.components.PageHeading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import kotlin.coroutines.coroutineContext
import kotlin.math.cos
import kotlin.math.sin

private data class ConnectionTestResult(
    val succeeded: Boolean,
    val title: String,
    val message: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: AppConfig,
    client: OkHttpClient,
    onSave: (AppConfig) -> Unit,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    initialTab: Int = 0
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            PageHeading(
                eyebrow = "PERSONALIZE",
                title = "Make it yours",
                description = "Providers, shortcuts, and behaviour—all in one place."
            )
            Spacer(Modifier.height(18.dp))
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                listOf("General", "AI Provider", "Local LLM").forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label, maxLines = 1) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                when (selectedTab) {
                    0 -> GeneralSettingsTabModern(config, onSave, onNavigate)
                    1 -> AiProviderSettingsTabModern(config, client, onSave)
                    else -> {
                        LocalLlmSetup(config, onSave)
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = {
                                onSave(config.copy(provider = "local"))
                                Toast.makeText(
                                    context,
                                    "Local LLM saved as the active provider.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        ) {
                            Text("Set Local as active provider")
                        }
                        Spacer(Modifier.height(20.dp))
                        LocalLlmHelp(MaterialTheme.colorScheme.primary, context)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralSettingsTabModern(
    config: AppConfig,
    onSave: (AppConfig) -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enableUndoOverlay by remember(config.enableUndoOverlay) { mutableStateOf(config.enableUndoOverlay) }
    var enableLoadingOverlay by remember(config.enableLoadingOverlay) { mutableStateOf(config.enableLoadingOverlay) }
    var loadingStyle by remember(config.loadingIndicatorStyle) { mutableStateOf(config.loadingIndicatorStyle.ifBlank { "classic" }) }
    var indicatorColor by remember(config.loadingIndicatorColor) {
        mutableIntStateOf(LoadingIndicatorStyle.sanitizeColor(config.loadingIndicatorColor))
    }
    var indicatorSize by remember(config.loadingIndicatorSizePercent) {
        mutableIntStateOf(LoadingIndicatorStyle.sanitizeSizePercent(config.loadingIndicatorSizePercent))
    }
    var allowTriggerAnywhere by remember(config.allowTriggerAnywhere) { mutableStateOf(config.allowTriggerAnywhere) }
    var ignorePrecedingWhitespace by remember(config.ignorePrecedingWhitespace) { mutableStateOf(config.ignorePrecedingWhitespace) }
    var globalTriggerPattern by remember(config.globalTriggerPattern) { mutableStateOf(config.globalTriggerPattern) }
    var historyEnabled by remember(config.isHistoryEnabled) { mutableStateOf(config.isHistoryEnabled) }
    var previewEnabled by remember(config.enablePreviewDialog) { mutableStateOf(config.enablePreviewDialog) }
    var timeout by remember(config.apiTimeoutSeconds) { mutableStateOf(config.apiTimeoutSeconds.toFloat()) }
    var checkingForUpdate by remember { mutableStateOf(false) }

    val loadingStyles = remember {
        listOf(
            LoadingStyleOption("classic", "Classic", "Simple spinner on dark bubble"),
            LoadingStyleOption("dots", "Bouncing dots", "Three dots that bounce and fade"),
            LoadingStyleOption("pulse", "Pulse", "Pulsing glow effect"),
            LoadingStyleOption("bars", "Bars", "Equalizer bars animation"),
            LoadingStyleOption("typing", "Typing", "iMessage-style typing bubble"),
            LoadingStyleOption("pill", "Pill", "Rounded pill with text"),
            LoadingStyleOption("neon", "Neon ring", "Glowing ring loader")
        )
    }

    ModernSettingsSection(
        title = "Floating controls",
        description = "Choose which helpful indicators appear while Prompt AI works."
    ) {
        ModernSwitchRow(
            title = "Undo button",
            description = "Show a quick undo action after replacing text.",
            checked = enableUndoOverlay
        ) {
            enableUndoOverlay = it
            onSave(config.copy(enableUndoOverlay = it))
        }
        HorizontalDivider()
        ModernSwitchRow(
            title = "Loading indicator",
            description = "Show a small progress overlay while AI is responding.",
            checked = enableLoadingOverlay
        ) {
            enableLoadingOverlay = it
            onSave(config.copy(enableLoadingOverlay = it))
        }

        if (enableLoadingOverlay) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Indicator style",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = loadingStyles.firstOrNull { it.id == loadingStyle }?.name ?: loadingStyle,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    label = { Text("Style") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    loadingStyles.forEach { style ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(style.name, fontWeight = FontWeight.SemiBold)
                                    Text(style.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = {
                                loadingStyle = style.id
                                expanded = false
                                onSave(config.copy(loadingIndicatorStyle = style.id))
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // Live preview
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Preview",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    val previewScale = indicatorSize / 100f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((90f * previewScale).coerceIn(90f, 200f).dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF121212)),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingStylePreview(
                            styleId = loadingStyle,
                            color = Color(indicatorColor),
                            scale = previewScale
                        )
                    }
                }
            }
        }
    }

    if (enableLoadingOverlay) {
        IndicatorColorSection(
            color = indicatorColor,
            onColorChange = { picked ->
                val safe = LoadingIndicatorStyle.ensureOpaque(picked)
                indicatorColor = safe
                onSave(config.copy(loadingIndicatorColor = safe))
            }
        )

        ModernSettingsSection(
            title = "Indicator size",
            description = "Make the floating indicator easier or harder to notice."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Small",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "$indicatorSize%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Large",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = indicatorSize.toFloat(),
                onValueChange = { indicatorSize = it.toInt() },
                onValueChangeFinished = {
                    onSave(config.copy(loadingIndicatorSizePercent = indicatorSize))
                },
                valueRange = LoadingIndicatorStyle.MIN_SIZE_PERCENT.toFloat()..LoadingIndicatorStyle.MAX_SIZE_PERCENT.toFloat(),
                steps = 14
            )
            Text(
                "100% is the original size. Applies to every style.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    ModernSettingsSection(
        title = "Triggers & text",
        description = "Adjust how shortcuts are detected in text fields."
    ) {
        ModernSwitchRow(
            title = "Allow triggers anywhere",
            description = "Process a trigger even when more text follows it.",
            checked = allowTriggerAnywhere
        ) {
            allowTriggerAnywhere = it
            onSave(config.copy(allowTriggerAnywhere = it))
        }
        HorizontalDivider()
        ModernSwitchRow(
            title = "Ignore the preceding space",
            description = "Allow commands directly after a word, like “hello.ta”.",
            checked = ignorePrecedingWhitespace
        ) {
            ignorePrecedingWhitespace = it
            onSave(config.copy(ignorePrecedingWhitespace = it))
        }
        OutlinedTextField(
            value = globalTriggerPattern,
            onValueChange = {
                globalTriggerPattern = it
                onSave(config.copy(globalTriggerPattern = it))
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            label = { Text("Global rewrite pattern") },
            supportingText = { Text("Use % where the rewrite instruction should go. Example: ...%...") },
            singleLine = true
        )
    }

    ModernSettingsSection(
        title = "History & preview",
        description = "Control what Prompt AI keeps and how longer answers are shown."
    ) {
        ModernSwitchRow(
            title = "Save processed text to history",
            description = "Keep recent results available for recovery.",
            checked = historyEnabled
        ) {
            historyEnabled = it
            onSave(config.copy(isHistoryEnabled = it))
        }
        HorizontalDivider()
        ModernSwitchRow(
            title = "Preview longer responses",
            description = "Review longer AI responses before they are inserted.",
            checked = previewEnabled
        ) {
            previewEnabled = it
            onSave(config.copy(enablePreviewDialog = it))
        }
    }

    ModernSettingsSection(
        title = "Network timeout",
        description = "Set the maximum wait for a provider response."
    ) {
        Text("${timeout.toInt()} seconds", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Slider(
            value = timeout,
            onValueChange = { timeout = it },
            onValueChangeFinished = { onSave(config.copy(apiTimeoutSeconds = timeout.toLong())) },
            valueRange = 10f..120f,
            steps = 21
        )
    }

    ModernSettingsSection(
        title = "Troubleshooting",
        description = "Check the Android permissions Prompt AI needs to run reliably."
    ) {
        ModernActionRow(
            title = "Check permissions",
            description = "Accessibility, notifications, and battery settings.",
            actionLabel = "Open"
        ) { onNavigate("permissions") }
    }

    if (BuildConfig.SHOW_UPDATES) {
        ModernSettingsSection(
            title = "App updates",
            description = "Check the official release channel for a newer version."
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Current version", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    enabled = !checkingForUpdate,
                    onClick = {
                        checkingForUpdate = true
                        scope.launch {
                            val result = UpdateRepository(context).checkForUpdate("estiaksoyeb", "TypeAssist")
                            checkingForUpdate = false
                            result.onSuccess { release ->
                                if (release != null) {
                                    Toast.makeText(context, "New release: ${release.tagName}", Toast.LENGTH_LONG).show()
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
                                } else {
                                    Toast.makeText(context, "You are on the latest version.", Toast.LENGTH_SHORT).show()
                                }
                            }.onFailure {
                                Toast.makeText(context, "Could not check updates: ${ApiErrorFormatter.explain(it)}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    if (checkingForUpdate) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Check")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiProviderSettingsTabModern(config: AppConfig, client: OkHttpClient, onSave: (AppConfig) -> Unit) {
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
    val geminiRecents = geminiHistory.filter { it.lastSelectedAt > 0L }.sortedByDescending { it.lastSelectedAt }.map { it.modelId }.distinct()
    val customFavorites = customHistory.filter { it.isFavorite }.map { it.modelId }.distinct()
    val customRecents = customHistory.filter { it.lastSelectedAt > 0L }.sortedByDescending { it.lastSelectedAt }.map { it.modelId }.distinct()

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
            customModelsMessage = "Enter the provider Base URL first."
            customModelsError = true
            return
        }
        customModelLoadJob?.cancel()
        customModelsLoading = true
        customModelsMessage = null
        customModelLoadJob = scope.launch {
            try {
                customModels = modelCatalog.fetchOpenAiCompatibleModels(customBaseUrl.trim(), customApiKey.trim())
                customModelsMessage = "Loaded ${customModels.size} model IDs. Select one below or keep typing manually."
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

    fun finishTest(succeeded: Boolean, title: String, message: String) {
        isTesting = false
        connectionResult = ConnectionTestResult(succeeded, title, message)
    }

    fun runGeminiTest(apiKey: String, model: String) {
        if (apiKey.isBlank()) {
            finishTest(false, "Gemini setup needs attention", "Enter your Gemini API key, then try the connection test again.")
            return
        }
        isTesting = true
        scope.launch {
            try {
                val available = modelCatalog.fetchGeminiModels(apiKey.trim())
                geminiModels = available
                geminiModelsMessage = "Loaded ${available.size} models that support text generation."
                geminiModelsError = false
                if (model.isBlank()) {
                    finishTest(true, "Gemini connection successful", "The API key works and ${available.size} text-generation models are available.")
                } else if (available.any { it.equals(model.trim(), ignoreCase = true) }) {
                    finishTest(true, "Gemini connection successful", "The API key works and “${model.trim()}” is available for text generation.")
                } else {
                    finishTest(false, "Gemini connected, model not found", "The key is valid, but “${model.trim()}” was not in the available text-generation models. Refresh the list and choose an available model, or enter a model ID supported by your account.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                finishTest(false, "Gemini connection failed", ApiErrorFormatter.explain(e))
            }
        }
    }

    fun runCustomTest(baseUrl: String, apiKey: String, model: String) {
        if (baseUrl.isBlank() || model.isBlank()) {
            finishTest(false, "Custom API setup needs attention", "Enter both a Base URL and model ID before testing this provider.")
            return
        }
        isTesting = true
        try {
            CustomApiClient(client).callCustomApi(
                baseUrl.trim(),
                apiKey.trim(),
                model.trim(),
                "You are a connection-test assistant. Reply with exactly: Connected.",
                "Reply with exactly: Connected.",
                config.apiTimeoutSeconds
            ) { result ->
                scope.launch {
                    result.fold(
                        onSuccess = { finishTest(true, "Custom API connection successful", "The endpoint accepted the request and returned a model response.") },
                        onFailure = { finishTest(false, "Custom API connection failed", ApiErrorFormatter.explain(it)) }
                    )
                }
            }
        } catch (e: Exception) {
            finishTest(false, "Custom API connection failed", ApiErrorFormatter.explain(e))
        }
    }

    fun runCloudflareTest(accountId: String, token: String, model: String) {
        if (accountId.isBlank() || token.isBlank() || model.isBlank()) {
            finishTest(false, "Cloudflare setup needs attention", "Enter the Account ID, API token, and model ID before testing.")
            return
        }
        isTesting = true
        try {
            CloudflareApiClient(client).callCloudflare(
                accountId.trim(), token.trim(), model.trim(),
                "You are a connection-test assistant. Reply with exactly: Connected.",
                "Reply with exactly: Connected.",
                config.apiTimeoutSeconds
            ) { result ->
                scope.launch {
                    result.fold(
                        onSuccess = { finishTest(true, "Cloudflare connection successful", "The account accepted the request and returned a model response.") },
                        onFailure = { finishTest(false, "Cloudflare connection failed", ApiErrorFormatter.explain(it)) }
                    )
                }
            }
        } catch (e: Exception) {
            finishTest(false, "Cloudflare connection failed", ApiErrorFormatter.explain(e))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ModernSettingsSection(
            title = "Choose a provider",
            description = "Select the service Prompt AI will use for AI commands."
        ) {
            var providerMenuExpanded by remember { mutableStateOf(false) }
            val providerNames = listOf("gemini" to "Google Gemini", "cloudflare" to "Cloudflare Workers AI", "custom" to "OpenAI-compatible API", "local" to "Local LLM")
            ExposedDropdownMenuBox(
                expanded = providerMenuExpanded,
                onExpandedChange = { providerMenuExpanded = !providerMenuExpanded }
            ) {
                OutlinedTextField(
                    value = providerNames.firstOrNull { it.first == selectedProvider }?.second ?: selectedProvider,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    label = { Text("Active provider") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerMenuExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = providerMenuExpanded,
                    onDismissRequest = { providerMenuExpanded = false }
                ) {
                    providerNames.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                selectedProvider = id
                                providerMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (selectedProvider) {
                    "gemini" -> "Live model discovery is available for your Gemini key."
                    "custom" -> "Works with OpenAI and providers that expose the OpenAI-compatible models endpoint."
                    "cloudflare" -> "Configure the Cloudflare account and model ID below."
                    else -> "Select a GGUF model in the Local LLM tab."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (selectedProvider == "gemini") {
            if (config.savedGeminiConfigs.isNotEmpty()) {
                ModernSettingsSection(title = "Saved Gemini profiles") {
                    config.savedGeminiConfigs.forEachIndexed { index, saved ->
                        SavedProviderRow(
                            title = saved.model.ifBlank { "Gemini profile ${index + 1}" },
                            subtitle = "Key ending ${saved.apiKey.takeLast(4).ifBlank { "••••" }}",
                            onClick = {
                                geminiKey = saved.apiKey
                                geminiModel = saved.model
                                geminiModelLoadJob?.cancel()
                                geminiModels = emptyList()
                                geminiModelsMessage = null
                                geminiModelsLoading = false
                            },
                            onDelete = {
                                val updated = config.savedGeminiConfigs.toMutableList().apply { remove(saved) }
                                onSave(config.copy(savedGeminiConfigs = updated))
                            }
                        )
                        if (index != config.savedGeminiConfigs.lastIndex) HorizontalDivider()
                    }
                }
            }
            ModernSettingsSection(
                title = "Google Gemini",
                description = "Add your key, then load the models that are available to it."
            ) {
                SecureApiKeyField(
                    value = geminiKey,
                    label = "Gemini API key",
                    isVisible = keyVisible,
                    onValueChange = {
                        geminiKey = it
                        geminiModelLoadJob?.cancel()
                        geminiModels = emptyList()
                        geminiModelsMessage = null
                        geminiModelsLoading = false
                    },
                    onToggleVisibility = { keyVisible = !keyVisible }
                )
                Spacer(Modifier.height(14.dp))
                ModelPickerCard(
                    title = "Model",
                    description = "The list is fetched from Google and filters to models supporting text generation. Manual entry remains available.",
                    value = geminiModel,
                    onValueChange = { geminiModel = it },
                    availableModels = geminiModels,
                    favoriteModels = geminiFavorites,
                    recentModels = geminiRecents,
                    isLoading = geminiModelsLoading,
                    statusMessage = geminiModelsMessage,
                    statusIsError = geminiModelsError,
                    onLoadModels = ::loadGeminiModels,
                    onSelectModel = {
                        geminiModel = it
                        recordModelSelection("gemini", "gemini", it)
                    },
                    onToggleFavorite = { toggleModelFavorite("gemini", "gemini", it) }
                )
            }
        } else if (selectedProvider == "custom") {
            if (config.savedCustomConfigs.isNotEmpty()) {
                ModernSettingsSection(title = "Saved API profiles") {
                    config.savedCustomConfigs.forEachIndexed { index, saved ->
                        SavedProviderRow(
                            title = saved.model.ifBlank { "Custom profile ${index + 1}" },
                            subtitle = saved.baseUrl,
                            onClick = {
                                customBaseUrl = saved.baseUrl
                                customApiKey = saved.apiKey
                                customModel = saved.model
                                customModelLoadJob?.cancel()
                                customModels = emptyList()
                                customModelsMessage = null
                                customModelsLoading = false
                            },
                            onDelete = {
                                val updated = config.savedCustomConfigs.toMutableList().apply { remove(saved) }
                                onSave(config.copy(savedCustomConfigs = updated))
                            }
                        )
                        if (index != config.savedCustomConfigs.lastIndex) HorizontalDivider()
                    }
                }
            }
            ModernSettingsSection(
                title = "OpenAI-compatible API",
                description = "Use OpenAI or another service with a compatible chat-completions API."
            ) {
                OutlinedTextField(
                    value = customBaseUrl,
                    onValueChange = {
                        customBaseUrl = it
                        customModelLoadJob?.cancel()
                        customModels = emptyList()
                        customModelsMessage = null
                        customModelsLoading = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                Spacer(Modifier.height(12.dp))
                SecureApiKeyField(
                    value = customApiKey,
                    label = "API key (optional for some endpoints)",
                    isVisible = keyVisible,
                    onValueChange = {
                        customApiKey = it
                        customModelLoadJob?.cancel()
                        customModels = emptyList()
                        customModelsMessage = null
                        customModelsLoading = false
                    },
                    onToggleVisibility = { keyVisible = !keyVisible }
                )
                Spacer(Modifier.height(14.dp))
                ModelPickerCard(
                    title = "Choose a model",
                    description = "Load model IDs from the standard /models endpoint, or keep typing a model name manually.",
                    value = customModel,
                    onValueChange = { customModel = it },
                    availableModels = customModels,
                    favoriteModels = customFavorites,
                    recentModels = customRecents,
                    isLoading = customModelsLoading,
                    statusMessage = customModelsMessage,
                    statusIsError = customModelsError,
                    onLoadModels = ::loadCustomModels,
                    onSelectModel = {
                        customModel = it
                        recordModelSelection("custom", customBaseUrl, it)
                    },
                    onToggleFavorite = { toggleModelFavorite("custom", customBaseUrl, it) }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Some compatible providers do not publish /models. If loading fails, you can still enter the model ID directly.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (selectedProvider == "cloudflare") {
            if (config.savedCloudflareConfigs.isNotEmpty()) {
                ModernSettingsSection(title = "Saved Cloudflare profiles") {
                    config.savedCloudflareConfigs.forEachIndexed { index, saved ->
                        SavedProviderRow(
                            title = saved.model.ifBlank { "Cloudflare profile ${index + 1}" },
                            subtitle = "Account ${saved.accountId.take(6)}…",
                            onClick = {
                                cfAccountId = saved.accountId
                                cfApiToken = saved.apiToken
                                cfModel = saved.model
                            },
                            onDelete = {
                                val updated = config.savedCloudflareConfigs.toMutableList().apply { remove(saved) }
                                onSave(config.copy(savedCloudflareConfigs = updated))
                            }
                        )
                        if (index != config.savedCloudflareConfigs.lastIndex) HorizontalDivider()
                    }
                }
            }
            ModernSettingsSection(title = "Cloudflare Workers AI", description = "Use your Cloudflare account and Workers AI token.") {
                OutlinedTextField(cfAccountId, { cfAccountId = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Account ID") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                SecureApiKeyField(cfApiToken, "API token", keyVisible, { cfApiToken = it }, { keyVisible = !keyVisible })
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(cfModel, { cfModel = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Model ID") }, placeholder = { Text("@cf/meta/llama-3-8b-instruct") }, singleLine = true)
            }
        } else {
            if (config.savedLocalModels.isNotEmpty()) {
                ModernSettingsSection(title = "Previously selected local models") {
                    config.savedLocalModels.forEach { savedPath ->
                        SavedProviderRow(
                            title = savedPath.substringAfterLast('/').ifBlank { "Local GGUF model" },
                            subtitle = "Tap to activate this model",
                            onClick = {
                                onSave(config.copy(localLlmConfig = config.localLlmConfig.copy(modelPath = savedPath)))
                                Toast.makeText(context, "Local model selected.", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = {
                                onSave(config.copy(savedLocalModels = config.savedLocalModels.toMutableList().apply { remove(savedPath) }))
                            }
                        )
                    }
                }
            }
            ModernSettingsSection(title = "Local model", description = "Choose a GGUF model from the Local LLM tab.") {
                Text("No cloud API key is used when the local provider is active.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Button(
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

                val savedCustom = config.savedCustomConfigs.toMutableList()
                if (selectedProvider == "custom" && customBaseUrl.isNotBlank() && customModel.isNotBlank() && newCustomConfig !in savedCustom) {
                    savedCustom.add(newCustomConfig)
                }
                val savedGemini = config.savedGeminiConfigs.toMutableList()
                if (selectedProvider == "gemini" && geminiKey.isNotBlank() && newGeminiConfig !in savedGemini) {
                    savedGemini.add(newGeminiConfig)
                }
                val savedCloudflare = config.savedCloudflareConfigs.toMutableList()
                if (selectedProvider == "cloudflare" && cfApiToken.isNotBlank() && newCloudflareConfig !in savedCloudflare) {
                    savedCloudflare.add(newCloudflareConfig)
                }
                val savedLocal = config.savedLocalModels.toMutableList()
                if (selectedProvider == "local" && config.localLlmConfig.modelPath.isNotBlank() && config.localLlmConfig.modelPath !in savedLocal) {
                    savedLocal.add(config.localLlmConfig.modelPath)
                }

                var updatedPreferences = config.modelPreferences ?: mutableListOf()
                when (selectedProvider) {
                    "gemini" -> updatedPreferences = ModelSelectionPreferences.recordSelection(updatedPreferences, "gemini", "gemini", geminiModel)
                    "custom" -> updatedPreferences = ModelSelectionPreferences.recordSelection(updatedPreferences, "custom", customBaseUrl, customModel)
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
                if (!config.isAppEnabled && configured) updatedConfig = updatedConfig.copy(isAppEnabled = true)
                onSave(updatedConfig)
                Toast.makeText(context, "Provider settings saved.", Toast.LENGTH_SHORT).show()

                when (selectedProvider) {
                    "gemini" -> runGeminiTest(geminiKey.trim(), geminiModel.trim())
                    "cloudflare" -> runCloudflareTest(cfAccountId.trim(), cfApiToken.trim(), cfModel.trim())
                    "custom" -> runCustomTest(customBaseUrl.trim(), customApiKey.trim(), customModel.trim())
                    else -> connectionResult = ConnectionTestResult(true, "Local provider saved", "Your local model selection has been saved.")
                }
            },
            enabled = !isTesting,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            if (isTesting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp))
                Text("Testing connection…")
            } else {
                Text(if (selectedProvider == "local") "Save provider" else "Test connection & save")
            }
        }
        Text(
            "The test sends a short sample request to the selected provider. Provider usage or billing may apply.",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
    }

    connectionResult?.let { result ->
        AlertDialog(
            onDismissRequest = { connectionResult = null },
            icon = {
                Icon(
                    if (result.succeeded) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (result.succeeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            },
            title = { Text(result.title) },
            text = { Text(result.message) },
            confirmButton = {
                Button(onClick = { connectionResult = null }) { Text("Done") }
            },
            shape = MaterialTheme.shapes.large
        )
    }
}

@Composable
private fun ModernSettingsSection(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun ModernSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ModernActionRow(
    title: String,
    description: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(actionLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecureApiKeyField(
    value: String,
    label: String,
    isVisible: Boolean,
    onValueChange: (String) -> Unit,
    onToggleVisibility: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (isVisible) "Hide API key" else "Show API key"
                )
            }
        }
    )
}

@Composable
private fun SavedProviderRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete saved profile", tint = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Settings section for picking the colour of the floating loading indicator: quick presets,
 * a hex field for any colour at all, and an HSV mixer for fine tuning.
 */
@Composable
private fun IndicatorColorSection(
    color: Int,
    onColorChange: (Int) -> Unit
) {
    var mixerVisible by remember { mutableStateOf(false) }
    var hexDraft by remember(color) { mutableStateOf(LoadingIndicatorStyle.toHexRgb(color)) }

    ModernSettingsSection(
        title = "Indicator colour",
        description = "Choose any colour for the floating loading indicator."
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
            Column {
                Text("Current colour", fontWeight = FontWeight.SemiBold)
                Text(
                    hexDraft.uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Quick picks",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LoadingIndicatorStyle.PRESET_COLORS.forEach { preset ->
                val selected = preset == color
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(preset))
                        .then(
                            if (selected) {
                                Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            }
                        )
                        .clickable { onColorChange(preset) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected colour",
                            tint = Color(LoadingIndicatorStyle.contrastColor(preset)),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        val hexIsValid = LoadingIndicatorStyle.parseHexColor(hexDraft) != null
        OutlinedTextField(
            value = hexDraft,
            onValueChange = { typed ->
                hexDraft = typed
                LoadingIndicatorStyle.parseHexColor(typed)?.let { onColorChange(it) }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            label = { Text("Any colour (hex)") },
            supportingText = {
                Text(
                    if (hexIsValid) "Applied to the indicator"
                    else "Format: #RRGGBB, for example #22D3EE"
                )
            },
            isError = hexDraft.isNotBlank() && !hexIsValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { mixerVisible = !mixerVisible }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("Mix a custom colour", fontWeight = FontWeight.SemiBold)
                Text(
                    "Slide hue, saturation and brightness.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (mixerVisible) "Hide" else "Show",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (mixerVisible) {
            val hsv = remember(color) {
                FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
            }
            var hue by remember(hsv) { mutableFloatStateOf(hsv[0]) }
            var saturation by remember(hsv) { mutableFloatStateOf(hsv[1]) }
            var brightness by remember(hsv) { mutableFloatStateOf(hsv[2]) }

            fun applyMixer() {
                onColorChange(
                    android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                                Color.Blue, Color.Magenta, Color.Red
                            )
                        )
                    )
            )
            Text(
                "Hue ${hue.toInt()}°",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = hue,
                onValueChange = { hue = it; applyMixer() },
                valueRange = 0f..360f
            )
            Text(
                "Saturation ${(saturation * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = saturation,
                onValueChange = { saturation = it; applyMixer() },
                valueRange = 0f..1f
            )
            Text(
                "Brightness ${(brightness * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = brightness,
                onValueChange = { brightness = it; applyMixer() },
                valueRange = 0f..1f
            )
        }
    }
}

private data class LoadingStyleOption(
    val id: String,
    val name: String,
    val description: String
)

@Composable
private fun LoadingStylePreview(
    styleId: String,
    color: Color = Color.White,
    scale: Float = 1f
) {
    val primary = color
    val white = color
    Box(
        modifier = Modifier.scale(scale),
        contentAlignment = Alignment.Center
    ) {
        when (styleId) {
            "classic" -> {
                Box(
                    modifier = Modifier.padding(14.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = white,
                        strokeWidth = 2.5.dp
                    )
                }
            }
            "dots" -> {
                val infinite = rememberInfiniteTransition(label = "dots")
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { i ->
                        val scaleAnim by infinite.animateFloat(
                            initialValue = 0.6f,
                            targetValue = 1.2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, delayMillis = i * 180, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dotScale$i"
                        )
                        val alpha by infinite.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, delayMillis = i * 180),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dotAlpha$i"
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .scale(scaleAnim)
                                .alpha(alpha)
                                .clip(CircleShape)
                                .background(white)
                        )
                    }
                }
            }
            "pulse" -> {
                val infinite = rememberInfiniteTransition(label = "pulse")
                val scaleAnim by infinite.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.35f,
                    animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "pulseScale"
                )
                val outerScale by infinite.animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.6f,
                    animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
                    label = "outerScale"
                )
                val outerAlpha by infinite.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 0.15f,
                    animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
                    label = "outerAlpha"
                )
                Box(
                    modifier = Modifier.padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .scale(outerScale)
                            .alpha(outerAlpha)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = 0.2f))
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .scale(scaleAnim)
                            .clip(CircleShape)
                            .background(white)
                    )
                }
            }
            "bars" -> {
                val infinite = rememberInfiniteTransition(label = "bars")
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    repeat(3) { i ->
                        val scaleAnim by infinite.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.7f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(500, delayMillis = i * 130, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "bar$i"
                        )
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(16.dp)
                                .scale(scaleX = 1f, scaleY = scaleAnim)
                                .clip(RoundedCornerShape(3.dp))
                                .background(white)
                        )
                    }
                }
            }
            "typing" -> {
                val infinite = rememberInfiniteTransition(label = "typing")
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { i ->
                        val offset by infinite.animateFloat(
                            initialValue = 0f,
                            targetValue = -8f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(380, delayMillis = i * 110, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "typingOffset$i"
                        )
                        Box(
                            modifier = Modifier
                                .offset(y = offset.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(white)
                        )
                    }
                }
            }
            "pill" -> {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = white,
                        strokeWidth = 2.dp
                    )
                    Text("Thinking...", color = white, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
            "neon" -> {
                val infinite = rememberInfiniteTransition(label = "neon")
                val spin by infinite.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
                    label = "neonSpin"
                )
                val glow by infinite.animateFloat(
                    initialValue = 0.55f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "neonGlow"
                )
                val corePulse by infinite.animateFloat(
                    initialValue = 0.75f,
                    targetValue = 1.25f,
                    animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "neonCore"
                )
                val coreColor = Color(LoadingIndicatorStyle.contrastColor(color.toArgb()))
                val sweepAngle = 300f
                Canvas(modifier = Modifier.size(34.dp)) {
                    val stroke = size.minDimension / 12f
                    val inset = stroke * 3f
                    val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                    val topLeft = Offset(inset, inset)

                    rotate(degrees = spin) {
                        // Soft halo bleeding out from the lit part of the tube.
                        drawArc(
                            color = color.copy(alpha = 0.16f * glow),
                            startAngle = 0f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke * 3f, cap = StrokeCap.Round)
                        )
                        // Dim track the light travels along.
                        drawArc(
                            color = color.copy(alpha = 0.17f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke)
                        )
                        // The lit tube, with a tail that fades out.
                        drawArc(
                            brush = Brush.sweepGradient(
                                0f to color.copy(alpha = 0f),
                                0.3f to color.copy(alpha = 0.35f),
                                0.85f to color,
                                1f to color,
                                center = center
                            ),
                            startAngle = 0f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        // Bright leading head.
                        val radians = Math.toRadians(sweepAngle.toDouble())
                        val radius = arcSize.width / 2f
                        drawCircle(
                            color = coreColor,
                            radius = stroke * 0.8f,
                            center = Offset(
                                center.x + radius * cos(radians).toFloat(),
                                center.y + radius * sin(radians).toFloat()
                            )
                        )
                    }
                    drawCircle(color = coreColor, radius = stroke * 1.1f * corePulse, center = center)
                }
            }
        }
    }
}
