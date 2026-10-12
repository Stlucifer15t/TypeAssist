package com.typeassist.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.GenConfig
import com.typeassist.app.data.GenerationSettings
import com.typeassist.app.ui.MonoFamily
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.components.AppCard
import com.typeassist.app.ui.components.AppScaffold
import com.typeassist.app.ui.components.AppTopBar
import com.typeassist.app.ui.components.ChoiceRow
import com.typeassist.app.ui.components.GroupCard
import com.typeassist.app.ui.components.NoticeCard
import com.typeassist.app.ui.components.NoticeTone
import com.typeassist.app.ui.components.RowDivider
import com.typeassist.app.ui.components.ScreenIntro
import com.typeassist.app.ui.components.SectionHeader
import com.typeassist.app.ui.components.SettingRow
import com.typeassist.app.ui.components.SmallButton
import com.typeassist.app.ui.components.StatusPill
import com.typeassist.app.ui.components.StatusTone

/**
 * ============================== TEXT GENERATION ==============================
 *
 * The sliders that decide *how* the model answers: how literal it is, how widely it picks its words
 * and how long the answer may be. Every value here is sent with the request — `generationConfig` for
 * Gemini, `temperature` / `top_p` / `max_tokens` for Cloudflare and for any OpenAI-compatible
 * endpoint — which is what makes them real settings and not decoration.
 *
 * The on-device model keeps its own sliders: a 0.5B model running on a phone needs a different
 * starting point than a hosted model, and mixing the two would break whichever one was set last.
 */
@Composable
fun GenerationSettingsScreen(
    config: AppConfig,
    onSave: (AppConfig) -> Unit,
    onBack: () -> Unit,
    onNavigateLocal: () -> Unit
) {
    val stored = GenerationSettings.sanitize(config.generationConfig)
    var temperature by remember(stored.temperature) { mutableStateOf(stored.temperature.toFloat()) }
    var topP by remember(stored.topP) { mutableStateOf(stored.topP.toFloat()) }
    var maxTokens by remember(stored.maxTokens) { mutableStateOf(stored.maxTokens.toFloat()) }

    fun save() {
        onSave(
            config.copy(
                generationConfig = GenerationSettings.sanitize(
                    GenConfig(
                        temperature = temperature.toDouble(),
                        topP = topP.toDouble(),
                        maxTokens = maxTokens.toInt()
                    )
                )
            )
        )
    }

    val activePreset = GenerationSettings.presetIdOf(temperature.toDouble(), topP.toDouble())
    val isDefault = activePreset == "balanced" && maxTokens.toInt() == GenerationSettings.DEFAULT_MAX_TOKENS
    val providerName = when (config.provider) {
        "gemini" -> "Google Gemini"
        "cloudflare" -> "Cloudflare Workers AI"
        "custom" -> "OpenAI-compatible API"
        else -> "On-device model"
    }

    AppScaffold(
        topBar = { AppTopBar(title = "Text generation", onBack = onBack) }
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
                    "Sent with every request, so these values change how the AI answers: grammar " +
                        "and translation want precision, drafting wants freedom."
                )
            }

            item {
                SettingRow(
                    title = "Used by",
                    description = "Cloud providers use these values.",
                    icon = Icons.Default.Speed
                ) {
                    StatusPill(
                        text = providerName,
                        tone = if (config.provider == "local") StatusTone.Idle else StatusTone.Active
                    )
                }
            }

            item { SectionHeader(title = "Starting point") }
            item {
                GroupCard {
                    GenerationSettings.PRESETS.forEachIndexed { index, preset ->
                        ChoiceRow(
                            title = preset.name,
                            description = preset.description,
                            selected = activePreset == preset.id,
                            useRadio = false,
                            onSelect = {
                                temperature = preset.temperature.toFloat()
                                topP = preset.topP.toFloat()
                                save()
                            }
                        )
                        if (index != GenerationSettings.PRESETS.lastIndex) RowDivider()
                    }
                }
            }

            item {
                SettingRow(
                    title = "Temperature",
                    description = GenerationSettings.describeTemperature(temperature.toDouble())
                ) {
                    Text(
                        text = GenerationSettings.formatTemperature(temperature.toDouble()),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            item {
                AppCard {
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        onValueChangeFinished = { save() },
                        valueRange = GenerationSettings.MIN_TEMPERATURE.toFloat()..
                            GenerationSettings.MAX_TEMPERATURE.toFloat(),
                        steps = 19
                    )

                    RowDivider(insetStart = false)
                    Spacer(Modifier.height(Spacing.sm))
                    SettingRow(
                        title = "Top-P",
                        description = GenerationSettings.describeTopP(topP.toDouble())
                    ) {
                        Text(
                            text = GenerationSettings.formatTopP(topP.toDouble()),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Slider(
                        value = topP,
                        onValueChange = { topP = it },
                        onValueChangeFinished = { save() },
                        valueRange = GenerationSettings.MIN_TOP_P.toFloat()..
                            GenerationSettings.MAX_TOP_P.toFloat(),
                        steps = 18
                    )

                    RowDivider(insetStart = false)
                    Spacer(Modifier.height(Spacing.sm))
                    SettingRow(
                        title = "Max tokens",
                        description = GenerationSettings.describeMaxTokens(maxTokens.toInt())
                    ) {
                        Text(
                            text = GenerationSettings.formatMaxTokens(maxTokens.toInt()),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Slider(
                        value = maxTokens,
                        onValueChange = { maxTokens = it },
                        onValueChangeFinished = { save() },
                        valueRange = 0f..GenerationSettings.MAX_MAX_TOKENS.toFloat(),
                        steps = 15
                    )

                    Spacer(Modifier.height(Spacing.md))
                    SmallButton(
                        text = "Reset to defaults",
                        enabled = !isDefault,
                        onClick = {
                            val defaults = GenerationSettings.defaults()
                            temperature = defaults.temperature.toFloat()
                            topP = defaults.topP.toFloat()
                            maxTokens = defaults.maxTokens.toFloat()
                            save()
                        }
                    )
                }
            }

            item {
                NoticeCard(
                    title = "What these values do",
                    message = "Temperature is how much the model improvises. Top-P limits it to the " +
                        "most likely words. Max tokens caps the answer's length; “Provider default” " +
                        "lets the model decide, which is what most providers expect. An answer that " +
                        "stops mid-sentence means the token limit is too low for the task.",
                    tone = NoticeTone.Info,
                    icon = Icons.Default.Info
                )
            }

            item {
                AppCard {
                    Text(
                        text = "Sent with each request",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = buildString {
                            append("temperature ")
                            append(GenerationSettings.formatTemperature(temperature.toDouble()))
                            append("\ntop_p ")
                            append(GenerationSettings.formatTopP(topP.toDouble()))
                            if (maxTokens.toInt() > 0) {
                                append("\nmax_tokens ")
                                append(maxTokens.toInt())
                            } else {
                                append("\nno token limit sent")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = MonoFamily
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = if (activePreset != null) {
                            "Matches the ${GenerationSettings.presetById(activePreset)?.name} preset."
                        } else {
                            "Custom values: the sliders sit between the presets."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SettingRow(
                    title = "Local model values",
                    description = "On-device models are tuned separately, with a token budget that " +
                        "suits a phone.",
                    icon = Icons.Default.Memory,
                    onClick = onNavigateLocal
                ) {
                    Text(
                        text = "${config.localLlmConfig.maxTokens} tokens",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
