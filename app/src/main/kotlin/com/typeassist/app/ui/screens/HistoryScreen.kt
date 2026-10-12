package com.typeassist.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.items
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
import com.typeassist.app.data.HistoryManager
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ============================== HISTORY ==============================
 *
 * The last five minutes of processed text, so nothing you replaced is ever really gone. The title
 * states the window, the rows are timestamped left-to-right, and copying is one tap per row.
 */
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    var historyItems by remember { mutableStateOf(HistoryManager.getHistory()) }
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "History",
                onBack = onBack,
                actions = {
                    if (historyItems.isNotEmpty()) {
                        GhostButton(text = "Clear", onClick = { showClearDialog = true })
                    }
                }
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
                bottom = Spacing.xxxl
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                ScreenIntro("Text you processed in the last five minutes. Copy anything back, or clear the list.")
            }

            if (historyItems.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Schedule,
                        title = "Nothing yet",
                        description = "Text processed with Prompt AI shows up here for five minutes."
                    )
                }
            } else {
                item {
                    GroupCard {
                        items(historyItems) { item ->
                            HistoryRow(
                                time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                    .format(Date(item.timestamp)),
                                text = item.text,
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("History item", item.text)
                                    )
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                }
                            )
                            if (item != historyItems.last()) RowDivider()
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = "Clear history?",
            message = "This removes every item currently kept in your five-minute history. " +
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
