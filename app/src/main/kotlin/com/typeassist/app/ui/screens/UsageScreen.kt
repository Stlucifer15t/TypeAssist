package com.typeassist.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.typeassist.app.data.UsageStore
import com.typeassist.app.data.UsageTracker
import com.typeassist.app.ui.MonoFamily
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.components.AppCard
import com.typeassist.app.ui.components.AppScaffold
import com.typeassist.app.ui.components.AppTopBar
import com.typeassist.app.ui.components.ConfirmDialog
import com.typeassist.app.ui.components.EmptyState
import com.typeassist.app.ui.components.GroupCard
import com.typeassist.app.ui.components.NavRow
import com.typeassist.app.ui.components.NoticeCard
import com.typeassist.app.ui.components.NoticeTone
import com.typeassist.app.ui.components.RowDivider
import com.typeassist.app.ui.components.ScreenIntro
import com.typeassist.app.ui.components.SectionHeader
import com.typeassist.app.ui.components.SettingRow
import com.typeassist.app.ui.components.SmallButton

/**
 * ============================== TOKEN USAGE ==============================
 *
 * What the assistant has actually sent and received: requests and tokens per provider, for this
 * session and in total. Numbers come from the provider when it reports them and are estimated from
 * the text length otherwise, and the screen says so rather than pretending to be a billing report.
 *
 * Reachable two ways — as a tab next to History, and from Settings › Data on its own — because the
 * question ("how much is this using?") is asked from both places.
 */
@Composable
fun UsageScreen(
    onBack: () -> Unit,
    onOpenGeneration: () -> Unit,
    onOpenProviders: () -> Unit
) {
    AppScaffold(
        topBar = { AppTopBar(title = "Token usage", onBack = onBack) }
    ) { padding ->
        UsageContent(
            padding = padding,
            onOpenGeneration = onOpenGeneration,
            onOpenProviders = onOpenProviders,
            showIntro = true
        )
    }
}

/**
 * The screen body, shared by the standalone route and the tab inside History. Refreshes from the
 * stored totals on first composition, so opening it after a restart still shows the all-time rows.
 */
