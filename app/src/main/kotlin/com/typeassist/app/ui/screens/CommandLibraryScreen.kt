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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.CommandLibrary
import com.typeassist.app.data.CommandTemplate
import com.typeassist.app.data.InlineCommand
import com.typeassist.app.data.Trigger
import com.typeassist.app.ui.MonoFamily
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.components.AppScaffold
import com.typeassist.app.ui.components.AppSheet
import com.typeassist.app.ui.components.AppTopBar
import com.typeassist.app.ui.components.CardTone
import com.typeassist.app.ui.components.EmptyState
import com.typeassist.app.ui.components.GhostButton
import com.typeassist.app.ui.components.GroupCard
import com.typeassist.app.ui.components.MonoChip
import com.typeassist.app.ui.components.PrimaryButton
import com.typeassist.app.ui.components.RowDivider
import com.typeassist.app.ui.components.ScreenIntro
import com.typeassist.app.ui.components.SectionHeader
import com.typeassist.app.ui.components.SheetActions
import com.typeassist.app.ui.components.SheetHeader
import com.typeassist.app.ui.components.SmallButton

/**
 * ============================== COMMAND GALLERY ==============================
 *
 * Ready-made prompts, grouped as a scannable list rather than a wall of cards. Tapping a row opens
 * the full instruction so nothing is added blind; the Add button is the only way in.
 */
@Composable
fun CommandLibraryScreen(
    config: AppConfig,
    onSave: (AppConfig) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var detail by remember { mutableStateOf<CommandTemplate?>(null) }
    val templates = CommandLibrary.templates

    fun addTemplate(template: CommandTemplate): Boolean {
        val currentTriggers = config.triggers ?: mutableListOf()
        val currentInline = config.inlineCommands ?: mutableListOf()
        val exists = if (template.isInline) {
            currentInline.any { it.pattern == template.recommendedTrigger }
        } else {
            currentTriggers.any { it.pattern == template.recommendedTrigger }
        }
        if (exists) {
            Toast.makeText(
                context,
                "“${template.recommendedTrigger}” is already in your commands.",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        if (template.isInline) {
            val updated = currentInline.toMutableList()
            updated.add(InlineCommand(template.recommendedTrigger, template.systemPrompt))
            onSave(config.copy(inlineCommands = updated))
        } else {
            val updated = currentTriggers.toMutableList()
            updated.add(Trigger(template.recommendedTrigger, template.systemPrompt))
            onSave(config.copy(triggers = updated))
        }
        Toast.makeText(context, "Added “${template.title}” to your commands.", Toast.LENGTH_SHORT).show()
        return true
    }

    AppScaffold(
        topBar = { AppTopBar(title = "Command gallery", onBack = onBack) }
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
                ScreenIntro(
                    "Add a ready-made shortcut, then edit it like any other command. " +
                        "Tap a row to read the full instruction first."
                )
            }

            if (templates.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Bolt,
                        title = "The gallery is empty",
                        description = "No starter commands are available in this build."
                    )
                }
            } else {
                val standardTemplates = templates.filterNot { it.isInline }
                val inlineTemplates = templates.filter { it.isInline }

                if (standardTemplates.isNotEmpty()) {
                    item { SectionHeader(title = "Standard commands") }
                    item {
                        GroupCard {
                            standardTemplates.forEachIndexed { index, template ->
                                TemplateRow(
                                    template = template,
                                    onOpen = { detail = template },
                                    onAdd = { addTemplate(template) }
                                )
                                if (index != standardTemplates.lastIndex) RowDivider()
                            }
                        }
                    }
                }
                if (inlineTemplates.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Inline commands",
                            description = "These run anywhere inside a sentence."
                        )
                    }
                    item {
                        GroupCard {
                            inlineTemplates.forEachIndexed { index, template ->
                                TemplateRow(
                                    template = template,
                                    onOpen = { detail = template },
                                    onAdd = { addTemplate(template) }
                                )
                                if (index != inlineTemplates.lastIndex) RowDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.let { template ->
        AppSheet(onDismissRequest = { detail = null }) {
            SheetHeader(
                title = template.title,
                description = template.description,
                onClose = { detail = null }
            )
            Spacer(Modifier.height(Spacing.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (template.isInline) "Inline trigger" else "Trigger",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                MonoChip(text = template.recommendedTrigger, tone = CardTone.Accent)
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "Instruction sent to the AI",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = template.systemPrompt,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = MonoFamily,
                color = MaterialTheme.colorScheme.onSurface
            )
            SheetActions {
                PrimaryButton(
                    text = "Add to my commands",
                    onClick = {
                        if (addTemplate(template)) detail = null
                    },
                    modifier = Modifier.weight(1f)
                )
                GhostButton(text = "Close", onClick = { detail = null })
            }
        }
    }
}

@Composable
private fun TemplateRow(
    template: CommandTemplate,
    onOpen: () -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.rowStart, end = Spacing.sm, top = Spacing.md, bottom = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onOpen() }
        ) {
            Text(
                text = template.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = template.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonoChip(text = template.recommendedTrigger, tone = CardTone.Muted)
                if (template.isInline) {
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "INLINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.width(Spacing.md))
        SmallButton(text = "Add", onClick = onAdd, primary = true)
    }
}
