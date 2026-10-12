package com.typeassist.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.typeassist.app.ui.Sizes
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.extra

/**
 * ============================== STATUS & STATES ==============================
 *
 * Status is communicated with a coloured dot and a short word, never with a loud banner or an
 * emoji. Every state in the app - service running, provider connected, permission missing, error
 * from a provider - uses one of the components below, so "something is wrong" always looks the
 * same and is therefore instantly recognisable.
 */

enum class StatusTone { Active, Idle, Warning, Critical, Accent }

@Composable
private fun toneColors(tone: StatusTone): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        StatusTone.Active -> MaterialTheme.extra.success to MaterialTheme.extra.successContainer
        StatusTone.Warning -> MaterialTheme.extra.warning to MaterialTheme.extra.warningContainer
        StatusTone.Critical -> scheme.error to scheme.errorContainer
        StatusTone.Accent -> scheme.primary to MaterialTheme.extra.accentSoft
        StatusTone.Idle -> scheme.onSurfaceVariant to scheme.surfaceContainerHigh
    }
}

/** Dot + word. The compact way to say "this is on", "this is paused", "this needs attention". */
@Composable
fun StatusPill(
    text: String,
    tone: StatusTone = StatusTone.Idle,
    modifier: Modifier = Modifier
) {
    val (content, container) = toneColors(tone)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = container
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(Sizes.statusDot)
                    .clip(CircleShape)
                    .background(content)
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                maxLines = 1
            )
        }
    }
}

/** Just the dot, for dense lists where the word would be noise. */
@Composable
fun StatusDot(tone: StatusTone, modifier: Modifier = Modifier) {
    val (content, _) = toneColors(tone)
    Box(
        modifier = modifier
            .size(Sizes.statusDot)
            .clip(CircleShape)
            .background(content)
    )
}

/** A thin, unobtrusive progress ring for inline "working..." states. */
@Composable
fun InlineSpinner(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 16.dp,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        strokeWidth = 2.dp,
        color = tint
    )
}

/** Three dots, used where a full ring would be too heavy (loading a model list, for example). */
@Composable
fun LoadingDots(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha = 0.35f + (index * 0.22f)
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = alpha))
            )
        }
    }
}

enum class NoticeTone { Info, Success, Warning, Critical }

/**
 * A bordered note. Used for anything the user must know *before* it matters: the accessibility
 * service being off, an incompatible model, a skipped stability setting, a failed connection test.
 * Optional by design - screens do not show a note unless it is true.
 */
@Composable
fun NoticeCard(
    title: String,
    message: String,
    tone: NoticeTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val (content, container) = when (tone) {
        NoticeTone.Info -> scheme.onSurfaceVariant to scheme.surfaceContainer
        NoticeTone.Success -> MaterialTheme.extra.success to MaterialTheme.extra.successContainer
        NoticeTone.Warning -> MaterialTheme.extra.onWarningContainer to MaterialTheme.extra.warningContainer
        NoticeTone.Critical -> scheme.onErrorContainer to scheme.errorContainer
    }
    AppCard(
        modifier = modifier,
        tone = when (tone) {
            NoticeTone.Success -> CardTone.Surface
            NoticeTone.Warning -> CardTone.Warning
            NoticeTone.Critical -> CardTone.Critical
            NoticeTone.Info -> CardTone.Muted
        }
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(Sizes.icon)
                )
                Spacer(Modifier.width(Spacing.md))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = content
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.88f)
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.sm))
            GhostButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** The empty state: one quiet glyph, one sentence of explanation, one optional way forward. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxl, vertical = Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.iconLg)
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = description,
            modifier = Modifier.widthIn(max = 280.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.lg))
            SecondaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** An inline error line: a provider failure, an invalid value, a failed paste. */
@Composable
fun InlineError(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.statusDot)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error)
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/** A positive inline confirmation, used after a successful connection test or a save. */
@Composable
fun InlineSuccess(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.statusDot)
                .clip(CircleShape)
                .background(MaterialTheme.extra.success)
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extra.success
        )
    }
}