@Composable
fun UsageContent(
    padding: PaddingValues,
    onOpenGeneration: (() -> Unit)? = null,
    onOpenProviders: (() -> Unit)? = null,
    showIntro: Boolean
) {
    val context = LocalContext.current
    var session by remember { mutableStateOf(UsageTracker.sessionRows()) }
    var allTime by remember { mutableStateOf(UsageTracker.allTimeRows()) }
    var showResetDialog by remember { mutableStateOf(false) }

    fun refresh() {
        session = UsageTracker.sessionRows()
        allTime = UsageTracker.allTimeRows()
    }

    // A restart clears the in-memory totals, so the stored ones are read back once, off the
    // composition itself.
    LaunchedEffect(Unit) {
        UsageTracker.load(UsageStore(context).load())
        refresh()
    }

    val sessionTotals = UsageTracker.sessionTotals()
    val allTimeTotals = UsageTracker.allTimeTotals()

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
        if (showIntro) {
            item {
                ScreenIntro(
                    "Requests and tokens since the app started, and in total. Counts come from the " +
                        "provider when it reports them, and are estimated from the text length otherwise."
                )
            }
        }

        item { SectionHeader(title = "This session") }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    UsageFigure(
                        value = sessionTotals.requests.toString(),
                        label = if (sessionTotals.requests == 1) "request" else "requests",
                        modifier = Modifier.weight(1f)
                    )
                    UsageFigure(
                        value = UsageTracker.formatTokens(sessionTotals.totalTokens),
                        label = "tokens",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "Sent ${UsageTracker.formatTokens(sessionTotals.promptTokens)} · " +
                        "Received ${UsageTracker.formatTokens(sessionTotals.completionTokens)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = MonoFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (sessionTotals.requests > 0) {
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = estimateNote(sessionTotals.estimatedRequests, sessionTotals.requests),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (session.isNotEmpty()) {
            item {
                GroupCard {
                    session.forEachIndexed { index, row ->
                        SettingRow(
                            title = UsageTracker.shortModel(row.model),
                            description = "${UsageTracker.providerLabel(row.provider)} · " +
                                "${row.requests} ${if (row.requests == 1) "request" else "requests"}",
                            icon = Icons.Default.Speed
                        ) {
                            Text(
                                text = UsageTracker.formatTokens(row.totalTokens),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = MonoFamily,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (index != session.lastIndex) RowDivider()
                    }
                }
            }
            item {
                SmallButton(
                    text = "Clear this session",
                    onClick = {
                        UsageTracker.clearSession()
                        refresh()
                    }
                )
            }
        } else {
            item {
                EmptyState(
                    icon = Icons.Default.Speed,
                    title = "Nothing yet",
                    description = "Token counts appear here after the first command is processed."
                )
            }
        }

        item { SectionHeader(title = "In total") }
        if (allTimeTotals.hasNumbers) {
            item {
                AppCard {
                    Text(
                        text = "${UsageTracker.formatTokens(allTimeTotals.totalTokens)} tokens across " +
                            "${allTimeTotals.requests} " +
                            (if (allTimeTotals.requests == 1) "request" else "requests"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = "Kept on this device only, and only as numbers: no text from any " +
                            "conversation is stored here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                GroupCard {
                    allTime.forEachIndexed { index, row ->
                        SettingRow(
                            title = "${UsageTracker.providerLabel(row.provider)} · " +
                                UsageTracker.shortModel(row.model),
                            description = "${row.requests} " +
                                (if (row.requests == 1) "request" else "requests") +
                                " · ${UsageTracker.formatTokens(row.totalTokens)} tokens"
                        )
                        if (index != allTime.lastIndex) RowDivider()
                    }
                }
            }
            item {
                SmallButton(
                    text = "Reset totals",
                    onClick = { showResetDialog = true }
                )
            }
        } else {
            item {
                AppCard {
                    Text(
                        text = "No totals stored yet. They start counting with the next command.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (onOpenGeneration != null || onOpenProviders != null) {
            item { SectionHeader(title = "Keep it low") }
            item {
                GroupCard {
                    if (onOpenGeneration != null) {
                        NavRow(
                            title = "Text generation",
                            description = "The answer-length limit sent with every request is the " +
                                "one setting that directly caps what a command can use.",
                            icon = Icons.Default.Tune,
                            onClick = onOpenGeneration
                        )
                    }
                    if (onOpenGeneration != null && onOpenProviders != null) RowDivider()
                    if (onOpenProviders != null) {
                        NavRow(
                            title = "Provider & model",
                            description = "A smaller model usually uses fewer tokens for the same " +
                                "command.",
                            icon = Icons.Default.Speed,
                            onClick = onOpenProviders
                        )
                    }
                }
            }
        }

        item {
            NoticeCard(
                title = "How these numbers are counted",
                message = "Providers that report token counts (Gemini, Workers AI and " +
                    "OpenAI-compatible answers) are used as-is. Streaming and providers that stay " +
                    "silent are estimated at about ${UsageTracker.CHARS_PER_TOKEN} characters per " +
                    "token, which is a rule of thumb for English and an over- or under-count for " +
                    "other languages. Treat these as a guide to your usage, not as a bill.",
                tone = NoticeTone.Info,
                icon = Icons.Default.Info
            )
        }
    }

    if (showResetDialog) {
        ConfirmDialog(
            title = "Reset token totals?",
            message = "This clears the stored totals and the session counters. It does not affect " +
                "your settings, your history or anything at your provider.",
            confirmLabel = "Reset",
            destructive = true,
            onConfirm = {
                UsageTracker.clearAll()
                UsageStore(context).clear()
                showResetDialog = false
                refresh()
            },
            onDismiss = { showResetDialog = false }
        )
    }
}

@Composable
private fun UsageFigure(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = MonoFamily,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun estimateNote(estimatedRequests: Int, requests: Int): String = when {
    estimatedRequests == 0 -> "Every count here was reported by the provider."
    estimatedRequests >= requests -> "Estimated from text length: these providers do not report usage."
    else -> "$estimatedRequests of $requests requests were estimated from text length."
}
