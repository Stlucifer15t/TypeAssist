package com.typeassist.app.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.Snippet
import com.typeassist.app.ui.MonoFamily
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.components.AppScaffold
import com.typeassist.app.ui.components.AppSearchField
import com.typeassist.app.ui.components.AppSheet
import com.typeassist.app.ui.components.AppTextField
import com.typeassist.app.ui.components.AppTopBar
import com.typeassist.app.ui.components.CardTone
import com.typeassist.app.ui.components.ConfirmDialog
import com.typeassist.app.ui.components.EmptyState
import com.typeassist.app.ui.components.GhostButton
import com.typeassist.app.ui.components.GroupCard
import com.typeassist.app.ui.components.MonoChip
import com.typeassist.app.ui.components.NoticeCard
import com.typeassist.app.ui.components.NoticeTone
import com.typeassist.app.ui.components.PrimaryButton
import com.typeassist.app.ui.components.RowDivider
import com.typeassist.app.ui.components.ScreenIntro
import com.typeassist.app.ui.components.SectionHeader
import com.typeassist.app.ui.components.SheetActions
import com.typeassist.app.ui.components.SheetHeader
import com.typeassist.app.ui.components.SmallButton

/**
 * ============================== SNIPPETS ==============================
 *
 * Saved text with a trigger. A snippet can hold several variations; the picker that appears while
 * typing lets you choose between them, which is why the editor is built around a variation list
 * rather than one big text box.
 */
@Composable
fun SnippetsScreen(
    config: AppConfig,
    onSave: (AppConfig) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var editorOpen by remember { mutableStateOf(false) }
    var editingTrigger by remember { mutableStateOf<String?>(null) }
    var draftTrigger by remember { mutableStateOf("") }
    val draftVariations = remember { mutableStateListOf<String>() }
    var pendingDelete by remember { mutableStateOf<Snippet?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortAlphabetical by remember { mutableStateOf(false) }

    val snippets = config.snippets.orEmpty()
    val visible = remember(snippets, searchQuery, sortAlphabetical) {
        val filtered = snippets.filter { snippet ->
            snippet.trigger.contains(searchQuery, ignoreCase = true) ||
                snippet.contents.any { it.contains(searchQuery, ignoreCase = true) }
        }
        if (sortAlphabetical) filtered.sortedBy { it.trigger.lowercase() } else filtered
    }

    fun openEditor(trigger: String, contents: List<String>) {
        draftTrigger = trigger
        draftVariations.clear()
        draftVariations.addAll(contents.ifEmpty { listOf("") })
        editingTrigger = trigger.ifEmpty { null }
        editorOpen = true
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "Snippets",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { sortAlphabetical = !sortAlphabetical }) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = if (sortAlphabetical) "Sort: A to Z" else "Sort: newest first",
                            tint = if (sortAlphabetical) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingAction = {
            ExtendedFloatingActionButton(
                onClick = { openEditor("", emptyList()) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New snippet", style = MaterialTheme.typography.labelLarge) }
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
                    ScreenIntro(
                        "Type a trigger and it expands into saved text - addresses, signatures, " +
                            "replies you send often."
                    )
                    Spacer(Modifier.height(Spacing.md))
                    NoticeCard(
                        title = "Two ways to expand",
                        message = "Type ${config.snippetTriggerPrefix}name to insert a snippet, or " +
                            "(.save:name:content) to save one without leaving the app you are in.",
                        tone = NoticeTone.Info
                    )
                    Spacer(Modifier.height(Spacing.md))
                    AppSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search snippets"
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Saved snippets",
                    description = when {
                        searchQuery.isNotBlank() -> "${visible.size} matching"
                        visible.isEmpty() -> "None yet"
                        else -> "${visible.size} saved"
                    }
                )
            }

            if (visible.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.BookmarkBorder,
                        title = if (searchQuery.isBlank()) "No snippets yet" else "Nothing matched",
                        description = if (searchQuery.isBlank()) {
                            "Save text you reuse and expand it with a short trigger."
                        } else {
                            "Try another word, or clear the search."
                        }
                    )
                }
            } else {
                item {
                    GroupCard {
                        visible.forEachIndexed { index, snippet ->
                            SnippetRow(
                                snippet = snippet,
                                onEdit = {
                                    openEditor(snippet.trigger, snippet.contents)
                                },
                                onDelete = { pendingDelete = snippet }
                            )
                            if (index != visible.lastIndex) RowDivider()
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        AppSheet(onDismissRequest = { editorOpen = false }) {
            SheetHeader(
                title = if (editingTrigger == null) "New snippet" else "Edit snippet",
                description = "Give it a trigger and one or more variations. Any variation can be chosen while typing.",
                onClose = { editorOpen = false }
            )
            Spacer(Modifier.height(Spacing.lg))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                AppTextField(
                    value = draftTrigger,
                    onValueChange = { draftTrigger = it },
                    label = "Trigger",
                    placeholder = "email",
                    supporting = "Expanded by typing ${config.snippetTriggerPrefix}$draftTrigger",
                    monospace = true
                )
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = "Variations",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.sm))
                draftVariations.forEachIndexed { index, variation ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppTextField(
                            value = variation,
                            onValueChange = { draftVariations[index] = it },
                            modifier = Modifier.weight(1f),
                            label = "Variation ${index + 1}",
                            singleLine = false,
                            minLines = 2
                        )
                        if (draftVariations.size > 1) {
                            IconButton(onClick = { draftVariations.removeAt(index) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove variation",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                }
                SmallButton(text = "Add variation", onClick = { draftVariations.add("") })
            }
            SheetActions {
                PrimaryButton(
                    text = "Save",
                    onClick = {
                        val contents = draftVariations.filter { it.isNotBlank() }.toMutableList()
                        val trigger = draftTrigger.trim()
                        val existing = snippets.toMutableList()
                        val duplicate = trigger != editingTrigger && existing.any { it.trigger == trigger }
                        when {
                            trigger.isBlank() || contents.isEmpty() -> Unit
                            duplicate -> Toast.makeText(
                                context,
                                "A snippet called “$trigger” already exists.",
                                Toast.LENGTH_SHORT
                            ).show()
                            else -> {
                                val updated = existing.toMutableList().apply {
                                    editingTrigger?.let { original -> removeIf { it.trigger == original } }
                                    removeIf { it.trigger == trigger }
                                    add(Snippet(trigger, contents = contents))
                                }
                                onSave(config.copy(snippets = updated))
                                editorOpen = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = draftTrigger.isNotBlank() && draftVariations.any { it.isNotBlank() }
                )
                GhostButton(text = "Cancel", onClick = { editorOpen = false })
            }
        }
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = "Delete “${target.trigger}”?",
            message = "This snippet and all of its variations are removed.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                onSave(
                    config.copy(snippets = snippets.toMutableList().apply { remove(target) })
                )
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun SnippetRow(
    snippet: Snippet,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.rowStart, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onEdit() }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = snippet.trigger,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = MonoFamily,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (snippet.contents.size > 1) {
                    Spacer(Modifier.width(Spacing.sm))
                    MonoChip(text = "${snippet.contents.size} variations", tone = CardTone.Muted)
                }
            }
            val preview = snippet.contents.firstOrNull().orEmpty()
            if (preview.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete snippet",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
