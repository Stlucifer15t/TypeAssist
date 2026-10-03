package com.typeassist.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.isReasoningModel

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
                        "Prompt AI automatically removes it from the final output.",
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
