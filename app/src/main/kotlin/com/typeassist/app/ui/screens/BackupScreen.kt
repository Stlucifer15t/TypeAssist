package com.typeassist.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.google.gson.GsonBuilder
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.ConfigMigrations
import com.typeassist.app.data.LoadingIndicatorStyle
import com.typeassist.app.ui.Sizes
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.components.AppCard
import com.typeassist.app.ui.components.AppDialog
import com.typeassist.app.ui.components.AppScaffold
import com.typeassist.app.ui.components.AppTextField
import com.typeassist.app.ui.components.AppTopBar
import com.typeassist.app.ui.components.GroupCard
import com.typeassist.app.ui.components.InlineError
import com.typeassist.app.ui.components.InlineSuccess
import com.typeassist.app.ui.components.NoticeCard
import com.typeassist.app.ui.components.NoticeTone
import com.typeassist.app.ui.components.PrimaryButton
import com.typeassist.app.ui.components.ScreenIntro
import com.typeassist.app.ui.components.SecondaryButton
import com.typeassist.app.ui.components.SectionHeader
import com.typeassist.app.ui.components.SmallButton
import com.typeassist.app.ui.components.SwitchRow
import com.typeassist.app.utils.BackupManager
import kotlinx.coroutines.launch

/**
 * ============================== BACKUP & RESTORE ==============================
 *
 * One file, everything inside: settings, API profiles, commands, snippets and history. The screen
 * is a single column of decisions, in the order people actually make them - save a copy, then (only
 * if needed) restore one. The raw JSON editor sits at the bottom, clearly marked as advanced,
 * because it edits the same data without any of the safety nets.
 */
