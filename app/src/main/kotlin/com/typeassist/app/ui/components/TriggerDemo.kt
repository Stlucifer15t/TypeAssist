package com.typeassist.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.typeassist.app.ui.MonoFamily
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.extra
import kotlinx.coroutines.delay

/**
 * A miniature of the thing the app actually does: type text, say your trigger, get the rewritten
 * text back. It runs on the onboarding hero and on the home screen.
 *
 * Deliberately built from the same primitives as the rest of the UI (a bordered panel, a monospace
 * trigger chip, the app's own three-dot loading indicator) so it reads as a demo of this product
 * rather than as decoration.
 */
private data class TriggerScenario(
    val input: String,
    val trigger: String,
    val output: String
)

private val TRIGGER_SCENARIOS = listOf(
    TriggerScenario("i go home yestarday", ".g", "I went home yesterday."),
    TriggerScenario("Capital of France?", ".ta", "Paris"),
    TriggerScenario("send me the file", ".polite", "Could you please send me the file?"),
    TriggerScenario("会议在下午三点", ".tr", "The meeting is at 3 PM.")
)

@Composable
fun TriggerDemo(modifier: Modifier = Modifier) {
    var scenarioIndex by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    var triggerShown by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }
    var caretOn by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            caretOn = !caretOn
            delay(560)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val scenario = TRIGGER_SCENARIOS[scenarioIndex]
            typed = ""
            triggerShown = false
            working = false
            output = ""
            delay(700)

            for (index in 1..scenario.input.length) {
                typed = scenario.input.take(index)
                delay(58)
            }
            delay(420)
            triggerShown = true
            delay(360)
            working = true
            delay(1100)
            working = false
            output = scenario.output
            delay(3200)

            scenarioIndex = (scenarioIndex + 1) % TRIGGER_SCENARIOS.size
        }
    }

    AppCard(modifier = modifier, tone = CardTone.Muted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = typed,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (typed.isNotEmpty() && !triggerShown) {
                Box(
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .width(2.dp)
                        .height(17.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = if (caretOn) 1f else 0f)
                        )
                )
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(
                visible = triggerShown,
                enter = fadeIn(tween(140)) + slideInVertically(tween(160)) { it / 3 }
            ) {
                MonoChip(text = TRIGGER_SCENARIOS[scenarioIndex].trigger, tone = CardTone.Accent)
            }
        }

        Spacer(Modifier.height(Spacing.md))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.extra.hairline)
        )
        Spacer(Modifier.height(Spacing.md))

        Box(modifier = Modifier.heightIn(min = 42.dp)) {
            FadeReveal(
                visible = working,
                enter = fadeIn(tween(140)),
                exit = fadeOut(tween(140))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingDots()
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        text = "Working",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FadeReveal(
                visible = output.isNotBlank(),
                enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 4 }
            ) {
                Text(
                    text = output,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.widthIn(max = 320.dp)
                )
            }
            if (!working && output.isBlank()) {
                Text(
                    text = "Type in any app, then finish with a trigger.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            DemoLegend(trigger = ".ta", meaning = "ask AI")
            DemoLegend(trigger = ".g", meaning = "fix grammar")
            DemoLegend(trigger = ".tr", meaning = "translate")
        }
    }
}

@Composable
private fun DemoLegend(trigger: String, meaning: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = trigger,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = MonoFamily,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = meaning,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Fade/slide reveal that works from any layout scope.
 *
 * `AnimatedVisibility` has Column- and Row-scoped overloads, and calling it inside a `Box` that is
 * nested in a `Column` leaves the compiler with two applicable implicit receivers. This wrapper
 * declares no receiver of its own, so the plain overload is always chosen.
 */
@Composable
private fun FadeReveal(
    visible: Boolean,
    enter: EnterTransition = fadeIn(),
    exit: ExitTransition = fadeOut(),
    content: @Composable () -> Unit
) {
    AnimatedVisibility(visible = visible, enter = enter, exit = exit) {
        content()
    }
}
