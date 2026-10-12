package com.typeassist.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.typeassist.app.BuildConfig
import com.typeassist.app.MainActivity
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.model.GitHubRelease
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.components.AppCard
import com.typeassist.app.ui.components.AppDialog
import com.typeassist.app.ui.components.AppMark
import com.typeassist.app.ui.components.AppScaffold
import com.typeassist.app.ui.components.AppTopBar
import com.typeassist.app.ui.components.CardFact
import com.typeassist.app.ui.components.CardTone
import com.typeassist.app.ui.components.GhostButton
import com.typeassist.app.ui.components.GroupCard
import com.typeassist.app.ui.components.MonoChip
import com.typeassist.app.ui.components.NavRow
import com.typeassist.app.ui.components.NoticeCard
import com.typeassist.app.ui.components.NoticeTone
import com.typeassist.app.ui.components.RowDivider
import com.typeassist.app.ui.components.SectionHeader
import com.typeassist.app.ui.components.StatusPill
import com.typeassist.app.ui.components.StatusTone
import com.typeassist.app.ui.components.TriggerDemo

/**
 * ============================== HOME ==============================
 *
 * Answers three questions, in this order: is the assistant running, what is it running on, and what
 * can I do next. No hero banner, no activity feed, no statistics. When everything is configured the
 * screen is short on purpose; the teaching card only appears while there is something to teach.
 */
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
    var showProviderDialog by remember { mutableStateOf(false) }
    var showServiceHelp by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = activity.isAccessibilityEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
        "local" -> config.localLlmConfig.modelPath.substringAfterLast('/')
        else -> config.model
    }
    val providerConfigured = when (config.provider) {
        "gemini" -> config.apiKey.isNotBlank()
        "cloudflare" -> config.cloudflareConfig.apiToken.isNotBlank()
        "custom" -> config.customApiConfig.baseUrl.isNotBlank()
        "local" -> config.localLlmConfig.modelPath.isNotBlank()
        else -> false
    }
    val triggers = config.triggers.orEmpty().take(4)
    val showTeachingCard = !hasPermission || !config.isAppEnabled || !providerConfigured

    if (showProviderDialog) {
        AppDialog(
            title = "Set up a provider",
            message = "Add a provider in Settings to use cloud AI. Offline tools such as snippets, " +
                "utilities and local models work without one.",
            confirmLabel = "Open provider settings",
            onConfirm = {
                showProviderDialog = false
                onNavigate("settings/provider")
            },
            dismissLabel = "Use offline tools",
            onDismiss = {
                showProviderDialog = false
                onToggle(true)
            }
        )
    }

    if (showServiceHelp) {
        AppDialog(
            title = "Refresh the service?",
            message = "Android occasionally pauses an accessibility service. Turn Prompt AI off and " +
                "on again in Accessibility settings if shortcuts stop responding.",
            confirmLabel = "Open settings",
            onConfirm = {
                showServiceHelp = false
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            dismissLabel = "Not now",
            onDismiss = { showServiceHelp = false }
        )
    }

    AppScaffold(
        topBar = {
            AppTopBar(title = "Prompt AI", subtitle = "Writing tools in every text field")
        }
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
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                AssistantPanel(
                    enabled = config.isAppEnabled,
                    hasPermission = hasPermission,
                    providerConfigured = providerConfigured,
                    provider = providerName,
                    model = activeModel,
                    onToggle = { newState ->
                        if (!newState) {
                            onToggle(false)
                        } else if (!activity.isAccessibilityEnabled()) {
                            Toast.makeText(
                                context,
                                "Enable the Prompt AI accessibility service first.",
                                Toast.LENGTH_SHORT
                            ).show()
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } else if (config.provider != "local" && config.provider != "custom" && config.apiKey.isBlank()) {
                            showProviderDialog = true
                        } else {
                            onToggle(true)
                        }
                    },
                    onOpenProvider = { onNavigate("settings/provider") },
                    onServiceHelp = { showServiceHelp = true }
                )
            }

            if (!hasPermission) {
                item {
                    NoticeCard(
                        title = "Accessibility is off",
                        message = "Without it, Prompt AI cannot read or replace the text you are " +
                            "writing. This is the one permission the app needs.",
                        tone = NoticeTone.Warning,
                        icon = Icons.Default.Accessibility,
                        actionLabel = "Enable it",
                        onAction = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    )
                }
            }

            updateInfo?.let { release ->
                item {
                    NoticeCard(
                        title = "Version ${release.tagName} is available",
                        message = "Open the release notes to see what changed.",
                        tone = NoticeTone.Info,
                        icon = Icons.Default.Refresh,
                        actionLabel = "View release",
                        onAction = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
                        }
                    )
                }
            }

            if (showTeachingCard) {
                item {
                    SectionHeader(
                        title = "How it works",
                        description = "Finish what you are typing with a trigger."
                    )
                }
                item { TriggerDemo() }
            }

            if (triggers.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Your shortcuts",
                        description = "Type a trigger into any text field to run it."
                    )
                }
                item {
                    GroupCard {
                        triggers.forEachIndexed { index, trigger ->
                            TriggerSummary(
                                trigger = trigger.pattern,
                                prompt = trigger.prompt,
                                onClick = { onNavigate("commands") }
                            )
                            if (index != triggers.lastIndex) RowDivider()
                        }
                        RowDivider(insetStart = false)
                        NavRow(
                            title = "Manage commands",
                            description = "Edit triggers, inline commands and the gallery.",
                            icon = Icons.Default.Bolt,
                            onClick = { onNavigate("commands") }
                        )
                    }
                }
            }

            item {
                SectionHeader(title = "Tools")
            }
            item {
                GroupCard {
                    NavRow(
                        title = "Command gallery",
                        description = "Ready-made prompts you can add in one tap.",
                        icon = Icons.Default.Bolt,
                        onClick = { onNavigate("library") }
                    )
                    RowDivider()
                    NavRow(
                        title = "Test lab",
                        description = "A text field wired to the service, with presets.",
                        icon = Icons.Default.Science,
                        onClick = { onNavigate("test") }
                    )
                    RowDivider()
                    NavRow(
                        title = "Backup & restore",
                        description = "Export or import your settings and snippets.",
                        icon = Icons.Default.Code,
                        onClick = { onNavigate("backup") }
                    )
                    RowDivider()
                    NavRow(
                        title = "User guide",
                        description = "Every trigger, utility and shortcut explained.",
                        icon = Icons.Default.Info,
                        onClick = { onNavigate("guide") }
                    )
                    RowDivider()
                    NavRow(
                        title = "Tips & examples",
                        description = "Features you may not have discovered yet.",
                        icon = Icons.Default.Lightbulb,
                        onClick = { onNavigate("did_you_know") }
                    )
                }
            }

            item {
                FooterNote(
                    onOpenSource = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/Stlucifer15t/TypeAssist")
                            )
                        )
                    }
                )
            }
        }
    }
}