@Composable
fun BackupScreen(
    config: AppConfig,
    onSave: (AppConfig) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gson = remember { GsonBuilder().setPrettyPrinting().create() }

    var showBackupSheet by remember { mutableStateOf(false) }
    var showRestoreSheet by remember { mutableStateOf(false) }
    var useEncryption by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    var jsonText by remember { mutableStateOf(gson.toJson(config)) }
    var jsonMessage by remember { mutableStateOf<String?>(null) }
    var jsonMessageIsError by remember { mutableStateOf(false) }
    var jsonEditorOpen by remember { mutableStateOf(false) }

    val createLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = BackupManager.exportBackup(config, backupPassword.takeIf { useEncryption })
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IllegalStateException("The chosen location could not be written to.")
                statusMessage = "Backup saved. Keep the file somewhere you can reach it later."
                statusIsError = false
            } catch (e: Exception) {
                statusMessage = "Export failed: ${e.message}"
                statusIsError = true
            }
        }
    }

    fun restore(uri: Uri, password: String?) {
        scope.launch {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val (restored, needsPassword) = BackupManager.importBackup(stream, password)
                    when {
                        needsPassword -> {
                            statusMessage = "That backup is encrypted. Enter the password to restore it."
                            statusIsError = true
                        }
                        restored != null -> {
                            onSave(restored)
                            jsonText = gson.toJson(restored)
                            statusMessage = "Restore complete. Settings, commands and snippets were replaced."
                            statusIsError = false
                        }
                        else -> {
                            statusMessage = "The file could not be read as a Prompt AI backup."
                            statusIsError = true
                        }
                    }
                } ?: run {
                    statusMessage = "The file could not be opened."
                    statusIsError = true
                }
            } catch (e: SecurityException) {
                statusMessage = e.message ?: "Incorrect password or corrupted file."
                statusIsError = true
            } catch (e: Exception) {
                statusMessage = "Restore failed: ${e.message}"
                statusIsError = true
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val encrypted = try {
            context.contentResolver.openInputStream(uri)?.use { BackupManager.isEncrypted(it) } ?: false
        } catch (e: Exception) {
            false
        }
        if (encrypted) {
            pendingRestoreUri = uri
            restorePassword = ""
            showRestoreSheet = true
        } else {
            restore(uri, null)
        }
    }

    AppScaffold(
        topBar = { AppTopBar(title = "Backup & restore", onBack = onBack) }
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
                    "Settings, provider profiles, API keys, commands, snippets and history travel " +
                        "together in one file with the .tabak extension."
                )
            }

            if (statusMessage != null) {
                item {
                    if (statusIsError) {
                        InlineError(statusMessage!!)
                    } else {
                        InlineSuccess(statusMessage!!)
                    }
                }
            }

            item { SectionHeader(title = "Save a backup") }
            item {
                AppCard {
                    Text(
                        text = "Write everything to a single file you choose. Useful before switching " +
                            "device, reinstalling, or experimenting with settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.md))
                    PrimaryButton(
                        text = "Create backup file",
                        onClick = {
                            backupPassword = ""
                            useEncryption = false
                            showBackupSheet = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.FileDownload
                    )
                }
            }

            item { SectionHeader(title = "Restore a backup") }
            item {
                AppCard {
                    Text(
                        text = "Restoring replaces the current settings, commands, snippets and " +
                            "history with the contents of the file. There is no undo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.md))
                    SecondaryButton(
                        text = "Choose backup file",
                        onClick = { openLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.FileUpload
                    )
                }
            }

            item {
                NoticeCard(
                    title = "The file contains your API keys",
                    message = "Anyone with the file can read your provider keys and, if history is " +
                        "kept, the text you have worked on. Encrypt it with a password if you plan " +
                        "to store or share it.",
                    tone = NoticeTone.Warning,
                    icon = Icons.Default.Lock
                )
            }

            item { SectionHeader(title = "Advanced") }
            item {
                GroupCard {
                    SwitchRow(
                        title = "Edit settings as raw JSON",
                        description = "The same data the backup contains, editable by hand. Mistakes " +
                            "here can break configuration without warning.",
                        checked = jsonEditorOpen,
                        onCheckedChange = { jsonEditorOpen = it }
                    )
                }
            }

            if (jsonEditorOpen) {
                item {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DataObject,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(Sizes.icon)
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                text = "Current configuration",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            SmallButton(text = "Reset view", onClick = {
                                jsonText = gson.toJson(config)
                                jsonMessage = null
                            })
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        AppTextField(
                            value = jsonText,
                            onValueChange = {
                                jsonText = it
                                jsonMessage = null
                            },
                            placeholder = "{ }",
                            monospace = true,
                            singleLine = false,
                            minLines = 10,
                            supporting = "Keys and tokens appear here in plain text."
                        )
                        if (jsonMessage != null) {
                            Spacer(Modifier.height(Spacing.sm))
                            if (jsonMessageIsError) {
                                InlineError(jsonMessage!!)
                            } else {
                                InlineSuccess(jsonMessage!!)
                            }
                        }
                        Spacer(Modifier.height(Spacing.md))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            SecondaryButton(
                                text = "Copy JSON",
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Prompt AI configuration", jsonText))
                                    jsonMessage = "Configuration copied to the clipboard."
                                    jsonMessageIsError = false
                                },
                                modifier = Modifier.weight(1f)
                            )
                            PrimaryButton(
                                text = "Apply",
                                onClick = {
                                    try {
                                        val parsed = ConfigMigrations.apply(
                                            LoadingIndicatorStyle.sanitize(gson.fromJson(jsonText, AppConfig::class.java))
                                        )
                                        onSave(parsed)
                                        jsonMessage = "Configuration applied."
                                        jsonMessageIsError = false
                                    } catch (e: Exception) {
                                        jsonMessage = "That JSON could not be parsed: ${e.message}"
                                        jsonMessageIsError = true
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBackupSheet) {
        AppDialog(
            title = "Create backup",
            message = "The file holds your settings, provider profiles, API keys, commands, " +
                "snippets and history.",
            onDismiss = { showBackupSheet = false },
            confirmLabel = "Choose location",
            onConfirm = {
                if (useEncryption && backupPassword.isBlank()) {
                    statusMessage = "Enter a password, or turn encryption off."
                    statusIsError = true
                    showBackupSheet = false
                } else {
                    showBackupSheet = false
                    createLauncher.launch(BackupManager.generateFileName())
                }
            },
            dismissLabel = "Cancel",
            confirmEnabled = !useEncryption || backupPassword.isNotBlank(),
            icon = Icons.Default.FileDownload
        ) {
            SwitchRow(
                title = "Encrypt with a password",
                description = "AES-256 encryption. Without the password the file cannot be opened again.",
                checked = useEncryption,
                onCheckedChange = {
                    useEncryption = it
                    if (!it) backupPassword = ""
                }
            )
            if (useEncryption) {
                PasswordField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    label = "Backup password",
                    visible = passwordVisible,
                    onToggleVisibility = { passwordVisible = !passwordVisible }
                )
                Text(
                    text = "There is no recovery if this password is lost.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showRestoreSheet) {
        AppDialog(
            title = "Encrypted backup",
            message = "Enter the password used when this file was created.",
            onDismiss = {
                showRestoreSheet = false
                pendingRestoreUri = null
            },
            confirmLabel = "Restore",
            onConfirm = {
                val uri = pendingRestoreUri
                showRestoreSheet = false
                pendingRestoreUri = null
                if (uri != null) restore(uri, restorePassword)
            },
            dismissLabel = "Cancel",
            confirmEnabled = restorePassword.isNotBlank(),
            icon = Icons.Default.Lock
        ) {
            PasswordField(
                value = restorePassword,
                onValueChange = { restorePassword = it },
                label = "Password",
                visible = passwordVisible,
                onToggleVisibility = { passwordVisible = !passwordVisible }
            )
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisibility: () -> Unit
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
