package com.typeassist.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.typeassist.app.api.HttpClients
import com.typeassist.app.api.ModelDownloader
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.isReasoningModel
import com.typeassist.app.ui.MonoFamily
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.components.AppCard
import com.typeassist.app.ui.components.AppScaffold
import com.typeassist.app.ui.components.AppTextField
import com.typeassist.app.ui.components.AppTopBar
import com.typeassist.app.ui.components.CardFact
import com.typeassist.app.ui.components.DangerButton
import com.typeassist.app.ui.components.GhostButton
import com.typeassist.app.ui.components.GroupCard
import com.typeassist.app.ui.components.NoticeCard
import com.typeassist.app.ui.components.NoticeTone
import com.typeassist.app.ui.components.PrimaryButton
import com.typeassist.app.ui.components.RowDivider
import com.typeassist.app.ui.components.ScreenIntro
import com.typeassist.app.ui.components.SectionHeader
import com.typeassist.app.ui.components.SettingRow
import com.typeassist.app.ui.components.SmallButton
import com.typeassist.app.ui.components.StatusPill
import com.typeassist.app.ui.components.StatusTone
import com.typeassist.app.ui.components.SwitchRow
import java.io.File
import java.util.Locale

/**
 * ============================== LOCAL MODEL ==============================
 *
 * Bring your own GGUF file and run everything on the device. The screen is built around the three
 * things that actually go wrong with local inference: no model selected, the model still sitting in
 * app storage, and a reasoning model's thinking tokens leaking into the answer. Each of those has a
 * control or an explanation right next to it.
 */
