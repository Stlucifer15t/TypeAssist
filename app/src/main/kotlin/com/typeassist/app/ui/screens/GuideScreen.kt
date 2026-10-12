package com.typeassist.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.typeassist.app.data.AppConfig
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.components.AppCard
import com.typeassist.app.ui.components.AppScaffold
import com.typeassist.app.ui.components.AppTopBar
import com.typeassist.app.ui.components.MonoChip
import com.typeassist.app.data.HistoryManager
import com.typeassist.app.ui.components.ScreenIntro
import com.typeassist.app.ui.components.SecondaryButton
import com.typeassist.app.ui.components.SettingRow

/**
 * ============================== GUIDE ==============================
 *
 * The guide is generated from the configuration, not written by hand: the triggers listed here are
 * the ones this device actually responds to. If the user renames `.g` to `.fix`, the guide says
 * `.fix` - which is the whole reason the old hardcoded version kept drifting out of date.
 *
 * Each section opens in place, so the whole reference stays on one scrollable screen with no
 * navigation depth.
 */
@Composable
fun GuideScreen(
    config: AppConfig,
    onBack: () -> Unit,
    onOpenTest: () -> Unit,
    onOpenCommands: () -> Unit
) {
    AppScaffold(
        topBar = { AppTopBar(title = "Guide", onBack = onBack) }
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
                    "How the triggers on this device work. Everything here reflects your current " +
                        "settings, so it always matches what the app actually does."
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SecondaryButton(
                        text = "Try it",
                        onClick = onOpenTest,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Edit commands",
                        onClick = onOpenCommands,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ---- 1. Standard commands -----------------------------------------------------------
            item {
                GuideSection(
                    title = "Commands at the end of your text",
                    description = if (config.triggers.isEmpty()) {
                        "No commands are set up yet."
                    } else {
                        "${config.triggers.size} commands, typed as the last thing in the field."
                    },
                    icon = Icons.Default.TextFields
                ) {
                    if (config.triggers.isEmpty()) {
                        GuideNote("Add one from the Commands tab to use this pattern.")
                    } else {
                        config.triggers.forEach { trigger ->
                            GuideItem(
                                trigger = trigger.pattern,
                                title = "Runs this instruction",
                                body = trigger.prompt
                            )
                        }
                    }
                    GuideNote(
                        "Example: write your text, then ${config.triggers.firstOrNull()?.pattern ?: ".ta"} " +
                            "and the answer replaces the whole field."
                    )
                }
            }

            // ---- 2. Inline commands --------------------------------------------------------------
            item {
                GuideSection(
                    title = "Commands inside a sentence",
                    description = "Placed anywhere in the text, so one part of a sentence can be " +
                        "answered without touching the rest.",
                    icon = Icons.Default.Chat
                ) {
                    if (config.inlineCommands.isEmpty()) {
                        GuideNote("No inline commands are set up yet.")
                    } else {
                        config.inlineCommands.forEach { command ->
                            GuideItem(
                                trigger = command.pattern,
                                title = "Replaced in place",
                                body = command.prompt
                            )
                        }
                    }
                    GuideNote(
                        "The % in a pattern is where your text goes, so (%:.ta) wraps whatever you " +
                            "type inside the brackets."
                    )
                }
            }

            // ---- 3. Offline tools ----------------------------------------------------------------
            item {
                GuideSection(
                    title = "Offline tools",
                    description = "These run on the device without a provider, an API key or a network.",
                    icon = Icons.Default.Build
                ) {
                    GuideItem("(.c: 25 * 4 + 10)", "Calculator", "Returns 110. Supports + - * / ^, brackets, sqrt, sin, cos and log.")
                    GuideItem(".now", "Time", "Inserts the current time.")
                    GuideItem(".date", "Date", "Inserts today's date.")
                    GuideItem(".pass", "Password", "Generates a strong random password.")
                    GuideNote("These patterns are fixed; they do not need a provider and never leave the device.")
                }
            }

            // ---- 4. Snippets ---------------------------------------------------------------------
            item {
                GuideSection(
                    title = "Snippets",
                    description = "${config.snippets.size} saved, expanded by typing a prefix.",
                    icon = Icons.Default.Label
                ) {
                    GuideItem(
                        config.snippetTriggerPrefix,
                        "Expand a snippet",
                        "Type the prefix and the snippet name, for example " +
                            "${config.snippetTriggerPrefix}address, and the saved text takes its place."
                    )
                    GuideItem(
                        config.saveSnippetPattern,
                        "Save while typing",
                        "Captures the text as a new snippet without leaving the app you are in."
                    )
                    GuideNote("Each snippet can hold several variations; the app asks which one to use when there is more than one.")
                }
            }

            // ---- 5. Global rewrites --------------------------------------------------------------
            item {
                GuideSection(
                    title = "Rewrite the whole field",
                    description = "For instructions that apply to everything you have written.",
                    icon = Icons.Default.AutoFixHigh
                ) {
                    GuideItem(
                        config.globalTriggerPattern,
                        "Global instruction",
                        "Put your instruction between the first and last characters of the pattern. " +
                            "Example: 'I am running late ${config.globalTriggerPattern.replace("%", "make this apologetic")}'."
                    )
                    GuideNote("Useful for quick refinements like shortening, softening the tone or removing formatting.")
                }
            }

            // ---- 6. Undo and history -------------------------------------------------------------
            item {
                GuideSection(
                    title = "Undo and history",
                    description = "Nothing is final until you are happy with it.",
                    icon = Icons.Default.RestartAlt
                ) {
                    GuideItem(
                        config.undoCommandPattern,
                        "Undo",
                        "Restores the text that was replaced. Also available as a button on the " +
                            "floating chip right after a change."
                    )
                    GuideItem(
                        "Usage tab",
                        "Counts requests and tokens per provider, per session and in total. " +
                            "Providers that report their token counts are used as-is; the rest are " +
                            "estimated from the length of the text."
                    )
                    GuideItem(
                        "History tab",
                        if (config.isHistoryEnabled) {
                            "Keeps original text for " +
                                HistoryManager.describeRetention(config.historyRetentionMinutes) +
                                " so it can be recovered."
                        } else {
                            "History is currently turned off in Settings."
                        },
                        "The History tab lists what the app worked on, for the window set in " +
                            "Settings > Behaviour. Turn retention off there if you would rather " +
                            "nothing is kept."
                    )
                }
            }

            // ---- 7. Screen-aware commands ---------------------------------------------------------
            item {
                GuideSection(
                    title = "Reply to what is on screen",
                    description = "Reads the visible conversation and writes into the field you are in.",
                    icon = Icons.Default.Forum
                ) {
                    GuideItem(
                        ".reply",
                        "Draft a reply",
                        "Reads the latest message from the other person and drafts an answer in your " +
                            "own voice: your own messages from the conversation are quoted back to the " +
                            "model, so it copies your wording, length and level of formality. Add an " +
                            "instruction to steer it, for example '.reply keep it short'."
                    )
                    GuideItem(
                        ".sum",
                        "Summarise",
                        "Condenses the visible conversation into a few points."
                    )
                    GuideItem(
                        ".ta",
                        "Ask about the screen",
                        "Answers a question using the text on screen, and says so when the answer " +
                            "is not in the conversation."
                    )
                    GuideItem(
                        "@screen",
                        "Ask in your own words",
                        "Any instruction ending in @screen is answered from the conversation, for " +
                            "example 'summarise this @screen'."
                    )
                    GuideNote(
                        "Tone (match me, warm, formal, direct) and length (short, medium, detailed) " +
                            "come from Settings > Behaviour > Drafted replies, and are sent with the " +
                            "request. Screen reading is off by default, works only while a trigger is " +
                            "typed, and never runs in the background or in apps you block."
                    )
                }
            }

            // ---- 8. Tones ------------------------------------------------------------------------
            item {
                GuideSection(
                    title = "What the command names do",
                    description = "Plain-language summary of the instructions behind the defaults.",
                    icon = Icons.Default.Spellcheck
                ) {
                    GuideItem(".g", "Fix grammar", "Spelling, grammar and punctuation only. Nothing is rewritten.")
                    GuideItem(".polite", "Polite tone", "Makes the text more formal and professional.")
                    GuideItem(".casual", "Casual tone", "Makes the text friendlier and less formal.")
                    GuideItem(".improve", "Improve writing", "Clarifies the wording without changing the meaning.")
                    GuideItem(".tr", "Translate", "English by default; change the instruction to target another language.")
                    GuideNote("Every command is editable, so these names are only the starting point.")
                }
            }

            item {
                AppCard {
                    Text(
                        text = "Spotted something that no longer matches? The Commands tab is the " +
                            "single place where triggers and their instructions are defined.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * One collapsible topic. Collapsed by default so the guide scans as a list of headings, and opens
 * in place - the screen keeps its scroll position instead of pushing the user into a sub-screen.
 */
@Composable
private fun GuideSection(
    title: String,
    description: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    AppCard {
        SettingRow(
            title = title,
            description = description,
            icon = icon,
            onClick = { expanded = !expanded }
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = Spacing.sm)) {
                content()
            }
        }
    }
}

/** A trigger with the instruction behind it. The trigger is the chip, because it is an exact string. */
@Composable
private fun GuideItem(trigger: String, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoChip(text = trigger)
            Spacer(Modifier.size(Spacing.sm))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GuideNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm)
    )
}
