package com.typeassist.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.InlineCommand
import com.typeassist.app.data.Trigger
import com.typeassist.app.ui.MonoFamily
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.components.AppScaffold
import com.typeassist.app.ui.components.AppSearchField
import com.typeassist.app.ui.components.AppSheet
import com.typeassist.app.ui.components.AppTextField
import com.typeassist.app.ui.components.AppTopBar
import com.typeassist.app.ui.components.ConfirmDialog
import com.typeassist.app.ui.components.EmptyState
import com.typeassist.app.ui.components.GhostButton
import com.typeassist.app.ui.components.GroupCard
import com.typeassist.app.ui.components.NoticeCard
import com.typeassist.app.ui.components.NoticeTone
import com.typeassist.app.ui.components.PrimaryButton
import com.typeassist.app.ui.components.RowDivider
import com.typeassist.app.ui.components.SectionHeader
import com.typeassist.app.ui.components.SegmentedTabs
import com.typeassist.app.ui.components.SheetActions
import com.typeassist.app.ui.components.SheetHeader
import com.typeassist.app.ui.components.TriggerRow

/**
 * ============================== COMMANDS ==============================
 *
 * Two kinds of shortcut, one list each: standard triggers that end a sentence, and inline commands
 * that run anywhere inside one. Editing happens in a sheet, so the fields get the full width of the
 * screen and the keyboard never fights the list.
 */
