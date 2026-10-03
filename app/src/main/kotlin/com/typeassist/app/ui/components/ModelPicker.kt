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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/**
 * Model name field with a "read from provider" button and a searchable picker.
 *
 * The text field stays editable, so anyone who prefers typing an exact model id
 * can keep doing that - the list is only a shortcut.
 */
@Composable
fun ModelSelector(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    models: List<String>,
    isFetching: Boolean,
    errorMessage: String?,
    canFetch: Boolean,
    helperText: String,
    placeholder: String = "",
    pickerTitle: String = "Select Model",
    fetchLabel: String = "Fetch Models",
    onFetch: () -> Unit,
    onModelPicked: ((String) -> Unit)? = null
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(6.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val statusColor = if (errorMessage != null) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        val statusText = when {
            isFetching -> "Reading models from the provider…"
            errorMessage != null -> errorMessage
            models.isNotEmpty() -> "${models.size} models available"
            else -> helperText
        }
        Text(
            text = statusText,
            fontSize = 11.sp,
            color = statusColor,
            lineHeight = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(4.dp))
        if (models.isNotEmpty()) {
            TextButton(onClick = { showPicker = true }) {
                Text("Browse", fontSize = 12.sp)
            }
        }
        TextButton(onClick = onFetch, enabled = canFetch && !isFetching) {
            if (isFetching) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(if (models.isEmpty()) fetchLabel else "Refresh", fontSize = 12.sp)
        }
    }

    if (value.isNotBlank() && models.isNotEmpty() && models.none { it.equals(value, ignoreCase = true) }) {
        Text(
            text = "⚠️ \"$value\" is not in the fetched list - it may be wrong or no longer available.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.error,
            lineHeight = 14.sp
        )
    }

    if (showPicker) {
        ModelPickerDialog(
            title = pickerTitle,
            models = models,
            selected = value,
            isFetching = isFetching,
            errorMessage = errorMessage,
            onSelect = { picked ->
                showPicker = false
                onValueChange(picked)
                onModelPicked?.invoke(picked)
            },
            onRefresh = onFetch,
            onDismiss = { showPicker = false }
        )
    }
}

/** Searchable list of every model the provider reported. */
@Composable
fun ModelPickerDialog(
    title: String,
    models: List<String>,
    selected: String,
    isFetching: Boolean,
    errorMessage: String?,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(models, query) {
        if (query.isBlank()) models else models.filter { it.contains(query.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                when {
                    isFetching && models.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading models…", fontSize = 13.sp)
                    }

                    filtered.isEmpty() -> Text(
                        text = errorMessage
                            ?: if (models.isEmpty()) "No models loaded yet. Tap \"Refresh list\" to read them from your provider."
                            else "No model matches \"$query\".",
                        fontSize = 13.sp,
                        color = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        items(filtered, key = { it }) { model ->
                            val isSelected = model.equals(selected, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(model) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = model,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = onRefresh, enabled = !isFetching) {
                Text(if (isFetching) "Refreshing…" else "Refresh list")
            }
        }
    )
}
