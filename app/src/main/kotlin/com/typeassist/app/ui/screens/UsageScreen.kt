package com.typeassist.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.typeassist.app.data.UsageTracker
import com.typeassist.app.ui.components.PageHeading
import com.typeassist.app.ui.components.SurfacePanel

/** Local usage dashboard: requests, words generated and the commands/models behind them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(onBack: () -> Unit) {
    var summary by remember { mutableStateOf(UsageTracker.summary()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Usage") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = {
                        UsageTracker.clear()
                        summary = UsageTracker.summary()
                    }) {
                        Icon(Icons.Default.DeleteSweep, "Reset statistics", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            PageHeading(
                eyebrow = "YOUR ACTIVITY",
                title = "Usage dashboard",
                description = "Everything stays on this device — ${summary.retainedDays} days retained."
            )
            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = "${summary.totalRequests}",
                    label = "Requests (${summary.retainedDays}d)"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = formatCount(summary.totalWords),
                    label = "Words generated"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = "${summary.todayRequests}",
                    label = "Today"
                )
            }

            Spacer(Modifier.height(14.dp))
            SurfacePanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(0.dp))
                    Text(
                        "  Reliability",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(10.dp))
                val successRate = if (summary.totalRequests > 0) {
                    summary.successfulRequests.toFloat() / summary.totalRequests.toFloat()
                } else 0f
                LinearProgressIndicator(
                    progress = { successRate },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${summary.successfulRequests} of ${summary.totalRequests} requests succeeded" +
                        if (summary.totalRequests > 0) " (${(successRate * 100).toInt()}%)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(6.dp))
            BucketSection(
                title = "Top commands",
                emptyText = "Run a command like .g or .ta to see activity here.",
                buckets = summary.topCommands
            )
            BucketSection(
                title = "Models used",
                emptyText = "Model usage appears after your first AI request.",
                buckets = summary.topModels
            )

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    UsageTracker.clear()
                    summary = UsageTracker.summary()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset statistics")
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, value: String, label: String) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BucketSection(title: String, emptyText: String, buckets: List<UsageTracker.Bucket>) {
    Spacer(Modifier.height(10.dp))
    SurfacePanel {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (buckets.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val max = buckets.maxOf { it.count }.coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                buckets.forEach { bucket ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                bucket.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${bucket.count}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { bucket.count.toFloat() / max },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private fun formatCount(count: Int): String {
    return if (count >= 10000) {
        val k = count / 1000.0
        if (k >= 100) "${k.toInt()}k" else String.format("%.1fk", k)
    } else count.toString()
}
