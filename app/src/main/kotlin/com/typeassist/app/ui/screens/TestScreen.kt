package com.typeassist.app.ui.screens

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import com.typeassist.app.data.AppConfig
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.components.AppCard
import com.typeassist.app.ui.components.AppScaffold
import com.typeassist.app.ui.components.AppTextField
import com.typeassist.app.ui.components.AppTopBar
import com.typeassist.app.ui.components.GroupCard
import com.typeassist.app.ui.components.MonoChip
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

/**
 * ============================== TRY IT HERE ==============================
 *
 * The fastest way to find out whether the setup works: type in the box below and use a trigger.
 * The accessibility service keeps running while this screen is open, so what happens here is what
 * happens everywhere else - the loading indicator, the result chip and undo all behave normally.
 *
 * Failures here are almost always permissions, so that check is part of the screen rather than a
 * link the user has to go looking for.
 */
@Composable
fun TestScreen(
    config: AppConfig,
    onStartTest: () -> Unit,
    onStopTest: () -> Unit,
    onOpenPermissions: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    var sample by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onStartTest()
        onDispose { onStopTest() }
    }

    val prefix = config.globalTriggerPattern.ifBlank { ".ta" }
    val presets = remember(prefix) {
        listOf(
            "What is the capital of Bangladesh? $prefix" to "A question - the answer replaces this text",
            "Sp3ll1ng and gr@mm3r mistakes shall be fixing $prefix" to "Fix the mistakes in place",
            "এটি একটি এআই ভিত্তিক অ্যাপ। ${config.snippetTriggerPrefix.ifBlank { ".s" }}" to "Pick a snippet variation"
        )
    }

    AppScaffold(
        topBar = { AppTopBar(title = "Try a trigger", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
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
                    "Type in the field below, finished with a trigger. The service works here " +
                        "exactly as it does in any other app."
                )
            }

            item {
                StatusPill(text = "Listening for triggers", tone = StatusTone.Active)
            }

            item {
                AppCard {
                    Column {
                        Text(
                            text = "Test field",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        AppTextField(
                            value = sample,
                            onValueChange = { sample = it },
                            placeholder = "Type your text, then the trigger…",
                            singleLine = false,
                            minLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                        )
                        Spacer(Modifier.height(Spacing.md))
                        PrimaryButton(
                            text = "Clear field",
                            onClick = {
                                sample = ""
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                                    as InputMethodManager
                                imm.hideSoftInputFromWindow(view.windowToken, 0)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = sample.isNotEmpty()
                        )
                    }
                }
            }

            item { SectionHeader(title = "Try one of these") }
            item {
                GroupCard {
                    presets.forEachIndexed { index, (text, description) ->
                        SettingRow(
                            title = description,
                            description = "Tap to place this text in the field above",
                            onClick = { sample = text }
                        ) {
                            SmallButton(
                                text = "Use",
                                onClick = { sample = text }
                            )
                        }
                        if (index != presets.lastIndex) RowDivider()
                    }
                }
            }

            item {
                AppCard {
                    Text(
                        text = "Active triggers",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    val triggers = buildList {
                        add("Trigger" to prefix)
                        if (config.snippetTriggerPrefix.isNotBlank()) {
                            add("Snippet" to config.snippetTriggerPrefix)
                        }
                    }
                    triggers.forEach { (label, value) ->
                        SettingRow(title = label, description = null) {
                            MonoChip(text = value)
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = "Commands and their triggers are listed on the Commands tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                NoticeCard(
                    title = "Nothing happened?",
                    message = "Check that the accessibility service is on, and that the trigger " +
                        "matches the one in Commands. Triggers only work in fields that accept text " +
                        "input.",
                    tone = NoticeTone.Warning,
                    icon = Icons.Default.Science,
                    actionLabel = "Check permissions",
                    onAction = onOpenPermissions
                )
            }

            item {
                AppCard {
                    Text(
                        text = "Copy the sample text to test in another app",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.md))
                    SmallButton(
                        text = "Copy to clipboard",
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText(
                                    "Prompt AI test text",
                                    sample.ifBlank { "What is the capital of Bangladesh? $prefix" }
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}
