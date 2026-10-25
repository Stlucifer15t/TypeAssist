package com.typeassist.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import com.typeassist.app.data.DataExporter
import com.typeassist.app.data.HistoryManager
import com.typeassist.app.data.HistoryStore
import com.typeassist.app.ui.MonoFamily
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.components.AppScaffold
import com.typeassist.app.ui.components.AppTopBar
import com.typeassist.app.ui.components.ConfirmDialog
import com.typeassist.app.ui.components.EmptyState
import com.typeassist.app.ui.components.GhostButton
import com.typeassist.app.ui.components.GroupCard
import com.typeassist.app.ui.components.RowDivider
import com.typeassist.app.ui.components.ScreenIntro
import com.typeassist.app.ui.components.SegmentedTabs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ============================== HISTORY ==============================
 *
 * Two related views of what the assistant has done: the recent processed text (so nothing you
 * replaced is ever really gone) and the token counts behind those commands. The second view is a
 * tab on this screen rather than a sixth bottom-bar destination, which keeps the bar readable and
 * puts "what did it do" next to "what did it use".
 *
 * The retention window comes from settings (one hour by default), rows are timestamped, and copying
 * is one tap per row.
 */
@Composable
fun HistoryScreen(
    retentionMinutes: Int,
    onBack: () -> Unit
) {
    val window = HistoryManager.describeRetention(retentionMinutes)
    val context = LocalContext.current
    // The entries are written to disk as they happen, so read them back here: the service may not
    // be running, and the list must not be empty just because the app was restarted.
    var historyItems by remember(context) {
        HistoryManager.attachStore(HistoryStore(context))
        mutableStateOf(HistoryManager.getHistory())
    }
    var showClearDialog by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = if (tab == 1) "Token usage" else "History",
                onBack = onBack,
                onBack = onBack,
                actions = {
                    // Export hands the tab's data to the share sheet: usage as counters only,
                    // history as the text the user replaced - their data, their choice.
                    if (tab == 0 && historyItems.isNotEmpty()) {
                        GhostButton(text = "Export", onClick = {
                            shareCsv(context, "Share history as CSV", DataExporter.historyCsv())
                        })
                    }
                    if (tab == 1) {
                        GhostButton(text = "Export", onClick = {
                            shareCsv(context, "Share token usage as CSV", DataExporter.usageCsv())
                        })
                    }
                    if (tab == 0 && historyItems.isNotEmpty()) {
                        GhostButton(text = "Clear", onClick = { showClearDialog = true })
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SegmentedTabs(
                items = listOf("History", "Usage"),
                selectedIndex = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    top = Spacing.sm,
                    bottom = Spacing.xs
                )
            )

            if (tab == 1) {
                UsageContent(
                    padding = PaddingValues(0.dp),
                    showIntro = true
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.screen,
                        end = Spacing.screen,
                        top = Spacing.xs,
                        bottom = Spacing.xxxl
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    item {
                        ScreenIntro(
                            "Text you processed in the last $window. Copy anything back, or clear " +
                                "the list. Change the window in Settings › Behaviour."
                        )
                    }

                    if (historyItems.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Default.Schedule,
                                title = "Nothing yet",
                                description = "Text you process shows up here for $window."
                            )
                        }
                    } else {
                        item {
                            GroupCard {
                                // The list is capped by HistoryManager, so it is laid out in full
                                // inside one card rather than as a nested lazy list.
                                historyItems.forEachIndexed { index, entry ->
                                    HistoryRow(
                                        time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                            .format(Date(entry.timestamp)),
                                        text = entry.text,
                                        onCopy = {
                                            val clipboard = context.getSystemService(
                                                Context.CLIPBOARD_SERVICE
                                            ) as ClipboardManager
                                            clipboard.setPrimaryClip(
                                                ClipData.newPlainText("History item", entry.text)
                                            )
                                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    if (index != historyItems.lastIndex) RowDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = "Clear history?",
            message = "This removes every item kept in your $window history. " +
                "Text already inserted into other apps is not affected.",
            confirmLabel = "Clear history",
            destructive = true,
            onConfirm = {
                HistoryManager.clear()
                historyItems = HistoryManager.getHistory()
                showClearDialog = false
                Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showClearDialog = false }
        )
    }
}

/** Hands a CSV to the share sheet, so the export lands wherever the user wants it. */
private fun shareCsv(context: Context, chooserTitle: String, csv: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Prompt AI export")
            putExtra(Intent.EXTRA_TEXT, csv)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open the share sheet: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun HistoryRow(
    time: String,
    text: String,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.rowStart, end = Spacing.xs, top = Spacing.md, bottom = Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = MonoFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy text",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 6
        )
    }
}
