package com.typeassist.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.typeassist.app.ui.MonoFamily
import com.typeassist.app.ui.Sizes
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.extra

/**
 * ============================== ROWS ==============================
 *
 * Rows are the app's primary structural unit. They are flat (no per-row card), minimum 48-56dp tall,
 * and always laid out with the same insets, so a list of twenty settings is as easy to scan as a
 * list of three. Separators come from [RowDivider] and are the caller's choice, which keeps the
 * grouping intentional.
 */

/** Optional leading glyph: a small tinted tile, or nothing at all when the title is self-evident. */
@Composable
private fun RowLeading(icon: ImageVector?, tint: androidx.compose.ui.graphics.Color?) {
    if (icon == null) return
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Sizes.iconSm)
        )
    }
    Spacer(Modifier.width(Spacing.md))
}

/**
 * The base row: a title, an optional second line, optional leading glyph and any trailing content.
 * [onClick] makes the whole row tappable, which is what keeps tap targets comfortable one-handed.
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    iconTint: androidx.compose.ui.graphics.Color? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(if (onClick != null && enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = Spacing.rowStart, vertical = Spacing.rowVertical)
            .heightIn(min = Sizes.touchTarget),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowLeading(icon, iconTint)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            if (description != null) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.md))
            trailing()
        }
    }
}

/** Title, description and a switch. The switch is the row's only control. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true
) {
    SettingRow(
        title = title,
        description = description,
        modifier = modifier,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) }
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

/** A row that navigates somewhere: trailing chevron, whole row tappable. */
@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    iconTint: androidx.compose.ui.graphics.Color? = null,
    value: String? = null,
    enabled: Boolean = true
) {
    SettingRow(
        title = title,
        description = description,
        icon = icon,
        iconTint = iconTint,
        enabled = enabled,
        modifier = modifier,
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(Spacing.sm))
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.icon)
            )
        }
    }
}

/** A row whose trailing element is a compact action button. */
@Composable
fun ActionRow(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    SettingRow(
        title = title,
        description = description,
        icon = icon,
        enabled = enabled,
        modifier = modifier,
        trailing = {
            SmallButton(
                text = actionLabel,
                onClick = onAction,
                enabled = enabled
            )
        }
    )
}

/**
 * A single-select row used for appearance, provider choice and any other exclusive pick. Shows a
 * check rather than a radio when the list is short, so the row stays about the choice.
 */
@Composable
fun ChoiceRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    useRadio: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    SettingRow(
        title = title,
        description = description,
        modifier = modifier,
        onClick = onSelect,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trailing != null) {
                    trailing()
                    Spacer(Modifier.width(Spacing.md))
                }
                if (useRadio) {
                    RadioButton(
                        selected = selected,
                        onClick = onSelect,
                        colors = androidx.compose.material3.RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary,
                            unselectedColor = MaterialTheme.colorScheme.outline
                        )
                    )
                } else {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Sizes.icon)
                        )
                    }
                }
            }
        }
    )
}

/**
 * A command or snippet: the trigger in monospace (it is an identifier, so it gets identifier
 * typography), the prompt or content as a muted second line. Tap anywhere to edit.
 */
@Composable
fun TriggerRow(
    trigger: String,
    detail: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    inline: Boolean = false,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = Spacing.rowStart, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm)
            .heightIn(min = Sizes.switchRowMin),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = trigger,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = MonoFamily,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (inline) {
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "INLINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                if (meta != null) {
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Sizes.icon)
                )
            }
        }
    }
}

/** A compact key/value row used inside cards for configuration facts. */
@Composable
fun CardFact(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) MonoFamily else null,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Kept for call sites that want the muted hairline colour directly. */
@Composable
fun hairlineColor() = MaterialTheme.extra.hairline
