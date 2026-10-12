package com.typeassist.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.typeassist.app.ui.Sizes
import com.typeassist.app.ui.Spacing

/**
 * ============================== DIALOGS & SHEETS ==============================
 *
 * Two patterns only:
 *
 *  * [AppDialog] / [ConfirmDialog] - short, interrupting decisions (delete, skip, overwrite).
 *  * [AppSheet] - editing and picking. Anything with more than two fields lives in a sheet, because
 *    a sheet is reachable with a thumb and grows with the content instead of clipping it.
 *
 * Both are built here so every dialog in the app has identical radius, padding, title weight and
 * button order (dismiss on the left, the deciding action on the right).
 */

@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    dismissLabel: String? = null,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    body: (@Composable ColumnScope.() -> Unit)? = null
) {
    val dialogIcon: (@Composable () -> Unit)? = if (icon != null) {
        {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Sizes.iconLg)
            )
        }
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 0.dp,
        icon = dialogIcon,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                if (message != null) {
                    Text(text = message, style = MaterialTheme.typography.bodyMedium)
                }
                body?.invoke(this)
            }
        },
        confirmButton = {
            if (destructive) {
                DangerButton(text = confirmLabel, onClick = onConfirm, enabled = confirmEnabled)
            } else {
                PrimaryButton(text = confirmLabel, onClick = onConfirm, enabled = confirmEnabled)
            }
        },
        dismissButton = {
            if (dismissLabel != null) {
                TextButton(
                    onClick = onDismiss,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(dismissLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    AppDialog(
        title = title,
        message = message,
        onDismiss = onDismiss,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        dismissLabel = "Cancel",
        destructive = destructive
    )
}

/**
 * Bottom sheet shell for editing and selection. Drag handle, title row with a close affordance, then
 * whatever the screen needs. Content scrolls, and the sheet lifts above the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xxl),
            content = content
        )
    }
}

/** Title row of a sheet: title, one-line explanation, close button. */
@Composable
fun SheetHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            if (description != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.icon)
            )
        }
    }
}

/** The action row at the bottom of a sheet. */
@Composable
fun SheetActions(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        content = content
    )
}

/** Small helper for a field label inside a sheet body. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
