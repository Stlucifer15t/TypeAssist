package com.typeassist.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.LoadingIndicatorStyle
import com.typeassist.app.ui.Sizes
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.components.AppScaffold
import com.typeassist.app.ui.components.AppTopBar
import com.typeassist.app.ui.components.LoadingStylePreview
import com.typeassist.app.ui.components.NoticeCard
import com.typeassist.app.ui.components.NoticeTone
import com.typeassist.app.ui.components.ScreenIntro
import com.typeassist.app.ui.components.SecondaryButton
import com.typeassist.app.ui.components.SectionHeader
import com.typeassist.app.ui.components.StatusPill
import com.typeassist.app.ui.components.StatusTone
import com.typeassist.app.ui.extra

/**
 * ============================== WHAT APPEARS OVER OTHER APPS ==============================
 *
 * A life-size preview of every floating surface Prompt AI draws on top of other apps: the loading
 * indicator, the result chip, the preview before text is inserted, and the undo pill. Nothing here
 * changes behaviour - it exists so the look can be checked without leaving the app, and so the
 * controls that do change it are one tap away in Appearance.
 */
@Composable
fun SystemPreviewScreen(
    config: AppConfig,
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit
) {
    val indicatorColor = Color(LoadingIndicatorStyle.sanitizeColor(config.loadingIndicatorColor))
    val indicatorScale = LoadingIndicatorStyle.sanitizeSizePercent(config.loadingIndicatorSizePercent) / 100f

    AppScaffold(
        topBar = { AppTopBar(title = "Floating overlay", onBack = onBack) }
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
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item {
                ScreenIntro(
                    "These are the pieces that appear over other apps while the accessibility " +
                        "service is running. The preview uses your current colour and size settings."
                )
            }

            item {
                SecondaryButton(
                    text = "Open appearance settings",
                    onClick = onOpenAppearance,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                if (config.enableLoadingOverlay) {
                    StatusPill(text = "Loading indicator on", tone = StatusTone.Active)
                } else {
                    StatusPill(text = "Loading indicator off", tone = StatusTone.Idle)
                }
            }

            // ---- Loading indicator + result chip, shown over a mock text field ------------------
            item {
                SectionHeader(
                    title = "While a command is running",
                    description = "Drawn in the centre of the screen so it is never hidden by the keyboard."
                )
            }
            item {
                OverlayStage {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .border(
                                    Sizes.hairline,
                                    MaterialTheme.extra.hairline,
                                    MaterialTheme.shapes.medium
                                )
                                .padding(Spacing.md),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "The text you were working on…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(Spacing.lg))
                        Box(
                            modifier = Modifier
                                .size(72.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingStylePreview(
                                styleId = config.loadingIndicatorStyle,
                                color = indicatorColor,
                                scale = indicatorScale
                            )
                        }
                    }
                }
            }

            item { SectionHeader(title = "After the text is replaced") }
            item {
                OverlayStage {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(Sizes.hairline, MaterialTheme.extra.hairline, CircleShape)
                            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChipAction(label = "Undo", accent = true)
                        Box(
                            modifier = Modifier
                                .size(width = Sizes.hairline, height = 18.dp)
                                .background(MaterialTheme.extra.hairline)
                        )
                        ChipAction(label = "Copy")
                        Box(
                            modifier = Modifier
                                .size(width = Sizes.hairline, height = 18.dp)
                                .background(MaterialTheme.extra.hairline)
                        )
                        ChipAction(label = "Retry")
                    }
                }
            }

            item {
                NoticeCard(
                    title = "The chip never steals focus",
                    message = "Tapping Undo, Copy or Retry does not close the keyboard or move the " +
                        "cursor, because the overlay window is not focusable.",
                    tone = NoticeTone.Info
                )
            }

            // ---- Preview before inserting --------------------------------------------------------
            item {
                SectionHeader(
                    title = "Preview before inserting",
                    description = if (config.enablePreviewDialog) {
                        "Shown for commands that replace existing text."
                    } else {
                        "Currently off: results are inserted straight away."
                    }
                )
            }
            item {
                OverlayStage {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(
                                Sizes.hairline,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                MaterialTheme.shapes.large
                            )
                            .padding(Spacing.lg)
                    ) {
                        Text(
                            text = "Preview response",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(Spacing.xxs))
                        Text(
                            text = "Long-press the text to change the selection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            text = "Spelling and grammar mistakes should be fixed before this " +
                                "message is sent to the customer.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(Spacing.lg))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Discard",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(Spacing.xl))
                            Text(
                                text = "Insert",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // ---- Undo pill -----------------------------------------------------------------------
            item { SectionHeader(title = "Undo") }
            item {
                OverlayStage {
                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(Sizes.hairline, MaterialTheme.extra.hairline, MaterialTheme.shapes.large)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.extra.success,
                            modifier = Modifier.size(Sizes.icon)
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Column {
                            Text(
                                text = "Text replaced",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Undo stays available for a few seconds",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(Spacing.lg))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Sizes.iconSm)
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                text = "Undo",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item { SectionHeader(title = "Snippet picker") }
            item {
                OverlayStage {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(Sizes.hairline, MaterialTheme.extra.hairline, MaterialTheme.shapes.large)
                            .padding(Spacing.md)
                    ) {
                        Text(
                            text = "Choose a variation",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        listOf(
                            "Thanks for reaching out - we're looking into this now.",
                            "Thanks for the message. We're on it and will update you shortly."
                        ).forEach { variation ->
                            Text(
                                text = variation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.sm)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A framed stage that stands in for "another app" behind the floating surface. */
@Composable
private fun OverlayStage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(Sizes.hairline, MaterialTheme.extra.hairline, MaterialTheme.shapes.large)
            .padding(Spacing.lg)
    ) {
        content()
    }
}

@Composable
private fun ChipAction(label: String, accent: Boolean = false) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
    )
}