/**
 * The one prominent object on the screen: what is running, on what, and the switch that controls it.
 */
@Composable
private fun AssistantPanel(
    enabled: Boolean,
    hasPermission: Boolean,
    providerConfigured: Boolean,
    provider: String,
    model: String,
    onToggle: (Boolean) -> Unit,
    onOpenProvider: () -> Unit,
    onServiceHelp: () -> Unit
) {
    AppCard(contentPadding = PaddingValues(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppMark(size = 34.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (enabled) "Assistant is on" else "Assistant is off",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.xxs))
                StatusPill(
                    text = when {
                        !hasPermission -> "Needs accessibility"
                        !enabled -> "Paused"
                        !providerConfigured -> "No provider set"
                        else -> "Ready"
                    },
                    tone = when {
                        !hasPermission -> StatusTone.Warning
                        !enabled -> StatusTone.Idle
                        !providerConfigured -> StatusTone.Warning
                        else -> StatusTone.Active
                    }
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }

        Spacer(Modifier.height(Spacing.lg))
        RowDivider(insetStart = false)
        Spacer(Modifier.height(Spacing.md))

        CardFact(
            label = "Provider",
            value = if (providerConfigured) provider else "$provider · not configured"
        )
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Model",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            if (model.isBlank()) {
                Text(
                    text = "No model selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                MonoChip(text = model, tone = CardTone.Muted)
            }
        }

        Spacer(Modifier.height(Spacing.md))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            GhostButton(text = if (providerConfigured) "Change provider" else "Set up provider", onClick = onOpenProvider)
            if (enabled && hasPermission) {
                GhostButton(text = "Service help", onClick = onServiceHelp)
            }
        }
    }
}

/** One shortcut: the trigger in monospace, then what it does. */
@Composable
private fun TriggerSummary(
    trigger: String,
    prompt: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = Spacing.rowStart, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MonoChip(text = trigger, tone = CardTone.Accent)
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Version and the source link, stated once and quietly. */
@Composable
private fun FooterNote(onOpenSource: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Prompt AI ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Open source",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onOpenSource() }
            )
        }
    }
}
