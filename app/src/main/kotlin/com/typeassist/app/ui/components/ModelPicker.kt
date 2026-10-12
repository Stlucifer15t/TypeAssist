package com.typeassist.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.typeassist.app.ui.MonoFamily
import com.typeassist.app.ui.Sizes
import com.typeassist.app.ui.Spacing

/**
 * ============================== MODEL PICKER ==============================
 *
 * A model id is an identifier, so it is typed and shown in monospace. The list lives in a sheet
 * rather than a dropdown: it holds hundreds of ids for Gemini, it is searchable, and favourites and
 * recents sit at the top where they are actually useful. Manual entry always remains available -
 * some compatible providers never publish a model list.
 */
@Composable
fun ModelPicker(
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
    modifier: Modifier = Modifier,
    label: String = "Model ID",
    placeholder: String = "e.g. gemini-2.0-flash",
    description: String? = null
) {
    var browseOpen by remember { mutableStateOf(false) }
    val favouriteSet = favoriteModels.toSet()
    val candidates = remember(availableModels, favoriteModels, recentModels) {
        buildList {
            favoriteModels.forEach { add(it to "Favourite") }
            recentModels.filterNot { it in favouriteSet }.forEach { add(it to "Recent") }
            availableModels.filterNot { it in favouriteSet || it in recentModels }
                .forEach { add(it to "Available") }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            monospace = true,
            supporting = description
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            SmallButton(
                text = if (isLoading) "Loading…" else "Load models",
                onClick = onLoadModels,
                loading = isLoading,
                enabled = !isLoading,
                primary = candidates.isEmpty()
            )
            if (candidates.isNotEmpty()) {
                SmallButton(
                    text = "Browse ${candidates.size}",
                    onClick = { browseOpen = true }
                )
            }
            if (availableModels.isNotEmpty()) {
                Text(
                    text = "${availableModels.size} available",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (statusMessage != null) {
            Spacer(Modifier.height(Spacing.sm))
            if (statusIsError) {
                InlineError(statusMessage)
            } else {
                InlineSuccess(statusMessage)
            }
        }
    }

    if (browseOpen) {
        var query by remember { mutableStateOf("") }
        val filtered = candidates.filter {
            query.isBlank() || it.first.contains(query.trim(), ignoreCase = true)
        }
        AppSheet(onDismissRequest = { browseOpen = false }) {
            SheetHeader(
                title = "Choose a model",
                description = "Favourites first, then your recent picks, then everything the provider returned.",
                onClose = { browseOpen = false }
            )
            Spacer(Modifier.height(Spacing.md))
            AppSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search ${candidates.size} models"
            )
            Spacer(Modifier.height(Spacing.md))
            if (filtered.isEmpty()) {
                Text(
                    text = "Nothing matches “$query”.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(filtered) { (modelId, category) ->
                        ModelRow(
                            modelId = modelId,
                            category = category,
                            selected = modelId == value,
                            favourite = modelId in favouriteSet,
                            onSelect = {
                                onSelectModel(modelId)
                                browseOpen = false
                            },
                            onToggleFavourite = { onToggleFavorite(modelId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    modelId: String,
    category: String,
    selected: Boolean,
    favourite: Boolean,
    onSelect: () -> Unit,
    onToggleFavourite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = modelId,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = MonoFamily,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        IconButton(onClick = onToggleFavourite) {
            Icon(
                imageVector = if (favourite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (favourite) "Remove favourite" else "Add favourite",
                tint = if (favourite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(Sizes.icon)
            )
        }
    }
}

/** Kept for screens that want the same "reload" affordance outside the picker. */
@Composable
fun ReloadModelsButton(
    isLoading: Boolean,
    onLoadModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    SmallButton(
        text = if (isLoading) "Loading…" else "Reload models",
        onClick = onLoadModels,
        loading = isLoading,
        enabled = !isLoading,
        modifier = modifier
    )
    Spacer(Modifier.width(Spacing.sm))
    Icon(
        imageVector = Icons.Default.Refresh,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(Sizes.iconSm)
    )
}
