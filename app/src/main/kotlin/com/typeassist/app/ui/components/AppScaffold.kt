package com.typeassist.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.typeassist.app.ui.MonoFamily
import com.typeassist.app.ui.Sizes
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.extra

/**
 * ============================== SCREEN CHROME ==============================
 *
 * Every screen is assembled from these three pieces, which is what makes the app feel like one
 * product instead of a folder of screens:
 *
 *  * [AppScaffold] - window insets, background, optional bars and floating action.
 *  * [AppTopBar]  - a 56dp bar with a back affordance and up to two actions. Its title is the
 *    screen's *only* title; screens do not repeat it in the body.
 *  * [ScreenIntro] - the optional one-line explanation under the bar. Used when a screen's purpose
 *    is not obvious from its title, never as decoration.
 */

/** The app bar. Owns its status-bar inset so screens never have to think about it. */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(Sizes.topBar)
                .padding(
                    start = if (onBack == null) Spacing.screen else Spacing.sm,
                    end = Spacing.sm
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            actions()
        }
    }
}

/**
 * Content shell: background, insets, optional top bar, optional bottom bar and an optional floating
 * action. Screens pass their scrolling content in [content]; the padding it receives already
 * accounts for the bars.
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    floatingAction: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { topBar?.invoke() },
        bottomBar = { bottomBar?.invoke() },
        floatingActionButton = { floatingAction?.invoke() }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            content(padding)
        }
    }
}

/**
 * A short paragraph under the app bar. Body-sized and muted: it explains, it does not shout. There
 * are no uppercase "eyebrow" labels anywhere in the app - titles carry the hierarchy.
 */
@Composable
fun ScreenIntro(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * A section heading inside a screen: small, quiet, sits directly above its content. Lowercase
 * sentence case on purpose, so it reads as structure rather than as a label shouting for attention.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description != null) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * The separator used inside a [GroupCard]. Inset to the row's text start, the way a professional
 * settings list does it, so a column of rows reads as one object rather than a stack of boxes.
 */
@Composable
fun RowDivider(
    modifier: Modifier = Modifier,
    insetStart: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = if (insetStart) Spacing.rowStart else 0.dp)
            .height(Sizes.hairline)
            .background(MaterialTheme.extra.hairline)
    )
}

/**
 * ============================== SURFACES ==============================
 *
 * Cards are used *only* where they earn their place: a distinct object the user acts on (the
 * service panel, a provider profile, a snippet). Everything else is a grouped list. This is the
 * single biggest departure from the old UI, where every row was its own bordered card.
 */
enum class CardTone {
    /** Default: raised surface, hairline border, no shadow. */
    Surface,

    /** A quieter fill for nested or secondary content. */
    Muted,

    /** Accent-tinted, for the one or two things on a screen that are genuinely highlighted. */
    Accent,

    /** Attention, without failure. */
    Warning,

    /** Something needs fixing. */
    Critical
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Surface,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.card),
    border: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val container = when (tone) {
        CardTone.Surface -> scheme.surface
        CardTone.Muted -> scheme.surfaceContainer
        CardTone.Accent -> MaterialTheme.extra.accentSoft
        CardTone.Warning -> MaterialTheme.extra.warningContainer
        CardTone.Critical -> scheme.errorContainer
    }
    val stroke = when (tone) {
        CardTone.Surface -> BorderStroke(Sizes.hairline, MaterialTheme.extra.hairline)
        CardTone.Muted -> BorderStroke(Sizes.hairline, MaterialTheme.extra.hairline)
        CardTone.Accent -> BorderStroke(Sizes.hairline, scheme.primary.copy(alpha = 0.35f))
        CardTone.Warning -> BorderStroke(Sizes.hairline, MaterialTheme.extra.warning.copy(alpha = 0.35f))
        CardTone.Critical -> BorderStroke(Sizes.hairline, scheme.error.copy(alpha = 0.35f))
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = MaterialTheme.shapes.large,
        color = container,
        border = if (border) stroke else null,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * A grouped list: rows inside one card, separated by inset hairlines. The workhorse of Settings,
 * and the pattern that makes long configuration screens scannable.
 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            SectionHeader(title = title, description = description)
        }
        AppCard(contentPadding = PaddingValues(vertical = Spacing.xs)) {
            content()
        }
    }
}

/**
 * A single labelled chip: used for triggers, model ids, provider names - anything the user needs to
 * recognise as an exact string. Monospace, because these are identifiers, not prose.
 */
@Composable
fun MonoChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Muted,
    onClick: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val container = when (tone) {
        CardTone.Accent -> MaterialTheme.extra.accentSoft
        CardTone.Warning -> MaterialTheme.extra.warningContainer
        CardTone.Critical -> scheme.errorContainer
        else -> scheme.surfaceContainerHigh
    }
    val content = when (tone) {
        CardTone.Accent -> MaterialTheme.extra.onAccentSoft
        CardTone.Warning -> MaterialTheme.extra.onWarningContainer
        CardTone.Critical -> scheme.onErrorContainer
        else -> scheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = MaterialTheme.shapes.small,
        color = container
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = MonoFamily,
            color = content,
            maxLines = 1
        )
    }
}

/**
 * The app's mark: a lime tile holding a text caret - the app's whole idea in one glyph. Drawn from
 * primitives rather than an icon so it renders identically everywhere and cannot look like a stock
 * AI sparkle. Used in the home header and on the onboarding hero.
 */
@Composable
fun AppMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    background: Color = MaterialTheme.colorScheme.primary,
    foreground: Color = MaterialTheme.colorScheme.onPrimary
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(size * 0.11f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(size * 0.13f)
                    .height(size * 0.46f)
                    .clip(RoundedCornerShape(percent = 40))
                    .background(foreground)
            )
            Box(
                modifier = Modifier
                    .width(size * 0.13f)
                    .height(size * 0.30f)
                    .clip(RoundedCornerShape(percent = 40))
                    .background(foreground.copy(alpha = 0.55f))
            )
        }
    }
}
