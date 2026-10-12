package com.typeassist.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.components.AppMark
import com.typeassist.app.ui.components.AppScaffold
import com.typeassist.app.ui.components.PrimaryButton
import com.typeassist.app.ui.components.TriggerDemo
import com.typeassist.app.ui.extra
import kotlinx.coroutines.launch

/**
 * ============================== FIRST RUN ==============================
 *
 * Two steps: what the app does, then the permission it needs. The first page demonstrates the idea
 * with a live animation instead of describing it, and the second page is the real permissions
 * screen - so nobody lands in the app without a working service.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    AppScaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false
            ) { page ->
                when (page) {
                    0 -> WelcomeIntroPage(onNext = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    })
                    else -> PermissionsScreen(
                        onFinished = onFinished,
                        isStandalone = false
                    )
                }
            }

            if (pagerState.currentPage == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(2) { index ->
                        val selected = pagerState.currentPage == index
                        val color by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.extra.hairline,
                            animationSpec = tween(180),
                            label = "onboardingDot"
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
            }
        }
    }
}

@Composable
private fun WelcomeIntroPage(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
    ) {
        Spacer(Modifier.height(Spacing.xxxl))

        Row(verticalAlignment = Alignment.CenterVertically) {
            AppMark(size = 40.dp)
            Spacer(Modifier.size(Spacing.md))
            Column {
                Text(
                    text = "Prompt AI",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "AI that works inside your text fields",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(Spacing.xxl))

        Text(
            text = "Write anywhere. Finish with a trigger.",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "The app watches the field you are typing in. Add a trigger at the end and the " +
                "text is fixed, translated or rewritten before you send it - without switching apps " +
                "or copying anything.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(Spacing.xl))

        TriggerDemo()

        Spacer(Modifier.height(Spacing.xl))

        Text(
            text = "It only reads the field that has focus, only when you use a trigger, and " +
                "nothing is stored unless history is on. Everything is configurable later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start
        )

        Spacer(Modifier.height(Spacing.xxl))

        PrimaryButton(
            text = "Continue",
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Spacing.xl))
    }
}