@Composable
fun LocalLlmScreen(
    config: AppConfig,
    onSave: (AppConfig) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var modelPath by remember { mutableStateOf(config.localLlmConfig.modelPath) }
    var temperature by remember { mutableFloatStateOf(config.localLlmConfig.temperature) }
    var topP by remember { mutableFloatStateOf(config.localLlmConfig.topP) }
    var maxTokens by remember { mutableFloatStateOf(config.localLlmConfig.maxTokens.toFloat()) }
    var threads by remember { mutableFloatStateOf(config.localLlmConfig.numThreads.toFloat()) }
    var useGpu by remember { mutableStateOf(config.localLlmConfig.useGpu) }
    var disableReasoning by remember { mutableStateOf(config.localLlmConfig.disableReasoning) }

    val cacheFile = remember(modelPath) { File(context.cacheDir, "local_model.gguf") }
    var cachedSize by remember(modelPath) {
        mutableLongStateOf(if (cacheFile.exists()) cacheFile.length() else 0L)
    }

    // -- In-app model download --
    val downloader = remember { ModelDownloader(HttpClients.shared()) }
    var downloadUrl by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(-1L) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w("LocalLlmScreen", "Could not take persistable permission: ${e.message}")
            }
            val picked = it.toString()
            modelPath = picked
            onSave(config.copy(localLlmConfig = config.localLlmConfig.copy(modelPath = picked)))
        }
    }

    fun clearLocalModelStorage() {
        val freedBytes = if (cacheFile.exists()) cacheFile.length() else 0L
        if (cacheFile.exists()) {
            try {
                cacheFile.delete()
            } catch (e: Exception) {
                Log.e("LocalLlmScreen", "Failed to delete cached model file: ${e.message}")
            }
        }
        cachedSize = 0L
        modelPath = ""
        onSave(config.copy(localLlmConfig = config.localLlmConfig.copy(modelPath = "")))
        val freedText = if (freedBytes > 0) " Freed ${formatFileSize(freedBytes)}." else ""
        Toast.makeText(context, "Local model reference cleared.$freedText", Toast.LENGTH_LONG).show()
    }

    val isActive = config.provider == "local"
    val fileName = modelName(context, modelPath)
    val looksLikeReasoningModel = isReasoningModel(modelPath)

    AppScaffold(
        topBar = { AppTopBar(title = "Local model", onBack = onBack) }
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
                    "Runs a GGUF model with llama.cpp, entirely on this device. Nothing is sent " +
                        "anywhere and no API key is needed."
                )
            }

            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = MonoFamily,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            StatusPill(
                                text = when {
                                    modelPath.isBlank() -> "No model selected"
                                    isActive -> "Active provider"
                                    else -> "Selected"
                                },
                                tone = when {
                                    modelPath.isBlank() -> StatusTone.Warning
                                    isActive -> StatusTone.Active
                                    else -> StatusTone.Idle
                                }
                            )
                        }
                    }
                    if (cachedSize > 0L) {
                        Spacer(Modifier.height(Spacing.md))
                        CardFact(
                            label = "Copied into app storage",
                            value = formatFileSize(cachedSize)
                        )
                    }
                    Spacer(Modifier.height(Spacing.lg))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SmallButton(
                            text = if (modelPath.isBlank()) "Select model" else "Change model",
                            primary = modelPath.isBlank(),
                            onClick = { launcher.launch(arrayOf("*/*")) }
                        )
                        if (modelPath.isNotBlank() || cachedSize > 0L) {
                            SmallButton(
                                text = "Clear model cache",
                                onClick = { clearLocalModelStorage() }
                            )
                        }
                    }
                }
            }

            item {
                NoticeCard(
                    title = "Small models need simple instructions",
                    message = "Models under roughly 1.5B parameters often ignore complex prompts, " +
                        "answer with nothing when the token budget is low, or reply off-topic. If " +
                        "results look wrong, raise max tokens or pick a bigger model.",
                    tone = NoticeTone.Warning,
                    icon = Icons.Default.Info
                )
            }

            item {
                PrimaryButton(
                    text = if (isActive) "Local model is the active provider" else "Set as active provider",
                    onClick = {
                        onSave(config.copy(provider = "local"))
                        Toast.makeText(
                            context,
                            "Local model saved as the active provider.",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isActive
                )
            }

            item { SectionHeader(title = "Performance") }
            item {
                GroupCard {
                    SwitchRow(
                        title = "Use GPU acceleration (Vulkan)",
                        description = "Much faster on modern Mali and Adreno GPUs. Leave it off if " +
                            "the device gets hot or output looks corrupted.",
                        checked = useGpu,
                        onCheckedChange = {
                            useGpu = it
                            onSave(config.copy(localLlmConfig = config.localLlmConfig.copy(useGpu = it)))
                        }
                    )
                    RowDivider()
                    SettingRow(
                        title = "CPU threads",
                        description = "Match your device's performance cores, usually 4 or 8.",
                        icon = Icons.Default.Memory
                    ) {
                        Text(
                            text = "${threads.toInt()}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Slider(
                        value = threads,
                        onValueChange = { threads = it },
                        onValueChangeFinished = {
                            onSave(
                                config.copy(
                                    localLlmConfig = config.localLlmConfig.copy(numThreads = threads.toInt())
                                )
                            )
                        },
                        valueRange = 1f..8f,
                        steps = 7
                    )
                }
            }

            if (looksLikeReasoningModel) {
                item {
                    NoticeCard(
                        title = "Reasoning model detected",
                        message = "This model writes its internal thinking (<think>…</think>) before " +
                            "the answer. Prompt AI strips it from the final text automatically, and the " +
                            "switch below can suppress it completely.",
                        tone = NoticeTone.Info,
                        icon = Icons.Default.Info
                    )
                }
            }

            item {
                GroupCard {
                    SwitchRow(
                        title = "Disable reasoning output",
                        description = if (looksLikeReasoningModel) {
                            "Sends a /no_think hint and removes <think> blocks from the answer."
                        } else {
                            "For reasoning models: suppress thinking tokens and strip <think> blocks."
                        },
                        checked = disableReasoning,
                        onCheckedChange = {
                            disableReasoning = it
                            onSave(
                                config.copy(
                                    localLlmConfig = config.localLlmConfig.copy(disableReasoning = it)
                                )
                            )
                        }
                    )
                }
            }

            item { SectionHeader(title = "Sampling") }
            item {
                AppCard {
                    SettingRow(
                        title = "Temperature",
                        description = "Higher is more creative, lower is more literal."
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2f", temperature),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Slider(
                        value = temperature,
                        onValueChange = {
                            temperature = it
                            onSave(
                                config.copy(localLlmConfig = config.localLlmConfig.copy(temperature = it))
                            )
                        },
                        valueRange = 0f..2f
                    )
                    RowDivider(insetStart = false)
                    Spacer(Modifier.height(Spacing.sm))
                    SettingRow(
                        title = "Top-P",
                        description = "Limits the model to the most likely tokens."
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2f", topP),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Slider(
                        value = topP,
                        onValueChange = {
                            topP = it
                            onSave(config.copy(localLlmConfig = config.localLlmConfig.copy(topP = it)))
                        },
                        valueRange = 0f..1f
                    )
                    RowDivider(insetStart = false)
                    Spacer(Modifier.height(Spacing.sm))
                    SettingRow(
                        title = "Max tokens",
                        description = "The longest answer the model may produce."
                    ) {
                        Text(
                            text = "${maxTokens.toInt()}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Slider(
                        value = maxTokens,
                        onValueChange = {
                            maxTokens = it
                            onSave(
                                config.copy(
                                    localLlmConfig = config.localLlmConfig.copy(maxTokens = it.toInt())
                                )
                            )
                        },
                        valueRange = 64f..2048f,
                        steps = 31
                    )
                }
            }

            item { SectionHeader(title = "Getting a model") }
            item {
                AppCard {
                    Text(
                        text = "Download a GGUF model straight into the app by pasting its link, or " +
                            "download it yourself (for example Qwen2-0.5B-Instruct-GGUF from Hugging " +
                            "Face) and select it above. Either way Prompt AI uses the private app " +
                            "storage copy, which is what the cache figure refers to.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.md))

                    // -- Download from a direct link --
                    val urlHint = when {
                        downloadError != null -> downloadError
                        downloadUrl.isNotBlank() && !downloader.looksLikeModelUrl(downloadUrl) ->
                            "That link does not end in .gguf — it may still work if it redirects to the file."
                        else -> null
                    }
                    AppTextField(
                        value = downloadUrl,
                        onValueChange = { downloadUrl = it; downloadError = null },
                        label = "Model link",
                        placeholder = "https://huggingface.co/…/resolve/main/model.gguf",
                        supporting = urlHint,
                        isError = downloadError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    if (downloading) {
                        val percent = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else -1
                        Text(
                            text = if (totalBytes > 0) {
                                "${formatFileSize(downloadedBytes)} of ${formatFileSize(totalBytes)} ($percent%)"
                            } else {
                                "${formatFileSize(downloadedBytes)} downloaded"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        LinearProgressIndicator(
                            progress = {
                                if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        GhostButton(text = "Cancel download", onClick = {
                            downloader.cancel()
                            downloading = false
                        })
                    } else {
                        PrimaryButton(
                            text = "Download model",
                            onClick = {
                                if (downloadUrl.isBlank()) {
                                    downloadError = "Paste a link to a .gguf file first."
                                } else {
                                    downloading = true
                                    downloadedBytes = 0L
                                    totalBytes = -1L
                                    downloader.download(
                                        url = downloadUrl,
                                        destination = cacheFile,
                                        onProgress = { done, total ->
                                            downloadedBytes = done
                                            totalBytes = total
                                        },
                                        onFinished = { result ->
                                            downloading = false
                                            result.fold(
                                                onSuccess = { file ->
                                                    modelPath = file.absolutePath
                                                    cachedSize = file.length()
                                                    onSave(
                                                        config.copy(
                                                            localLlmConfig = config.localLlmConfig.copy(
                                                                modelPath = file.absolutePath
                                                            )
                                                        )
                                                    )
                                                    Toast.makeText(
                                                        context,
                                                        "Model downloaded and ready",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                },
                                                onFailure = { error ->
                                                    downloadError = error.message ?: "The download failed."
                                                }
                                            )
                                        }
                                    )
                                }
                            },
                            enabled = downloadUrl.isNotBlank()
                        )
                    }
                    if (downloadError != null) {
                        Spacer(Modifier.height(Spacing.xs))
                    }
                    Spacer(Modifier.height(Spacing.md))
                    DangerButton(
                        text = "Clear model cache",
                        onClick = { clearLocalModelStorage() },
                        enabled = cachedSize > 0L
                    )
                }
            }
        }
    }
}

/** Best-effort file name for a SAF uri or a plain path. */
private fun modelName(context: android.content.Context, uriOrPath: String): String {
    if (uriOrPath.isBlank()) return "No model selected"
    if (!uriOrPath.startsWith("content://")) {
        return uriOrPath.substringAfterLast('/').ifBlank { uriOrPath }
    }
    return try {
        val cursor = context.contentResolver.query(
            Uri.parse(uriOrPath),
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) it.getString(index) else "Selected model"
            } else "Selected model"
        } ?: "Selected model"
    } catch (e: Exception) {
        "Selected model"
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}