@Composable
fun CommandsScreen(
    config: AppConfig,
    onSave: (AppConfig) -> Unit,
    onBack: () -> Unit,
    onNavigateLibrary: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var editorOpen by remember { mutableStateOf(false) }
    var editingPattern by remember { mutableStateOf<String?>(null) }
    var draftPattern by remember { mutableStateOf("") }
    var draftPrompt by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Trigger?>(null) }
    var pendingDeleteInline by remember { mutableStateOf<InlineCommand?>(null) }

    val triggers = config.triggers.orEmpty()
    val inlineCommands = config.inlineCommands.orEmpty()
    val query = searchQuery.trim()
    val visibleTriggers = triggers.filter {
        query.isBlank() || it.pattern.contains(query, true) || it.prompt.contains(query, true)
    }
    val visibleInline = inlineCommands.filter {
        query.isBlank() || it.pattern.contains(query, true) || it.prompt.contains(query, true)
    }
    val inlineMode = selectedTab == 1

    fun openEditor(pattern: String, prompt: String, original: String?) {
        draftPattern = pattern
        draftPrompt = prompt
        editingPattern = original
        editorOpen = true
    }

    fun commitDraft() {
        if (draftPattern.isBlank() || draftPrompt.isBlank()) return
        if (inlineMode) {
            val updated = inlineCommands.toMutableList().apply {
                editingPattern?.let { original -> removeIf { it.pattern == original } }
                removeIf { it.pattern == draftPattern }
                add(InlineCommand(draftPattern.trim(), draftPrompt.trim()))
            }
            onSave(config.copy(inlineCommands = updated))
        } else {
            val updated = triggers.toMutableList().apply {
                editingPattern?.let { original -> removeIf { it.pattern == original } }
                removeIf { it.pattern == draftPattern }
                add(Trigger(draftPattern.trim(), draftPrompt.trim()))
            }
            onSave(config.copy(triggers = updated))
        }
        editorOpen = false
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "Commands",
                onBack = onBack,
                actions = {
                    GhostButton(text = "Gallery", onClick = onNavigateLibrary)
                }
            )
        },
        floatingAction = {
            ExtendedFloatingActionButton(
                onClick = {
                    openEditor(
                        pattern = if (inlineMode) "(.ta:%)" else "@",
                        prompt = "",
                        original = null
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New command", style = MaterialTheme.typography.labelLarge) }
            )
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
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Column {
                    SegmentedTabs(
                        items = listOf("Standard", "Inline"),
                        selectedIndex = selectedTab,
                        onSelect = { selectedTab = it }
                    )
                    Spacer(Modifier.height(Spacing.md))
                    AppSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search commands"
                    )
                }
            }

            if (inlineMode) {
                item {
                    NoticeCard(
                        title = "Inline commands run mid-sentence",
                        message = "Place one anywhere in your text and the % is replaced with the " +
                            "surrounding words - handy for synonyms or a quick rewrite without " +
                            "losing context.",
                        tone = NoticeTone.Info,
                        icon = Icons.Default.Info
                    )
                }
            }

            val visibleCount = if (inlineMode) visibleInline.size else visibleTriggers.size
            item {
                SectionHeader(
                    title = if (inlineMode) "Inline commands" else "Standard commands",
                    description = when {
                        query.isNotBlank() -> "$visibleCount matching ${if (visibleCount == 1) "command" else "commands"}"
                        visibleCount == 0 -> "None yet"
                        else -> "$visibleCount ${if (visibleCount == 1) "command" else "commands"}, " +
                            if (inlineMode) "usable anywhere in a sentence" else "used at the end of your text"
                    }
                )
            }

            if (visibleCount == 0) {
                item {
                    EmptyState(
                        icon = Icons.Default.Bolt,
                        title = if (query.isBlank()) "No commands yet" else "Nothing matched",
                        description = if (query.isBlank()) {
                            "Add your own shortcut, or start from a ready-made prompt in the gallery."
                        } else {
                            "Try a different word, or clear the search."
                        },
                        actionLabel = if (query.isBlank()) "Browse the gallery" else null,
                        onAction = if (query.isBlank()) onNavigateLibrary else null
                    )
                }
            } else {
                item {
                    GroupCard {
                        if (inlineMode) {
                            visibleInline.forEachIndexed { index, command ->
                                TriggerRow(
                                    trigger = command.pattern,
                                    detail = command.prompt,
                                    inline = true,
                                    onClick = {
                                        openEditor(command.pattern, command.prompt, command.pattern)
                                    },
                                    onDelete = { pendingDeleteInline = command }
                                )
                                if (index != visibleInline.lastIndex) RowDivider()
                            }
                        } else {
                            visibleTriggers.forEachIndexed { index, trigger ->
                                TriggerRow(
                                    trigger = trigger.pattern,
                                    detail = trigger.prompt,
                                    onClick = {
                                        openEditor(trigger.pattern, trigger.prompt, trigger.pattern)
                                    },
                                    onDelete = { pendingDelete = trigger }
                                )
                                if (index != visibleTriggers.lastIndex) RowDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        AppSheet(onDismissRequest = { editorOpen = false }) {
            SheetHeader(
                title = if (editingPattern == null) "New command" else "Edit command",
                description = if (inlineMode) {
                    "Inline command: runs wherever you put it and replaces % with your text."
                } else {
                    "Standard command: runs when you finish your text with this trigger."
                },
                onClose = { editorOpen = false }
            )
            Spacer(Modifier.height(Spacing.lg))
            AppTextField(
                value = draftPattern,
                onValueChange = { draftPattern = it },
                label = if (inlineMode) "Pattern" else "Trigger",
                placeholder = if (inlineMode) "(.ta:%)" else ".ta",
                supporting = if (inlineMode) "Use % where your text should be inserted." else null,
                monospace = true
            )
            Spacer(Modifier.height(Spacing.md))
            AppTextField(
                value = draftPrompt,
                onValueChange = { draftPrompt = it },
                label = "Instruction for the AI",
                placeholder = "Rewrite this so it sounds professional",
                singleLine = false,
                minLines = 4
            )
            SheetActions {
                PrimaryButton(
                    text = "Save",
                    onClick = { commitDraft() },
                    modifier = Modifier.weight(1f),
                    enabled = draftPattern.isNotBlank() && draftPrompt.isNotBlank()
                )
                GhostButton(text = "Cancel", onClick = { editorOpen = false })
            }
        }
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = "Delete “${target.pattern}”?",
            message = "This shortcut is removed from your commands. You can add it again from the gallery.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                onSave(config.copy(triggers = triggers.toMutableList().apply { remove(target) }))
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    pendingDeleteInline?.let { target ->
        ConfirmDialog(
            title = "Delete “${target.pattern}”?",
            message = "This inline command is removed from your commands.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                onSave(
                    config.copy(
                        inlineCommands = inlineCommands.toMutableList().apply { remove(target) }
                    )
                )
                pendingDeleteInline = null
            },
            onDismiss = { pendingDeleteInline = null }
        )
    }
}

/** Kept public: the guide and the gallery both show a trigger the same way. */
@Composable
fun TriggerChipStyle(pattern: String) {
    Text(
        text = pattern,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = MonoFamily,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth()
    )
}
