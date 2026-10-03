package com.typeassist.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.CloudflareConfig
import com.typeassist.app.data.CustomApiConfig
import com.typeassist.app.data.isReasoningModel
import com.typeassist.app.api.CloudflareApiClient
import com.typeassist.app.api.CustomApiClient
import com.typeassist.app.api.ModelFetcher
import com.typeassist.app.ui.components.ModelSelector
import com.typeassist.app.BuildConfig
import kotlinx.coroutines.launch
import okhttp3.*
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(config: AppConfig, client: OkHttpClient, onSave: (AppConfig) -> Unit, onBack: () -> Unit, onNavigate: (String) -> Unit, initialTab: Int = 0) {
    var selectedTab by remember { mutableStateOf(initialTab) }
    val context = LocalContext.current
    
    val view = LocalView.current
    val primaryColor = MaterialTheme.colorScheme.primary

    Scaffold(topBar = { 
        TopAppBar(
            title = { Text("Settings") }, 
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, 
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.primary,
                navigationIconContentColor = MaterialTheme.colorScheme.primary
            ) 
        ) 
    }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("General") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("AI Provider") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Local LLM") })
            }
            
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                when (selectedTab) {
                    0 -> GeneralSettingsTab(config, onSave, onNavigate)
                    1 -> AiProviderSettingsTab(config, client, onSave)
                    2 -> {
                        LocalLlmSetup(config, onSave)
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = {
                                val updatedConfig = config.copy(provider = "local")
                                onSave(updatedConfig)
                                Toast.makeText(context, "Local LLM saved and set as active provider ✅", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text("Set Local as Active Provider")
                        }
                        Spacer(Modifier.height(24.dp))
                        LocalLlmHelp(MaterialTheme.colorScheme.primary, context)
                    }
                }
            }
        }
    }
}

