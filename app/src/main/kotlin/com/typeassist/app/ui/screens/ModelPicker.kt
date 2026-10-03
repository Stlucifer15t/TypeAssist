package com.typeassist.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api

data class ModelCandidate(
    val id: String,
    val category: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerCard(
    title: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    availableModels: List<String>,
    favoriteModels: List<String>,
    recentModels: List<String>,
    isLoading: Boolean,
    statusMessage: String?,
    statusIsError: Boolean,
    onLoadModels: () -> Unit,
    onSelectModel: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val favorites = favoriteModels.toSet()
    val candidates = remember(availableModels, favoriteModels, recentModels) {
        buildList {
            favoriteModels.forEach { id -> add(ModelCandidate(id, "Favorite")) }
            recentModels.filterNot { it in favorites }.forEach { id -> add(ModelCandidate(id, "Recent")) }
            availableModels.filterNot { it in favorites || it in recentModels }.forEach { id ->
                add(ModelCandidate(id, "Available"))
            }
        }
    }
    val filteredCandidates = candidates
        .filter { query.isBlank() || it.id.contains(query.trim(), ignoreCase = true) }
        .take(40)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        ExposedDropdownMenuBox(
            expanded = expanded && filteredCandidates.isNotEmpty(),
            onExpandedChange = {
                expanded = it
                if (it) query = ""
            }
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    query = it
                    onValueChange(it)
                    expanded = true
                },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                label = { Text("Model ID") },
                placeholder = { Text("Type a model ID or load available models") },
                singleLine = true,
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (value.isNotBlank()) {
                            IconButton(onClick = {
                                query = ""
                                onValueChange("")
                                expanded = true
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear model search")
                            }
                        }
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                }
            )
            ExposedDropdownMenu(
                expanded = expanded && filteredCandidates.isNotEmpty(),
                onDismissRequest = { expanded = false }
            ) {
                filteredCandidates.forEach { candidate ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    candidate.id,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (candidate.id == value) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    candidate.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { onToggleFavorite(candidate.id) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = if (candidate.id in favorites) "Remove favorite" else "Add favorite",
                                    tint = if (candidate.id in favorites) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                )
                            }
                        },
                        onClick = {
                            onSelectModel(candidate.id)
                            query = ""
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onLoadModels,
                enabled = !isLoading,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (isLoading) "Loading…" else "Load models")
            }
            if (availableModels.isNotEmpty()) {
                Text(
                    "${availableModels.size} available",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (statusMessage != null) {
            Spacer(Modifier.height(7.dp))
            Text(
                statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
        if (favorites.isNotEmpty() || recentModels.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            Text(
                "Favorites and recent models appear at the top of the list.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}
