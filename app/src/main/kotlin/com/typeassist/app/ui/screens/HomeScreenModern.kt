package com.typeassist.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.typeassist.app.BuildConfig
import com.typeassist.app.MainActivity
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.model.GitHubRelease
import com.typeassist.app.ui.components.PageHeading
import com.typeassist.app.ui.components.SectionHeading
import com.typeassist.app.ui.components.SurfacePanel
import com.typeassist.app.ui.components.TypingAnimationPreview

@Composable
fun HomeScreen(
    config: AppConfig,
    context: Context,
    updateInfo: GitHubRelease?,
    onToggle: (Boolean) -> Unit,
    onNavigate: (String) -> Unit
) {
    val activity = context as MainActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(activity.isAccessibilityEnabled()) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showTroubleshootDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = activity.isAccessibilityEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showTroubleshootDialog) {
        AlertDialog(
            onDismissRequest = { showTroubleshootDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text("Refresh the service?") },
            text = {
                Text("Android may occasionally pause an accessibility service. Turn Prompt AI off and on again in Accessibility settings if shortcuts stop responding.")
            },
            confirmButton = {
                Button(onClick = {
                    showTroubleshootDialog = false
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { showTroubleshootDialog = false }) { Text("Not now") }
            }
        )
    }

    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
            title = { Text("Set up your AI provider") },
            text = {
                Text("Add a provider in Settings to use cloud AI. Offline tools such as snippets and local models can still work without a cloud key.")
            },
            confirmButton = {
                Button(onClick = {
                    showApiKeyDialog = false
                    onNavigate("settings:1")
                }) { Text("Open provider settings") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showApiKeyDialog = false
                    onToggle(true)
                }) { Text("Use offline tools") }
            }
        )
    }

    val providerName = when (config.provider) {
        "gemini" -> "Google Gemini"
        "cloudflare" -> "Cloudflare Workers AI"
        "custom" -> "OpenAI-compatible API"
        "local" -> "On-device model"
        else -> config.provider.replaceFirstChar { it.uppercase() }
    }
    val activeModel = when (config.provider) {
        "custom" -> config.customApiConfig.model
        "cloudflare" -> config.cloudflareConfig.model
        "local" -> config.localLlmConfig.modelPath.substringAfterLast('/').ifBlank { "No model selected" }
        else -> config.model
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "PROMPT AI",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.8.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Write with confidence.",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        )
                        Text(
                            "Your writing workspace",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showTroubleshootDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Service help")
                    }
                    IconButton(onClick = { onNavigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Open settings")
                    }
                }
            }

            item {
                ServiceHeroCard(
                    enabled = config.isAppEnabled,
                    hasPermission = hasPermission,
                    provider = providerName,
                    model = activeModel,
                    onToggle = { newState ->
                        if (!newState) {
                            onToggle(false)
                        } else if (!activity.isAccessibilityEnabled()) {
                            Toast.makeText(context, "Enable Prompt AI Accessibility Service first.", Toast.LENGTH_SHORT).show()
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } else {
                            val localReady = config.provider == "local" && config.localLlmConfig.modelPath.isNotBlank()
                            val customReady = config.provider == "custom"
                            val needsApiKey = !localReady && !customReady && config.apiKey.isBlank()
                            if (needsApiKey) showApiKeyDialog = true else onToggle(true)
                        }
                    }
                )
            }

            if (!hasPermission) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigate("permissions") },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Column(Modifier.weight(1f)) {
                                Text("One step left", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Spacer(Modifier.height(3.dp))
                                Text("Enable Accessibility so Prompt AI can work inside text fields.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Text("Fix", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }

            if (updateInfo != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.htmlUrl)))
                        },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Column(Modifier.weight(1f)) {
                                Text("Update ${updateInfo.tagName} available", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Open the release notes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }

            item {
                PageHeading(
                    eyebrow = "YOUR WORKSPACE",
                    title = "Pick up where you left off",
                    description = "Your most-used writing tools, ready when you are."
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DashboardQuickAction(
                            modifier = Modifier.weight(1f),
                            title = "Commands",
                            detail = "Your AI shortcuts",
                            icon = Icons.Default.Edit,
                            onClick = { onNavigate("commands") }
                        )
                        DashboardQuickAction(
                            modifier = Modifier.weight(1f),
                            title = "Snippets",
                            detail = "Reusable text",
                            icon = Icons.Default.Favorite,
                            onClick = { onNavigate("snippets") }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DashboardQuickAction(
                            modifier = Modifier.weight(1f),
                            title = "History",
                            detail = "Recent changes",
                            icon = Icons.Default.History,
                            onClick = { onNavigate("history") }
                        )
                        DashboardQuickAction(
                            modifier = Modifier.weight(1f),
                            title = "Command gallery",
                            detail = "Browse ideas",
                            icon = Icons.Default.AutoAwesome,
                            onClick = { onNavigate("library") }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DashboardQuickAction(
                            modifier = Modifier.weight(1f),
                            title = "Backup",
                            detail = "Export settings",
                            icon = Icons.Default.Code,
                            onClick = { onNavigate("json") }
                        )
                        DashboardQuickAction(
                            modifier = Modifier.weight(1f),
                            title = "Test lab",
                            detail = "Try commands",
                            icon = Icons.Default.Science,
                            onClick = { onNavigate("test") }
                        )
                    }
                }
            }

            item {
                SurfacePanel {
                    SectionHeading(
                        title = "Start with a shortcut",
                        subtitle = "Type one into any text field to begin."
                    )
                    Spacer(Modifier.height(12.dp))
                    HomeCommandExample(".ta", "Ask AI")
                    HomeCommandExample(".g", "Fix grammar")
                    HomeCommandExample(".tr", "Translate to English")
                    HomeCommandExample(".polite", "Make it professional")
                    Spacer(Modifier.height(12.dp))
                    TextButtonRow("Explore the guide") { onNavigate("guide") }
                }
            }

            item {
                SurfacePanel {
                    SectionHeading(
                        title = "See it in action",
                        subtitle = "A shortcut can turn a rough thought into clear writing."
                    )
                    Spacer(Modifier.height(14.dp))
                    TypingAnimationPreview()
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onNavigate("did_you_know") },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Column(Modifier.weight(1f)) {
                            Text("Discover a hidden feature", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text("Tips and examples for getting more from Prompt AI.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Text("Open", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            item { DeveloperCreditSection() }
            item {
                Text(
                    "Prompt AI ${BuildConfig.VERSION_NAME}",
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ServiceHeroCard(
    enabled: Boolean,
    hasPermission: Boolean,
    provider: String,
    model: String,
    onToggle: (Boolean) -> Unit
) {
    val shape = MaterialTheme.shapes.extraLarge
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF312C78), Color(0xFF5149C9), Color(0xFF13786F))
                )
            )
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "WRITING ASSISTANT",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (enabled) "Ready when you type." else "Your assistant is paused.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF44BDA7),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.28f),
                    uncheckedBorderColor = Color.White.copy(alpha = 0.6f)
                )
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (enabled) "ACTIVE" else "PAUSED",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.17f), MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Text(
                "$provider  ·  ${model.ifBlank { "No model selected" }}",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
        if (!hasPermission) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Accessibility permission is needed to process text.",
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun DashboardQuickAction(
    modifier: Modifier,
    title: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HomeCommandExample(trigger: String, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            trigger,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TextButtonRow(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(label)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}