@Composable
fun GeneralSettingsTab(config: AppConfig, onSave: (AppConfig) -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    var enableUndoOverlay by remember { mutableStateOf(config.enableUndoOverlay) }
    var enableLoadingOverlay by remember { mutableStateOf(config.enableLoadingOverlay) }
    
    Text("Overlay Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(16.dp))
    
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Show Undo Button", fontWeight = FontWeight.Bold)
            Text("Show an 'UNDO' button overlay after text replacement.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enableUndoOverlay, onCheckedChange = { 
            enableUndoOverlay = it
            onSave(config.copy(enableUndoOverlay = it))
        })
    }
    
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
    
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Show Loading Indicator", fontWeight = FontWeight.Bold)
            Text("Show a spinner overlay while AI is processing.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enableLoadingOverlay, onCheckedChange = { 
            enableLoadingOverlay = it
            onSave(config.copy(enableLoadingOverlay = it))
        })
    }

    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
    
    // Permission & Troubleshooting
    Text("Troubleshooting", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(16.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigate("permissions") },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Check Permissions", fontWeight = FontWeight.Bold)
            Text("Fix issues with background service, battery optimization, and notifications.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.rotate(180f)) // Forward arrow hack or use appropriate icon
    }

    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))

    Text("Triggers & History", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(16.dp))

    var allowTriggerAnywhere by remember { mutableStateOf(config.allowTriggerAnywhere) }
    var ignorePrecedingWhitespace by remember { mutableStateOf(config.ignorePrecedingWhitespace) }
    var globalTriggerPattern by remember { mutableStateOf(config.globalTriggerPattern) }
    
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Allow Trigger Anywhere", fontWeight = FontWeight.Bold)
            Text("Process commands even if followed by other text (e.g. 'text .g more').", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = allowTriggerAnywhere, onCheckedChange = { 
            allowTriggerAnywhere = it
            onSave(config.copy(allowTriggerAnywhere = it))
        })
    }

    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Ignore Preceding Space", fontWeight = FontWeight.Bold)
            Text("Allow triggers immediately after words/punctuation (e.g. 'word.ta' vs 'word .ta').", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = ignorePrecedingWhitespace, onCheckedChange = { 
            ignorePrecedingWhitespace = it
            onSave(config.copy(ignorePrecedingWhitespace = it))
        })
    }

    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
    
    Text("Global Trigger Pattern", fontWeight = FontWeight.Bold)
    Text("Pattern for rewriting text. Use '%' as the placeholder for instruction.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = globalTriggerPattern, 
        onValueChange = { 
            globalTriggerPattern = it
            onSave(config.copy(globalTriggerPattern = it))
        },
        label = { Text("Pattern (e.g. ...%...)") },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))

    var isHistoryEnabled by remember { mutableStateOf(config.isHistoryEnabled) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Enable History", fontWeight = FontWeight.Bold)
            Text("Save processed text to history for later access.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = isHistoryEnabled, onCheckedChange = { 
            isHistoryEnabled = it
            onSave(config.copy(isHistoryEnabled = it))
        })
    }

    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))

    var enablePreviewDialog by remember { mutableStateOf(config.enablePreviewDialog) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Enable Preview Dialog", fontWeight = FontWeight.Bold)
            Text("Show a scrollable preview for long AI responses (>15 words).", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = enablePreviewDialog, 
            onCheckedChange = { 
                enablePreviewDialog = it
                onSave(config.copy(enablePreviewDialog = it))
            }
        )
    }

    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))

    var apiTimeoutSeconds by remember { mutableStateOf(config.apiTimeoutSeconds.toFloat()) }

    Text("API Timeout", fontWeight = FontWeight.Bold)
    Text("Maximum time to wait for AI response: ${apiTimeoutSeconds.toInt()}s", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Slider(
        value = apiTimeoutSeconds,
        onValueChange = { apiTimeoutSeconds = it },
        onValueChangeFinished = {
            onSave(config.copy(apiTimeoutSeconds = apiTimeoutSeconds.toLong()))
        },
        valueRange = 10f..120f,
        steps = 22 // (120-10)/5 = 22 intervals for 5s steps (10, 15, ..., 120)
    )

    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))

    if (BuildConfig.SHOW_UPDATES) {
        Text("Updates", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))

        val updateRepository = remember { com.typeassist.app.data.repository.UpdateRepository(context) }
        val scope = rememberCoroutineScope()
        var isCheckingForUpdate by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isCheckingForUpdate) {
                    isCheckingForUpdate = true
                    scope.launch {
                        val result = updateRepository.checkForUpdate("estiaksoyeb", "TypeAssist")
                        isCheckingForUpdate = false
                        result.onSuccess { release ->
                            if (release != null) {
                                Toast.makeText(context, "New Update Available: ${release.tagName}", Toast.LENGTH_LONG).show()
                                // We could trigger the dialog here, but for now a toast is fine since 
                                // MainActivity will show it on next restart or we can just open the link.
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, "You are on the latest version! ✅", Toast.LENGTH_SHORT).show()
                            }
                        }.onFailure {
                            Toast.makeText(context, "Failed to check for updates: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Check for Updates", fontWeight = FontWeight.Bold)
                Text(if (isCheckingForUpdate) "Checking..." else "Current Version: ${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isCheckingForUpdate) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.rotate(180f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProviderSettingsTab(config: AppConfig, client: OkHttpClient, onSave: (AppConfig) -> Unit) {
    var selectedProvider by remember { mutableStateOf(config.provider) }
    
    // Gemini States
    var geminiKey by remember { mutableStateOf(config.apiKey) }
    var geminiModel by remember { mutableStateOf(config.model) }
    var geminiModels by remember { mutableStateOf(config.cachedGeminiModels ?: mutableListOf()) }
    var isFetchingGeminiModels by remember { mutableStateOf(false) }
    var geminiModelsError by remember { mutableStateOf<String?>(null) }
    
    // Cloudflare States
    var cfAccountId by remember { mutableStateOf(config.cloudflareConfig.accountId) }
    var cfApiToken by remember { mutableStateOf(config.cloudflareConfig.apiToken) }
    var cfModel by remember { mutableStateOf(config.cloudflareConfig.model) }
    var cfModels by remember { mutableStateOf(config.cloudflareConfig.cachedModels ?: mutableListOf()) }
    var isFetchingCfModels by remember { mutableStateOf(false) }
    var cfModelsError by remember { mutableStateOf<String?>(null) }

    // Custom API States
    var customBaseUrl by remember { mutableStateOf(config.customApiConfig.baseUrl) }
    var customApiKey by remember { mutableStateOf(config.customApiConfig.apiKey) }
    var customModel by remember { mutableStateOf(config.customApiConfig.model) }
    var customModels by remember { mutableStateOf(config.customApiConfig.cachedModels ?: mutableListOf()) }
    var isFetchingCustomModels by remember { mutableStateOf(false) }
    var customModelsError by remember { mutableStateOf<String?>(null) }

    var isKeyVisible by remember { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }
    
    val providers = listOf("gemini", "cloudflare", "custom")
    val modelFetcher = remember { ModelFetcher(client) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val context = LocalContext.current

    fun toast(message: String, long: Boolean = false) {
        if (message.isBlank()) return
        Toast.makeText(context, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    fun onMain(action: () -> Unit) {
        mainHandler.post(action)
    }

    // --- Auto model read: ask the provider which models exist and offer them as a list ---

    fun fetchCustomModels() {
        if (customBaseUrl.isBlank()) {
            toast("Enter a Base URL first")
            return
        }
        isFetchingCustomModels = true
        customModelsError = null
        modelFetcher.fetchOpenAiCompatibleModels(
            baseUrl = customBaseUrl.trim(),
            apiKey = customApiKey.trim(),
            timeoutSeconds = config.apiTimeoutSeconds
        ) { result ->
            onMain {
                isFetchingCustomModels = false
                result.onSuccess { list ->
                    customModels = list
                    // Only the model list is cached here - keys and URLs are saved with the Save button.
                    onSave(config.copy(customApiConfig = config.customApiConfig.copy(cachedModels = list.toMutableList())))
                    toast("${list.size} models found ✅")
                }.onFailure { e ->
                    customModelsError = e.message ?: "Could not read the model list"
                    toast(customModelsError ?: "", true)
                }
            }
        }
    }

    fun fetchGeminiModels() {
        if (geminiKey.isBlank()) {
            toast("Add your Gemini API key first")
            return
        }
        isFetchingGeminiModels = true
        geminiModelsError = null
        modelFetcher.fetchGeminiModels(
            apiKey = geminiKey.trim(),
            timeoutSeconds = config.apiTimeoutSeconds
        ) { result ->
            onMain {
                isFetchingGeminiModels = false
                result.onSuccess { list ->
                    geminiModels = list
                    onSave(config.copy(cachedGeminiModels = list.toMutableList()))
                    toast("${list.size} Gemini models found ✅")
                }.onFailure { e ->
                    geminiModelsError = e.message ?: "Could not read the model list"
                    toast(geminiModelsError ?: "", true)
                }
            }
        }
    }

    fun fetchCloudflareModels() {
        if (cfAccountId.isBlank() || cfApiToken.isBlank()) {
            toast("Add your Account ID and API token first")
            return
        }
        isFetchingCfModels = true
        cfModelsError = null
        modelFetcher.fetchCloudflareModels(
            accountId = cfAccountId.trim(),
            apiToken = cfApiToken.trim(),
            timeoutSeconds = config.apiTimeoutSeconds
        ) { result ->
            onMain {
                isFetchingCfModels = false
                result.onSuccess { list ->
                    cfModels = list
                    onSave(config.copy(cloudflareConfig = config.cloudflareConfig.copy(cachedModels = list.toMutableList())))
                    toast("${list.size} Cloudflare models found ✅")
                }.onFailure { e ->
                    cfModelsError = e.message ?: "Could not read the model list"
                    toast(cfModelsError ?: "", true)
                }
            }
        }
    }

    fun verifyGemini(k: String) {
        val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models?key=$k").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { (context as ComponentActivity).runOnUiThread { Toast.makeText(context, "Network Error: ${e.message}", Toast.LENGTH_LONG).show() } }
            override fun onResponse(call: Call, response: Response) { response.use { if(it.isSuccessful) (context as ComponentActivity).runOnUiThread { Toast.makeText(context, "Gemini API Verified! ✅", Toast.LENGTH_SHORT).show() } else (context as ComponentActivity).runOnUiThread { Toast.makeText(context, "Invalid Gemini Key ❌", Toast.LENGTH_SHORT).show() } } }
        })
    }

    fun verifyCloudflare(acc: String, tok: String, mod: String) {
        val cloudflareClient = CloudflareApiClient(client)
        cloudflareClient.callCloudflare(acc, tok, mod, "You are a helpful assistant.", "hi", config.apiTimeoutSeconds) { result ->
            (context as ComponentActivity).runOnUiThread {
                result.onSuccess {
                    Toast.makeText(context, "Cloudflare Verified! ✅ ($mod)", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "Cloudflare Verification Failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun verifyCustomApi(baseUrl: String, apiKey: String, model: String) {
        val customClient = CustomApiClient(client)
        customClient.callCustomApi(baseUrl, apiKey, model, "You are a helpful assistant.", "hi", config.apiTimeoutSeconds) { result ->
            (context as ComponentActivity).runOnUiThread {
                result.onSuccess {
                    Toast.makeText(context, "Custom API Verified! ✅ ($model)", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "Custom API Verification Failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    val primaryColor = MaterialTheme.colorScheme.primary

    // Provider Selection
    Text("AI Provider", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
    ExposedDropdownMenuBox(expanded = providerExpanded, onExpandedChange = { providerExpanded = !providerExpanded }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(value = selectedProvider.uppercase(), onValueChange = {}, readOnly = true, label = { Text("Select Provider") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) }, colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(), modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) { providers.forEach { item -> DropdownMenuItem(text = { Text(text = item.uppercase()) }, onClick = { selectedProvider = item; providerExpanded = false }) } }
    }
    
    Spacer(Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(Modifier.height(24.dp))

    if (selectedProvider == "gemini") {
        // Gemini Setup
        if (config.savedGeminiConfigs.isNotEmpty()) {
            Text("Saved Gemini Configs", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryColor)
            Spacer(Modifier.height(8.dp))
            config.savedGeminiConfigs.forEach { savedConfig ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                             geminiKey = savedConfig.apiKey
                             geminiModel = savedConfig.model
                        },
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = savedConfig.model.ifBlank { "Unknown Model" },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(text = "Key: " + savedConfig.apiKey.take(4) + "...", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = {
                        val newSavedList = config.savedGeminiConfigs.toMutableList()
                        newSavedList.remove(savedConfig)
                        onSave(config.copy(savedGeminiConfigs = newSavedList))
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider()
            }
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(value = geminiKey, onValueChange = { geminiKey = it }, label = { Text("Gemini API Key") }, modifier = Modifier.fillMaxWidth(), visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { isKeyVisible = !isKeyVisible }) { Icon(if (isKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) } })
        Spacer(Modifier.height(16.dp))
        ModelSelector(
            label = "Gemini Model Name",
            value = geminiModel,
            onValueChange = { geminiModel = it },
            models = geminiModels,
            isFetching = isFetchingGeminiModels,
            errorMessage = geminiModelsError,
            canFetch = geminiKey.isNotBlank(),
            helperText = "Tap Fetch Models to read the live list from Google.",
            placeholder = "e.g. gemini-2.5-flash",
            pickerTitle = "Select Gemini Model",
            onFetch = { fetchGeminiModels() },
            onModelPicked = { picked ->
                // Picking from the list applies the key + model right away.
                onSave(config.copy(provider = "gemini", apiKey = geminiKey.trim(), model = picked, cachedGeminiModels = geminiModels.toMutableList()))
                toast("Gemini model set to $picked")
            }
        )
    } else if (selectedProvider == "cloudflare") {
        // Cloudflare Setup
        if (config.savedCloudflareConfigs.isNotEmpty()) {
            Text("Saved Cloudflare Configs", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryColor)
            Spacer(Modifier.height(8.dp))
            config.savedCloudflareConfigs.forEach { savedConfig ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                             cfAccountId = savedConfig.accountId
                             cfApiToken = savedConfig.apiToken
                             cfModel = savedConfig.model
                        },
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = savedConfig.model.ifBlank { "Unknown Model" },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(text = "Account: " + savedConfig.accountId.take(4) + "...", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = {
                        val newSavedList = config.savedCloudflareConfigs.toMutableList()
                        newSavedList.remove(savedConfig)
                        onSave(config.copy(savedCloudflareConfigs = newSavedList))
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider()
            }
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(value = cfAccountId, onValueChange = { cfAccountId = it }, label = { Text("Cloudflare Account ID") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = cfApiToken, onValueChange = { cfApiToken = it }, label = { Text("Cloudflare API Token") }, modifier = Modifier.fillMaxWidth(), visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { isKeyVisible = !isKeyVisible }) { Icon(if (isKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) } })
        Spacer(Modifier.height(16.dp))
        ModelSelector(
            label = "Cloudflare Model ID",
            value = cfModel,
            onValueChange = { cfModel = it },
            models = cfModels,
            isFetching = isFetchingCfModels,
            errorMessage = cfModelsError,
            canFetch = cfAccountId.isNotBlank() && cfApiToken.isNotBlank(),
            helperText = "Popular text models are listed already. Fetch to read your account's full list.",
            placeholder = "@cf/meta/llama-3-8b-instruct",
            pickerTitle = "Select Cloudflare Model",
            onFetch = { fetchCloudflareModels() },
            onModelPicked = { picked ->
                onSave(config.copy(provider = "cloudflare", cloudflareConfig = config.cloudflareConfig.copy(accountId = cfAccountId.trim(), apiToken = cfApiToken.trim(), model = picked, cachedModels = cfModels.toMutableList())))
                toast("Cloudflare model set to $picked")
            }
        )
    } else if (selectedProvider == "custom") {
        // Custom API Setup
        if (config.savedCustomConfigs.isNotEmpty()) {
            Text("Saved Configurations", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryColor)
            Spacer(Modifier.height(8.dp))
            config.savedCustomConfigs.forEach { savedConfig ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                             customBaseUrl = savedConfig.baseUrl
                             customApiKey = savedConfig.apiKey
                             customModel = savedConfig.model
                        },
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = savedConfig.model.ifBlank { "Unknown Model" },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(text = savedConfig.baseUrl, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = {
                        val newSavedList = config.savedCustomConfigs.toMutableList()
                        newSavedList.remove(savedConfig)
                        onSave(config.copy(savedCustomConfigs = newSavedList))
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider()
            }
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(value = customBaseUrl, onValueChange = { customBaseUrl = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("https://api.openai.com/v1") })
        Spacer(Modifier.height(8.dp))
        Text("Quick setup (fills the Base URL)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(apiPresets) { preset ->
                FilterChip(
                    selected = customBaseUrl.trim().trimEnd('/').equals(preset.baseUrl, ignoreCase = true),
                    onClick = {
                        customBaseUrl = preset.baseUrl
                        // Read the model list for the chosen provider right away when a key is present.
                        if (customApiKey.isNotBlank()) fetchCustomModels()
                    },
                    label = { Text(preset.label, fontSize = 12.sp) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = customApiKey, onValueChange = { customApiKey = it }, label = { Text("API Key (Optional)") }, modifier = Modifier.fillMaxWidth(), visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { isKeyVisible = !isKeyVisible }) { Icon(if (isKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) } })
        Spacer(Modifier.height(16.dp))
        ModelSelector(
            label = "Model Name",
            value = customModel,
            onValueChange = { customModel = it },
            models = customModels,
            isFetching = isFetchingCustomModels,
            errorMessage = customModelsError,
            canFetch = customBaseUrl.isNotBlank(),
            helperText = "Tap Fetch Models to read the list from your provider (works with any OpenAI compatible API).",
            placeholder = "gpt-3.5-turbo",
            pickerTitle = "Select Model",
            onFetch = { fetchCustomModels() },
            onModelPicked = { picked ->
                // Picking from the list applies the URL + key + model right away.
                onSave(config.copy(provider = "custom", customApiConfig = config.customApiConfig.copy(baseUrl = customBaseUrl.trim(), apiKey = customApiKey.trim(), model = picked, cachedModels = customModels.toMutableList())))
                toast("Model set to $picked")
            }
        )
    } else if (selectedProvider == "local") {
        // Local LLM Saved Models List
        if (config.savedLocalModels.isNotEmpty()) {
            Text("Previously Selected Models", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryColor)
            Spacer(Modifier.height(8.dp))
            config.savedLocalModels.forEach { savedPath ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                             // Update the config with selected model
                             onSave(config.copy(localLlmConfig = config.localLlmConfig.copy(modelPath = savedPath)))
                             Toast.makeText(context, "Model selected: ${getFileName(context, savedPath)}", Toast.LENGTH_SHORT).show()
                        },
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val isCurrent = config.localLlmConfig.modelPath == savedPath
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = getFileName(context, savedPath),
                            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (isCurrent) primaryColor else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = {
                        val newSavedList = config.savedLocalModels.toMutableList()
                        newSavedList.remove(savedPath)
                        onSave(config.copy(savedLocalModels = newSavedList))
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider()
            }
            Spacer(Modifier.height(16.dp))
        }
        
        Text("Go to the 'Local LLM' tab to select a new model file.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Spacer(Modifier.height(24.dp))
    
    Button(
        onClick = { 
            val newCustomConfig = CustomApiConfig(
                baseUrl = customBaseUrl.trim(),
                apiKey = customApiKey.trim(),
                model = customModel.trim(),
                cachedModels = customModels.toMutableList()
            )
            val newGeminiConfig = com.typeassist.app.data.SavedGeminiConfig(
                apiKey = geminiKey.trim(),
                model = geminiModel.trim()
            )
            val newCloudflareConfig = CloudflareConfig(
                accountId = cfAccountId.trim(),
                apiToken = cfApiToken.trim(),
                model = cfModel.trim(),
                cachedModels = cfModels.toMutableList()
            )

            // Compare only the fields the user actually configures, so a changed model
            // cache does not create duplicate entries.
            val updatedSavedCustom = config.savedCustomConfigs.toMutableList()
            if (selectedProvider == "custom" && customBaseUrl.isNotBlank() && customModel.isNotBlank()) {
                 val exists = updatedSavedCustom.any { it.baseUrl == newCustomConfig.baseUrl && it.apiKey == newCustomConfig.apiKey && it.model == newCustomConfig.model }
                 if (!exists) updatedSavedCustom.add(newCustomConfig)
            }

            val updatedSavedGemini = config.savedGeminiConfigs.toMutableList()
            if (selectedProvider == "gemini" && geminiKey.isNotBlank()) {
                 if (updatedSavedGemini.none { it == newGeminiConfig }) updatedSavedGemini.add(newGeminiConfig)
            }

            val updatedSavedCloudflare = config.savedCloudflareConfigs.toMutableList()
            if (selectedProvider == "cloudflare" && cfApiToken.isNotBlank()) {
                 val exists = updatedSavedCloudflare.any { it.accountId == newCloudflareConfig.accountId && it.apiToken == newCloudflareConfig.apiToken && it.model == newCloudflareConfig.model }
                 if (!exists) updatedSavedCloudflare.add(newCloudflareConfig)
            }

            val updatedSavedLocal = config.savedLocalModels.toMutableList()
            if (selectedProvider == "local" && config.localLlmConfig.modelPath.isNotBlank()) {
                if (updatedSavedLocal.none { it == config.localLlmConfig.modelPath }) {
                    updatedSavedLocal.add(config.localLlmConfig.modelPath)
                }
            }

            var newConfig = config.copy(
                provider = selectedProvider,
                apiKey = geminiKey.trim(),
                model = geminiModel.trim(),
                cachedGeminiModels = geminiModels.toMutableList(),
                cloudflareConfig = newCloudflareConfig,
                customApiConfig = newCustomConfig,
                localLlmConfig = config.localLlmConfig, // This is updated via the sliders in LocalLlmSetup
                savedCustomConfigs = updatedSavedCustom,
                savedGeminiConfigs = updatedSavedGemini,
                savedCloudflareConfigs = updatedSavedCloudflare,
                savedLocalModels = updatedSavedLocal
            )
            
            // Auto-enable logic
            val hasValidKey = when (selectedProvider) {
                "gemini" -> geminiKey.isNotBlank()
                "cloudflare" -> cfApiToken.isNotBlank()
                "custom" -> customBaseUrl.isNotBlank() && customModel.isNotBlank()
                "local" -> config.localLlmConfig.modelPath.isNotBlank()
                else -> false
            }
            
            if (!config.isAppEnabled && hasValidKey) {
                 newConfig = newConfig.copy(isAppEnabled = true)
            }
            
            onSave(newConfig)
            val activeModelLabel = when (selectedProvider) {
                "gemini" -> geminiModel.trim()
                "cloudflare" -> cfModel.trim()
                "custom" -> customModel.trim()
                else -> ""
            }
            Toast.makeText(
                context,
                if (activeModelLabel.isBlank()) "Config Saved" else "Config Saved • using $activeModelLabel",
                Toast.LENGTH_SHORT
            ).show()
            
            when (selectedProvider) {
                "gemini" -> if (geminiKey.isNotBlank()) verifyGemini(geminiKey.trim())
                "cloudflare" -> if (cfApiToken.isNotBlank()) verifyCloudflare(cfAccountId.trim(), cfApiToken.trim(), cfModel.trim())
                "custom" -> if (customBaseUrl.isNotBlank()) verifyCustomApi(customBaseUrl.trim(), customApiKey.trim(), customModel.trim())
            }
        }, 
        modifier = Modifier.fillMaxWidth().height(50.dp), 
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) { 
        Text("Test and Save Config") 
    } 
    
    Spacer(Modifier.height(32.dp))
    
    when (selectedProvider) {
        "gemini" -> GeminiHelp(primaryColor, context)
        "cloudflare" -> CloudflareHelp(primaryColor, context)
        "custom" -> CustomApiHelp(primaryColor, context)
        "local" -> LocalLlmHelp(primaryColor, context)
    }
}

@Composable
fun LocalLlmSetup(config: AppConfig, onSave: (AppConfig) -> Unit) {
    val context = LocalContext.current
    var modelPath by remember { mutableStateOf(config.localLlmConfig.modelPath) }
    var temperature by remember { mutableStateOf(config.localLlmConfig.temperature) }
    var topP by remember { mutableStateOf(config.localLlmConfig.topP) }
    var maxTokens by remember { mutableStateOf(config.localLlmConfig.maxTokens.toFloat()) }
    var threads by remember { mutableStateOf(config.localLlmConfig.numThreads.toFloat()) }
    var useGpu by remember { mutableStateOf(config.localLlmConfig.useGpu) }
    var disableReasoning by remember { mutableStateOf(config.localLlmConfig.disableReasoning) }

    val cacheFile = remember(modelPath) { java.io.File(context.cacheDir, "local_model.gguf") }
    var cachedSize by remember(modelPath) { mutableStateOf(if (cacheFile.exists()) cacheFile.length() else 0L) }

    fun clearLocalModelStorage() {
        val freedBytes = if (cacheFile.exists()) cacheFile.length() else 0L
        if (cacheFile.exists()) {
            try {
                cacheFile.delete()
            } catch (e: Exception) {
                android.util.Log.e("SettingsScreen", "Failed to delete cached model file: ${e.message}")
            }
        }
        cachedSize = 0L
        modelPath = ""
        onSave(config.copy(localLlmConfig = config.localLlmConfig.copy(modelPath = "")))
        val freedText = if (freedBytes > 0) " Freed ${formatFileSize(freedBytes)} of storage." else ""
        Toast.makeText(context, "Local model reference cleared.$freedText", Toast.LENGTH_LONG).show()
    }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                android.util.Log.w("SettingsScreen", "Could not take persistable permission: ${e.message}")
            }
            modelPath = it.toString()
            onSave(config.copy(localLlmConfig = config.localLlmConfig.copy(modelPath = modelPath)))
        }
    }

    Text("Model Configuration", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Selected Model", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = getFileName(context, modelPath),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            if (cachedSize > 0L) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("📦 Internal App Cache: ", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text(formatFileSize(cachedSize), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { launcher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (modelPath.isBlank()) "Select Model" else "Change Model")
                }

                if (modelPath.isNotBlank() || cachedSize > 0L) {
                    OutlinedButton(
                        onClick = { clearLocalModelStorage() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Storage", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Clear Model Cache")
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Low-parameter models (e.g. under 1.5B parameters) may fail to follow complex prompt instructions, yield empty responses when token budget is low, or yield wrong/unexpected responses.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                    lineHeight = 14.sp
                )
            }
        }
    }

    Spacer(Modifier.height(24.dp))

    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Use GPU Acceleration (Vulkan)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                "Significantly faster on modern devices. Supports Mali and Adreno GPUs.", 
                fontSize = 11.sp, 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = useGpu,
            onCheckedChange = { 
                useGpu = it
                onSave(config.copy(localLlmConfig = config.localLlmConfig.copy(useGpu = it)))
            }
        )
    }

    Spacer(Modifier.height(16.dp))

    // --- Reasoning Model Detection ---
    val looksLikeReasoningModel = isReasoningModel(modelPath)
    if (looksLikeReasoningModel) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("🧠", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Reasoning Model Detected",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "This model outputs internal thinking (\u003cthink\u003e...\u003c/think\u003e). " +
                        "TypeAssist automatically strips it from the final output.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        lineHeight = 14.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    // --- Disable Reasoning Toggle ---
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Disable Reasoning Output", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                if (looksLikeReasoningModel)
                    "Send /no_think hint and strip \u003cthink\u003e blocks from response."
                else
                    "For reasoning models: suppress thinking tokens and strip \u003cthink\u003e blocks.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
        Switch(
            checked = disableReasoning,
            onCheckedChange = {
                disableReasoning = it
                onSave(config.copy(localLlmConfig = config.localLlmConfig.copy(disableReasoning = it)))
            }
        )
    }

    Spacer(Modifier.height(16.dp))
    
    Text("Temperature: ${String.format("%.2f", temperature)}", fontSize = 14.sp)
    Slider(value = temperature, onValueChange = { 
        temperature = it
        onSave(config.copy(localLlmConfig = config.localLlmConfig.copy(temperature = it)))
    }, valueRange = 0f..2f)

    Spacer(Modifier.height(8.dp))

    Text("Top-P: ${String.format("%.2f", topP)}", fontSize = 14.sp)
    Slider(value = topP, onValueChange = { 
        topP = it
        onSave(config.copy(localLlmConfig = config.localLlmConfig.copy(topP = it)))
    }, valueRange = 0f..1f)

    Spacer(Modifier.height(8.dp))

    Text("Max Tokens: ${maxTokens.toInt()}", fontSize = 14.sp)
    Slider(value = maxTokens, onValueChange = { 
        maxTokens = it
        onSave(config.copy(localLlmConfig = config.localLlmConfig.copy(maxTokens = it.toInt())))
    }, valueRange = 64f..2048f, steps = 31)

    Spacer(Modifier.height(8.dp))

    Text("Threads: ${threads.toInt()}", fontSize = 14.sp)
    Slider(value = threads, onValueChange = { 
        threads = it
        onSave(config.copy(localLlmConfig = config.localLlmConfig.copy(numThreads = it.toInt())))
    }, valueRange = 1f..8f, steps = 7)
}

@Composable
fun LocalLlmHelp(primaryColor: Color, context: android.content.Context) {
    Text("Local LLM (llama.cpp) Setup", fontWeight = FontWeight.Bold, fontSize = 18.sp)
    Spacer(Modifier.height(16.dp))
    Text("Run AI entirely on your device with no internet.", fontSize = 14.sp)
    Spacer(Modifier.height(8.dp))
    Text("1. Download a GGUF model from Hugging Face (e.g., Qwen2-0.5B-Instruct-GGUF).", fontSize = 14.sp)
    Text("2. Place the file in your device storage.", fontSize = 14.sp)
    Text("3. Provide the full absolute path to the .gguf file above.", fontSize = 14.sp)
    Text("4. Set threads to match your CPU cores (usually 4 or 8).", fontSize = 14.sp)
    Spacer(Modifier.height(8.dp))
    Text("Warning: Local inference is slow on older devices and consumes significant battery.", fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
}

@Composable
fun GeminiHelp(primaryColor: Color, context: android.content.Context) {
    Text("How to get Gemini API Key?", fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(Modifier.height(16.dp))
    val steps = listOf("1. Visit Google AI Studio at ", "2. Sign in with your Google account.", "3. Click 'Get API key'.", "4. Click 'Create API key'.", "5. Copy and paste above.", "6. Select Model.", "7. Click Save.")
    val annotatedString = buildAnnotatedString { steps.forEach { step -> if (step.contains("Google AI Studio")) { append("1. Visit Google AI Studio at "); pushStringAnnotation(tag = "URL", annotation = "https://aistudio.google.com/"); withStyle(style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) { append("aistudio.google.com") }; pop(); append(".\n") } else { append(step + "\n") } } }
    ClickableText(text = annotatedString, style = LocalTextStyle.current.copy(fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurface), onClick = { offset -> annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset).firstOrNull()?.let { annotation -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))) } })
}

@Composable
fun CloudflareHelp(primaryColor: Color, context: android.content.Context) {
    Text("How to setup Cloudflare AI?", fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(Modifier.height(16.dp))
    val steps = listOf(
        "1. Visit Workers AI at ",
        "2. Sign in with your Cloudflare account.",
        "3. Click on 'REST API'.",
        "4. Click 'Create a Workers AI API token'.",
        "5. Copy and paste the API Key above.",
        "6. Copy your 'Account ID' from the same page.",
        "7. Paste Account ID and Token above.",
        "8. Click Test and Save."
    )
    val annotatedString = buildAnnotatedString { 
        steps.forEach { step -> 
            if (step.contains("Visit Workers AI at")) { 
                append("1. Visit Workers AI at ")
                pushStringAnnotation(tag = "URL", annotation = "https://dash.cloudflare.com/?to=/:account/ai/workers-ai")
                withStyle(style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) { append("Cloudflare Dash") }
                pop()
                append(".\n")
            } else { 
                append(step + "\n") 
            } 
        } 
    }
    ClickableText(text = annotatedString, style = LocalTextStyle.current.copy(fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurface), onClick = { offset -> annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset).firstOrNull()?.let { annotation -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))) } })
}

@Composable
fun CustomApiHelp(primaryColor: Color, context: android.content.Context) {
    Text("Custom API Setup", fontWeight = FontWeight.Bold, fontSize = 18.sp)
    Spacer(Modifier.height(16.dp))
    Text("Compatible with any OpenAI-style Chat Completion API.", fontSize = 14.sp)
    Spacer(Modifier.height(8.dp))
    Text("1. Base URL: The API endpoint (e.g. https://api.groq.com/openai/v1 or http://localhost:11434/v1)", fontSize = 14.sp)
    Text("2. API Key: Your provider's API key (leave blank for local LLMs).", fontSize = 14.sp)
    Text("3. Model Name: Tap 'Fetch Models' to read the available models from the provider, then pick one from the list. You can still type a model ID by hand.", fontSize = 14.sp)
    Spacer(Modifier.height(8.dp))
    Text("Tip: the 'Quick setup' chips fill the Base URL for popular providers (OpenAI, Groq, OpenRouter, DeepSeek, Ollama, LM Studio and more), so you only need to paste your key.", fontSize = 14.sp)
}

/** Well known OpenAI compatible providers, used by the "Quick setup" chips. */
private data class ApiPreset(val label: String, val baseUrl: String)

private val apiPresets = listOf(
    ApiPreset("OpenAI", "https://api.openai.com/v1"),
    ApiPreset("Groq", "https://api.groq.com/openai/v1"),
    ApiPreset("OpenRouter", "https://openrouter.ai/api/v1"),
    ApiPreset("DeepSeek", "https://api.deepseek.com/v1"),
    ApiPreset("Together", "https://api.together.xyz/v1"),
    ApiPreset("Mistral", "https://api.mistral.ai/v1"),
    ApiPreset("xAI (Grok)", "https://api.x.ai/v1"),
    ApiPreset("Cerebras", "https://api.cerebras.ai/v1"),
    ApiPreset("Ollama (local)", "http://localhost:11434/v1"),
    ApiPreset("LM Studio (local)", "http://localhost:1234/v1")
)

private fun getFileName(context: android.content.Context, uriOrPath: String): String {
    if (uriOrPath.isBlank()) return "No model selected"
    if (!uriOrPath.startsWith("content://")) {
        return uriOrPath.substringAfterLast("/")
    }
    return try {
        val uri = Uri.parse(uriOrPath)
        var name: String? = null
        
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx != -1) {
                    name = cursor.getString(idx)
                }
            }
        }

        if (name.isNullOrBlank()) {
            val lastSeg = Uri.decode(uri.lastPathSegment ?: "")
            name = when {
                lastSeg.contains(":") -> lastSeg.substringAfterLast(":")
                lastSeg.contains("/") -> lastSeg.substringAfterLast("/")
                else -> lastSeg
            }
        }

        if (!name.isNullOrBlank()) name!! else "Local GGUF Model"
    } catch (e: Exception) {
        val last = uriOrPath.substringAfterLast("/")
        Uri.decode(last).substringAfterLast(":")
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val clampedGroup = digitGroups.coerceIn(0, units.size - 1)
    return String.format("%.2f %s", bytes / Math.pow(1024.0, clampedGroup.toDouble()), units[clampedGroup])
}
