package com.typeassist.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.typeassist.app.BuildConfig
import com.typeassist.app.api.ApiErrorFormatter
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.HistoryManager
import com.typeassist.app.data.ReplyStyle
import com.typeassist.app.data.repository.UpdateRepository
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.components.AppCard
import com.typeassist.app.ui.components.ChoiceRow
import com.typeassist.app.ui.components.AppScaffold
import com.typeassist.app.ui.components.AppTextField
import com.typeassist.app.ui.components.AppTopBar
import com.typeassist.app.ui.components.GroupCard
import com.typeassist.app.ui.components.MonoChip
import com.typeassist.app.ui.components.NavRow
import com.typeassist.app.ui.components.NoticeCard
import com.typeassist.app.ui.components.NoticeTone
import com.typeassist.app.ui.components.RowDivider
import com.typeassist.app.ui.components.ScreenIntro
import com.typeassist.app.ui.components.SectionHeader
import com.typeassist.app.ui.components.SettingRow
import com.typeassist.app.ui.components.SmallButton
import com.typeassist.app.ui.components.SwitchRow
import kotlinx.coroutines.launch

/**
 * ============================== BEHAVIOUR ==============================
 *
 * How the assistant behaves around your text: what it does after a reply, how triggers are
 * detected, what is kept in history, how long it waits, and - under a clearly separated heading -
 * what it is allowed to read from the screen. The privacy switches are stated in plain language
 * because they are the ones that matter.
 */
