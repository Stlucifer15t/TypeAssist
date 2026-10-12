package com.typeassist.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.typeassist.app.data.AppConfig
import com.typeassist.app.ui.MonoFamily
import com.typeassist.app.ui.Sizes
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.components.AppCard
import com.typeassist.app.ui.components.AppScaffold
import com.typeassist.app.ui.components.AppTopBar
import com.typeassist.app.ui.components.PrimaryButton
import com.typeassist.app.ui.components.SectionHeader
import com.typeassist.app.ui.extra
import kotlinx.coroutines.launch

/**
 * ============================== WHAT ELSE IT CAN DO ==============================
 *
 * Four short pages, shown once after a feature update. Each page teaches one capability with a
 * concrete before/after pair - no feature tour voice, no screenshots to squint at, and the triggers
 * shown are the ones configured on this device.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DidYouKnowScreen(
    config: AppConfig,
    onFinished: () -> Unit
) {
    val pages = remember(config.globalTriggerPattern, config.snippetTriggerPrefix) {
        listOf(
            DiscoveryPage(
                title = "Rewrite anything, anywhere",
                summary = "Wrap an instruction and the text around it is rewritten in place.",
                icon = Icons.Default.AutoFixHigh,
                pairs = listOf(
                    "Hey boss i cant come today ${config.globalTriggerPattern.replace("%", "make it formal")}" to
                        "Dear Manager, I will be unable to attend work today.",
                    "Hola amigo ${config.globalTriggerPattern.replace("%", "translate to English")}" to
                        "Hello friend"
                )
            ),
            DiscoveryPage(
                title = "Maths without leaving the chat",
                summary = "Calculations run on the device, so they work with the network off.",
                icon = Icons.Default.Build,
                pairs = listOf(
                    "Total: (.c: 25 * 4)" to "Total: 100",
                    "Share: (.c: 150 / 3)" to "Share: 50"
                )
            ),
            DiscoveryPage(
                title = "Snippets save the typing",
                summary = "Type the prefix plus a name to expand saved text into any field.",
                icon = Icons.Default.Label,
                pairs = listOf(
                    "${config.snippetTriggerPrefix}email" to "name@example.com",
                    config.saveSnippetPattern to "Saved without leaving the app"
                )
            ),
            DiscoveryPage(
                title = "Every command is yours to edit",
                summary = "Answers in the wrong language, or the wrong tone? Change the instruction " +
                    "behind the command instead of working around it.",
                icon = Icons.Default.Tune,
                pairs = listOf(
                    "Commands → tap a trigger → edit the instruction" to "Add “Reply in German” and it always will"
                )
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "What else it does",
                onBack = onFinished,
                actions = {
                    Text(
                        text = if (isLastPage) "Done" else "Skip",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onFinished() }
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                val page = pages[pageIndex]
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.screen,
                        end = Spacing.screen,
                        top = Spacing.sm,
                        bottom = Spacing.lg
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.extra.accentSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = page.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(Spacing.md))
                            Text(
                                text = page.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        Text(
                            text = page.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        SectionHeader(
                            title = "Try this",
                            description = "The arrow shows what it becomes."
                        )
                    }
                    page.pairs.forEach { pair ->
                        item {
                            AppCard {
                                Text(
                                    text = pair.first,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = MonoFamily,
                                    color = MaterialTheme.extra.onAccentSoft,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.extra.accentSoft)
                                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                                )
                                Spacer(Modifier.height(Spacing.md))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 14.dp, height = Sizes.hairline)
                                            .background(MaterialTheme.extra.hairline)
                                    )
                                    Spacer(Modifier.width(Spacing.sm))
                                    Text(
                                        text = pair.second,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.lg)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    pages.indices.forEach { index ->
                        val selected = pagerState.currentPage == index
                        val color by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.extra.hairline,
                            animationSpec = tween(180),
                            label = "pageDot"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = Spacing.xxs)
                                .size(width = if (selected) 20.dp else 8.dp, height = 8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
                PrimaryButton(
                    text = if (isLastPage) "Got it" else "Next",
                    onClick = {
                        if (isLastPage) {
                            onFinished()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Page ${pagerState.currentPage + 1} of ${pages.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private data class DiscoveryPage(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val pairs: List<Pair<String, String>>
)