@Composable
fun BehaviourSettingsScreen(
    config: AppConfig,
    onSave: (AppConfig) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showResultChip by remember(config.showResultChip) { mutableStateOf(config.showResultChip) }
    var streamResponses by remember(config.streamResponses) { mutableStateOf(config.streamResponses) }
    var allowTriggerAnywhere by remember(config.allowTriggerAnywhere) { mutableStateOf(config.allowTriggerAnywhere) }
    var ignorePrecedingWhitespace by remember(config.ignorePrecedingWhitespace) {
        mutableStateOf(config.ignorePrecedingWhitespace)
    }
    var globalTriggerPattern by remember(config.globalTriggerPattern) {
        mutableStateOf(config.globalTriggerPattern)
    }
    var historyEnabled by remember(config.isHistoryEnabled) { mutableStateOf(config.isHistoryEnabled) }
    var previewEnabled by remember(config.enablePreviewDialog) { mutableStateOf(config.enablePreviewDialog) }
    var timeout by remember(config.apiTimeoutSeconds) { mutableStateOf(config.apiTimeoutSeconds.toFloat()) }
    var retentionMinutes by remember(config.historyRetentionMinutes) {
        mutableStateOf(HistoryManager.sanitizeRetentionMinutes(config.historyRetentionMinutes))
    }
    var replyTone by remember(config.replyTone) { mutableStateOf(ReplyStyle.sanitizeTone(config.replyTone)) }
    var replyLength by remember(config.replyLength) {
        mutableStateOf(ReplyStyle.sanitizeLength(config.replyLength))
    }
    var customRetention by remember { mutableStateOf("") }

    var screenContextEnabled by remember(config.screenContextEnabled) {
        mutableStateOf(config.screenContextEnabled)
    }
    var screenContextChatLabels by remember(config.screenContextChatLabels) {
        mutableStateOf(config.screenContextChatLabels)
    }
    var screenImageEnabled by remember(config.screenImageEnabled) {
        mutableStateOf(config.screenImageEnabled)
    }
    var blockedPackages by remember(config.screenContextBlockedPackages) {
        mutableStateOf(config.screenContextBlockedPackages.orEmpty().toList())
    }
    var newPackage by remember { mutableStateOf("") }
    var checkingForUpdate by remember { mutableStateOf(false) }

    AppScaffold(
        topBar = { AppTopBar(title = "Behaviour", onBack = onBack) }
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
            item { ScreenIntro("These settings change how Prompt AI works inside other apps, not how it looks.") }

            item { SectionHeader(title = "After a reply") }
            item {
                GroupCard {
                    SwitchRow(
                        title = "Show accept / reject chip",
                        description = "A small Accept · Reject · Retry chip appears for a few seconds " +
                            "after the text is replaced.",
                        checked = showResultChip,
                        onCheckedChange = {
                            showResultChip = it
                            onSave(config.copy(showResultChip = it))
                        }
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Stream responses",
                        description = "Write the answer into the field as it arrives. Gemini and " +
                            "OpenAI-compatible providers stream; Cloudflare and local models reply in one piece.",
                        checked = streamResponses,
                        onCheckedChange = {
                            streamResponses = it
                            onSave(config.copy(streamResponses = it))
                        }
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Preview longer responses",
                        description = "Show the answer in a card first, so you can read it before it is inserted.",
                        checked = previewEnabled,
                        onCheckedChange = {
                            previewEnabled = it
                            onSave(config.copy(enablePreviewDialog = it))
                        }
                    )
                }
            }

            item { SectionHeader(title = "Triggers & text") }
            item {
                GroupCard {
                    SwitchRow(
                        title = "Allow triggers anywhere",
                        description = "Run a trigger even when more text follows it.",
                        checked = allowTriggerAnywhere,
                        onCheckedChange = {
                            allowTriggerAnywhere = it
                            onSave(config.copy(allowTriggerAnywhere = it))
                        }
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Ignore the preceding space",
                        description = "Allow commands directly after a word, like “hello.ta”.",
                        checked = ignorePrecedingWhitespace,
                        onCheckedChange = {
                            ignorePrecedingWhitespace = it
                            onSave(config.copy(ignorePrecedingWhitespace = it))
                        }
                    )
                }
            }
            item {
                AppCard {
                    AppTextField(
                        value = globalTriggerPattern,
                        onValueChange = {
                            globalTriggerPattern = it
                            onSave(config.copy(globalTriggerPattern = it))
                        },
                        label = "Global rewrite pattern",
                        supporting = "Use % where your instruction goes. Default: ...%...",
                        monospace = true
                    )
                }
            }

            item { SectionHeader(title = "Drafted replies") }
            item {
                GroupCard {
                    SettingRow(
                        title = "Tone",
                        description = ReplyStyle.toneName(replyTone),
                        icon = Icons.Default.Chat
                    ) {
                        MonoChip(text = ".reply")
                    }
                    ReplyStyle.TONES.forEach { option ->
                        RowDivider()
                        ChoiceRow(
                            title = option.name,
                            description = option.description,
                            selected = replyTone == option.id,
                            useRadio = false,
                            onSelect = {
                                replyTone = option.id
                                onSave(config.copy(replyTone = option.id))
                            }
                        )
                    }
                }
            }
            item {
                GroupCard {
                    SettingRow(
                        title = "Length",
                        description = ReplyStyle.lengthName(replyLength)
                    )
                    ReplyStyle.LENGTHS.forEach { option ->
                        RowDivider()
                        ChoiceRow(
                            title = option.name,
                            description = option.description,
                            selected = replyLength == option.id,
                            useRadio = false,
                            onSelect = {
                                replyLength = option.id
                                onSave(config.copy(replyLength = option.id))
                            }
                        )
                    }
                }
            }
            item {
                NoticeCard(
                    title = "Used by .reply and .sum",
                    message = "The tone and length are sent as instructions with the request, together " +
                        "with the screen text. They apply to plain .reply, and the length also caps " +
                        "how long a summary may be.",
                    tone = NoticeTone.Info,
                    icon = Icons.Default.Info
                )
            }

            item { SectionHeader(title = "History") }
            item {
                GroupCard {
                    SwitchRow(
                        title = "Keep processed text",
                        description = "Stores the text a command replaced, so you can recover or copy it.",
                        checked = historyEnabled,
                        onCheckedChange = {
                            historyEnabled = it
                            onSave(config.copy(isHistoryEnabled = it))
                        }
                    )
                    if (historyEnabled) {
                        RowDivider()
                        SettingRow(
                            title = "Keep for",
                            description = if (retentionMinutes == HistoryManager.DEFAULT_RETENTION_MINUTES) {
                                "The default. Long enough to fix a mistake made earlier in the day."
                            } else {
                                "Older entries disappear on their own."
                            }
                        ) {
                            Text(
                                text = HistoryManager.describeRetention(retentionMinutes),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            if (historyEnabled) {
                item {
                    GroupCard {
                        HistoryManager.RETENTION_CHOICES.forEach { minutes ->
                            ChoiceRow(
                                title = HistoryManager.describeRetention(minutes),
                                selected = retentionMinutes == minutes,
                                useRadio = false,
                                onSelect = {
                                    retentionMinutes = minutes
                                    onSave(config.copy(historyRetentionMinutes = minutes))
                                }
                            )
                            RowDivider()
                        }
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.card, vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppTextField(
                                value = customRetention,
                                onValueChange = { typed ->
                                    customRetention = typed.filter { it.isDigit() }.take(4)
                                },
                                label = "Custom (minutes)",
                                placeholder = "90",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            SmallButton(
                                text = "Apply",
                                enabled = customRetention.toIntOrNull()?.let {
                                    HistoryManager.sanitizeRetentionMinutes(it) != retentionMinutes
                                } == true,
                                onClick = {
                                    val minutes = HistoryManager.sanitizeRetentionMinutes(
                                        customRetention.toIntOrNull() ?: 0
                                    )
                                    retentionMinutes = minutes
                                    customRetention = ""
                                    onSave(config.copy(historyRetentionMinutes = minutes))
                                }
                            )
                        }
                    }
                }
            }
            item {
                NoticeCard(
                    title = "History stays on this device",
                    message = "Entries live in memory only, and the list is cleared when the service " +
                        "restarts. Nothing is written to storage, and nothing is sent anywhere.",
                    tone = NoticeTone.Info,
                    icon = Icons.Default.Schedule
                )
            }

            item { SectionHeader(title = "Timeouts") }
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Network timeout",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "How long to wait for a provider before giving up.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${timeout.toInt()}s",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Slider(
                        value = timeout,
                        onValueChange = { timeout = it },
                        onValueChangeFinished = {
                            onSave(config.copy(apiTimeoutSeconds = timeout.toLong()))
                        },
                        valueRange = 10f..120f,
                        steps = 21
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Screen context",
                    description = "Used by .reply, .sum and @screen questions. Off by default."
                )
            }
            item {
                GroupCard {
                    SwitchRow(
                        title = "Allow screen context",
                        description = "Nothing is read until you use one of those commands.",
                        checked = screenContextEnabled,
                        onCheckedChange = {
                            screenContextEnabled = it
                            onSave(config.copy(screenContextEnabled = it))
                        }
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Label chat sides",
                        description = "In conversation screens, mark blocks as “Them:” or “Me:” by " +
                            "their position. Used to match your writing style.",
                        checked = screenContextChatLabels,
                        enabled = screenContextEnabled,
                        onCheckedChange = {
                            screenContextChatLabels = it
                            onSave(config.copy(screenContextChatLabels = it))
                        }
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Send a screenshot with .reply",
                        description = "Captures the whole screen so the model can read a photo a " +
                            "contact sent. Needs Android 11+ and a Gemini or OpenAI-compatible " +
                            "provider. Off by default.",
                        checked = screenImageEnabled,
                        enabled = screenContextEnabled,
                        onCheckedChange = {
                            screenImageEnabled = it
                            onSave(config.copy(screenImageEnabled = it))
                        }
                    )
                }
            }
            item {
                NoticeCard(
                    title = "What screen context means",
                    message = "When you use .reply, .sum or @screen, the text visible on your screen " +
                        "is sent to the provider you chose so it can answer. Prompt AI never reads " +
                        "the screen in the background, never stores what it read, and always skips " +
                        "the apps listed below.",
                    tone = NoticeTone.Info
                )
            }

            item { SectionHeader(title = "Never read in these apps") }
            item {
                AppCard {
                    if (blockedPackages.isEmpty()) {
                        Text(
                            text = "No apps blocked yet. Banking, payment and password-manager " +
                                "packages are blocked by default.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        blockedPackages.forEach { packageName ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = packageName,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(onClick = {
                                    val updated = blockedPackages - packageName
                                    blockedPackages = updated
                                    onSave(config.copy(screenContextBlockedPackages = updated.toMutableList()))
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove $packageName",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppTextField(
                            value = newPackage,
                            onValueChange = { newPackage = it },
                            modifier = Modifier.weight(1f),
                            label = "App package name",
                            placeholder = "com.example.bank",
                            monospace = true
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        SmallButton(
                            text = "Add",
                            primary = true,
                            enabled = newPackage.isNotBlank() && newPackage.trim() !in blockedPackages,
                            onClick = {
                                val updated = blockedPackages + newPackage.trim()
                                blockedPackages = updated
                                newPackage = ""
                                onSave(config.copy(screenContextBlockedPackages = updated.toMutableList()))
                            }
                        )
                    }
                }
            }

            item { SectionHeader(title = "Service") }
            item {
                GroupCard {
                    NavRow(
                        title = "Check permissions",
                        description = "Accessibility, notifications, battery settings and Xiaomi autostart.",
                        icon = Icons.Default.Accessibility,
                        onClick = { onNavigate("permissions") }
                    )
                }
            }

            if (BuildConfig.SHOW_UPDATES) {
                item { SectionHeader(title = "App updates") }
                item {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Prompt AI ${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Check the release channel for a newer build.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            SmallButton(
                                text = "Check",
                                loading = checkingForUpdate,
                                enabled = !checkingForUpdate,
                                onClick = {
                                    checkingForUpdate = true
                                    scope.launch {
                                        val result = UpdateRepository().checkForUpdate()
                                        checkingForUpdate = false
                                        result.onSuccess { release ->
                                            if (release != null) {
                                                Toast.makeText(
                                                    context,
                                                    "New release: ${release.tagName}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
                                                )
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "You are on the latest version.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }.onFailure {
                                            Toast.makeText(
                                                context,
                                                "Could not check updates: ${ApiErrorFormatter.explain(it)}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
