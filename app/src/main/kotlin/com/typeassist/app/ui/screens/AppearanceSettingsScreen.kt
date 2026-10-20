package com.typeassist.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.LoadingIndicatorStyle
import com.typeassist.app.ui.AppThemeMode
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.ThemePalettes
import com.typeassist.app.ui.components.AppCard
import com.typeassist.app.ui.components.AppScaffold
import com.typeassist.app.ui.components.AppTextField
import com.typeassist.app.ui.components.AppTopBar
import com.typeassist.app.ui.components.CardTone
import com.typeassist.app.ui.components.ChoiceRow
import com.typeassist.app.ui.components.GhostButton
import com.typeassist.app.ui.components.GroupCard
import com.typeassist.app.ui.components.LOADING_STYLES
import com.typeassist.app.ui.components.LoadingStylePreview
import com.typeassist.app.ui.components.RowDivider
import com.typeassist.app.ui.components.ScreenIntro
import com.typeassist.app.ui.components.SectionHeader
import com.typeassist.app.ui.components.SwitchRow

/**
 * ============================== APPEARANCE ==============================
 *
 * Theme, the floating controls that appear while the assistant works, and the loading indicator's
 * style, colour and size. The indicator preview is the real animation at its real size, so what you
 * pick here is what you get over another app.
 */
@Composable
fun AppearanceSettingsScreen(
    config: AppConfig,
    onSave: (AppConfig) -> Unit,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onBack: () -> Unit
) {
    var enableUndoOverlay by remember(config.enableUndoOverlay) { mutableStateOf(config.enableUndoOverlay) }
    var enableLoadingOverlay by remember(config.enableLoadingOverlay) { mutableStateOf(config.enableLoadingOverlay) }
    var loadingStyle by remember(config.loadingIndicatorStyle) {
        mutableStateOf(
            LoadingIndicatorStyle.normalizeStyle(
                LoadingIndicatorStyle.styleIdOf(config.loadingIndicatorStyle)
            )
        )
    }
    var indicatorColor by remember(config.loadingIndicatorColor) {
        mutableIntStateOf(LoadingIndicatorStyle.sanitizeColor(config.loadingIndicatorColor))
    }
    var indicatorNeon by remember(config.loadingIndicatorNeon) {
        mutableStateOf(config.loadingIndicatorNeon)
    }
    var indicatorNeonColor by remember(config.loadingIndicatorNeonColor) {
        mutableIntStateOf(LoadingIndicatorStyle.sanitizeNeonColor(config.loadingIndicatorNeonColor))
    }
    var indicatorSize by remember(config.loadingIndicatorSizePercent) {
        mutableIntStateOf(LoadingIndicatorStyle.sanitizeSizePercent(config.loadingIndicatorSizePercent))
    }

    AppScaffold(
        topBar = { AppTopBar(title = "Appearance", onBack = onBack) }
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
                ScreenIntro("Pick how Prompt AI looks, and how it behaves while it is working.")
            }

            item { SectionHeader(title = "Theme") }
            item {
                GroupCard {
                    listOf(
                        Triple(AppThemeMode.SYSTEM, "System", "Follow your device's light or dark setting."),
                        Triple(AppThemeMode.LIGHT, "Light", "Warm paper, white cards, deep lime accent."),
                        Triple(AppThemeMode.DARK, "Dark", "Deep charcoal with raised surfaces."),
                        Triple(AppThemeMode.AMOLED, "AMOLED black", "True black for OLED panels.")
                    ).forEachIndexed { index, (mode, title, description) ->
                        ChoiceRow(
                            title = title,
                            description = description,
                            selected = AppThemeMode.sanitize(themeMode) == mode,
                            onSelect = { onThemeModeChange(mode) },
                            useRadio = false,
                            trailing = { ThemeSwatch(mode) }
                        )
                        if (index != 3) RowDivider()
                    }
                }
            }

            item { SectionHeader(title = "Floating controls") }
            item {
                GroupCard {
                    SwitchRow(
                        title = "Undo button",
                        description = "Offers a one-tap undo right after text is replaced.",
                        checked = enableUndoOverlay,
                        onCheckedChange = {
                            enableUndoOverlay = it
                            onSave(config.copy(enableUndoOverlay = it))
                        }
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Loading indicator",
                        description = "A small overlay while the model is answering.",
                        checked = enableLoadingOverlay,
                        onCheckedChange = {
                            enableLoadingOverlay = it
                            onSave(config.copy(enableLoadingOverlay = it))
                        }
                    )
                }
            }

            if (enableLoadingOverlay) {
                item {
                    IndicatorPreview(
                        styleId = loadingStyle,
                        color = indicatorColor,
                        neon = indicatorNeon,
                        neonColor = indicatorNeonColor,
                        sizePercent = indicatorSize
                    )
                }

                item { SectionHeader(title = "Indicator style") }
                item {
                    GroupCard {
                        LOADING_STYLES.forEachIndexed { index, option ->
                            ChoiceRow(
                                title = option.name,
                                description = option.description,
                                selected = loadingStyle == option.id,
                                useRadio = false,
                                onSelect = {
                                    loadingStyle = option.id
                                    onSave(config.copy(loadingIndicatorStyle = option.id))
                                }
                            )
                            if (index != LOADING_STYLES.lastIndex) RowDivider()
                        }
                    }
                }

                if (loadingStyle == LoadingIndicatorStyle.STYLE_SPECTRUM) {
                    item { SectionHeader(title = "Neon colour") }
                    item {
                        NeonColourCard(
                            color = indicatorNeonColor,
                            onColorChange = { picked ->
                                val safe = LoadingIndicatorStyle.ensureOpaque(picked)
                                indicatorNeonColor = safe
                                onSave(config.copy(loadingIndicatorNeonColor = safe))
                            }
                        )
                    }
                } else {
                    item {
                        GroupCard {
                            SwitchRow(
                                title = "Neon glow",
                                description = "Lights the indicator up in the colour you pick below.",
                                checked = indicatorNeon,
                                onCheckedChange = {
                                    indicatorNeon = it
                                    onSave(config.copy(loadingIndicatorNeon = it))
                                }
                            )
                        }
                    }

                    if (indicatorNeon) {
                        item { SectionHeader(title = "Neon colour") }
                        item {
                            NeonColourCard(
                                color = indicatorNeonColor,
                                onColorChange = { picked ->
                                    val safe = LoadingIndicatorStyle.ensureOpaque(picked)
                                    indicatorNeonColor = safe
                                    onSave(config.copy(loadingIndicatorNeonColor = safe))
                                }
                            )
                        }
                    } else {
                        item { SectionHeader(title = "Indicator colour") }
                        item {
                            IndicatorColorCard(
                                color = indicatorColor,
                                onColorChange = { picked ->
                                    val safe = LoadingIndicatorStyle.ensureOpaque(picked)
                                    indicatorColor = safe
                                    onSave(config.copy(loadingIndicatorColor = safe))
                                }
                            )
                        }
                    }
                }

                item { SectionHeader(title = "Indicator size") }
                item {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Size",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "$indicatorSize%",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Slider(
                            value = indicatorSize.toFloat(),
                            onValueChange = { indicatorSize = it.toInt() },
                            onValueChangeFinished = {
                                onSave(config.copy(loadingIndicatorSizePercent = indicatorSize))
                            },
                            valueRange = LoadingIndicatorStyle.MIN_SIZE_PERCENT.toFloat()..
                                LoadingIndicatorStyle.MAX_SIZE_PERCENT.toFloat(),
                            steps = 14
                        )
                        Text(
                            text = "100% is the original size and applies to every style.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * A live panel showing the chosen style at the chosen size, on the dark bubble the overlay actually
 * draws itself on. The size slider visibly moves the indicator, which is the point.
 */
@Composable
private fun IndicatorPreview(
    styleId: String,
    color: Int,
    neon: Boolean,
    neonColor: Int,
    sizePercent: Int
) {
    val scale = LoadingIndicatorStyle.scaleOf(sizePercent)
    val animatedScale by animateFloatAsState(targetValue = scale, label = "indicatorScale")
    AppCard(tone = CardTone.Muted, contentPadding = PaddingValues(Spacing.lg)) {
        Text(
            text = "Live preview",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.md))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(Color(0xFF141518)),
            contentAlignment = Alignment.Center
        ) {
            LoadingStylePreview(
                styleId = styleId,
                pickedColor = color,
                neonColor = neonColor,
                neon = neon,
                scale = animatedScale
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = LOADING_STYLES.firstOrNull { it.id == styleId }?.name ?: styleId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "$sizePercent%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The neon colour picker: the vivid swatches the spectrum is built from, plus a hex field for any
 * colour at all. The spectrum card underneath shows what the chosen hue actually produces.
 */
@Composable
private fun NeonColourCard(
    color: Int,
    onColorChange: (Int) -> Unit
) {
    var hexDraft by remember(color) { mutableStateOf(LoadingIndicatorStyle.toHexRgb(color)) }
    val hexIsValid = LoadingIndicatorStyle.parseHexColor(hexDraft) != null

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text(
                    text = "Current colour",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = LoadingIndicatorStyle.toHexRgb(color).uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            LoadingIndicatorStyle.NEON_PALETTE.forEach { preset ->
                val selected = preset == color
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(preset))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                        .clickable { onColorChange(preset) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color(LoadingIndicatorStyle.contrastColor(preset)),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        AppTextField(
            value = hexDraft,
            onValueChange = { typed ->
                hexDraft = typed
                LoadingIndicatorStyle.parseHexColor(typed)?.let { onColorChange(it) }
            },
            label = "Any neon colour (hex)",
            supporting = if (hexIsValid) "Applied to the indicator" else "Format: #RRGGBB",
            isError = hexDraft.isNotBlank() && !hexIsValid,
            monospace = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
        )

        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = "Spectrum",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = "The ring cycles through these shades of your colour.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            LoadingIndicatorStyle.neonSpectrum(color).forEach { neon ->
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(neon))
                )
            }
        }
    }
}

/**
 * Colour picker for the indicator: quick swatches in the app's own palette, a hex field for any
 * colour at all, and a mixer for fine tuning.
 */
@Composable
private fun IndicatorColorCard(
    color: Int,
    onColorChange: (Int) -> Unit
) {
    var mixerVisible by remember { mutableStateOf(false) }
    var hexDraft by remember(color) { mutableStateOf(LoadingIndicatorStyle.toHexRgb(color)) }
    val hexIsValid = LoadingIndicatorStyle.parseHexColor(hexDraft) != null

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text(
                    text = "Current colour",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = LoadingIndicatorStyle.toHexRgb(color).uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            LoadingIndicatorStyle.PRESET_COLORS.forEach { preset ->
                val selected = preset == color
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(preset))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                        .clickable { onColorChange(preset) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color(LoadingIndicatorStyle.contrastColor(preset)),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        AppTextField(
            value = hexDraft,
            onValueChange = { typed ->
                hexDraft = typed
                LoadingIndicatorStyle.parseHexColor(typed)?.let { onColorChange(it) }
            },
            label = "Any colour (hex)",
            supporting = if (hexIsValid) "Applied to the indicator" else "Format: #RRGGBB",
            isError = hexDraft.isNotBlank() && !hexIsValid,
            monospace = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
        )

        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mix a custom colour",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Slide hue, saturation and brightness.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            GhostButton(
                text = if (mixerVisible) "Hide" else "Show",
                onClick = { mixerVisible = !mixerVisible }
            )
        }

        if (mixerVisible) {
            val hsv = remember(color) {
                FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
            }
            var hue by remember(hsv) { mutableFloatStateOf(hsv[0]) }
            var saturation by remember(hsv) { mutableFloatStateOf(hsv[1]) }
            var brightness by remember(hsv) { mutableFloatStateOf(hsv[2]) }

            fun applyMixer() {
                onColorChange(
                    android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
                )
            }

            Spacer(Modifier.height(Spacing.md))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                                Color.Blue, Color.Magenta, Color.Red
                            )
                        )
                    )
            )
            Spacer(Modifier.height(Spacing.md))
            MixerSlider(
                label = "Hue ${hue.toInt()}°",
                value = hue,
                range = 0f..360f,
                onChange = { hue = it; applyMixer() }
            )
            MixerSlider(
                label = "Saturation ${(saturation * 100).toInt()}%",
                value = saturation,
                range = 0f..1f,
                onChange = { saturation = it; applyMixer() }
            )
            MixerSlider(
                label = "Brightness ${(brightness * 100).toInt()}%",
                value = brightness,
                range = 0f..1f,
                onChange = { brightness = it; applyMixer() }
            )
        }
    }
}

@Composable
private fun MixerSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range
    )
}

/**
 * A miniature of one appearance: canvas, card and accent dot. Enough to tell the four apart without
 * a screenshot, and it follows the theme it represents, not the one you are currently in.
 */
@Composable
private fun ThemeSwatch(mode: String) {
    val palettes = if (mode == AppThemeMode.SYSTEM) {
        listOf(
            ThemePalettes.surfacesFor(AppThemeMode.LIGHT),
            ThemePalettes.surfacesFor(AppThemeMode.DARK)
        )
    } else {
        listOf(ThemePalettes.surfacesFor(mode))
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        palettes.forEach { palette ->
            Box(
                modifier = Modifier
                    .size(width = 22.dp, height = 32.dp)
                    .background(Color(palette.background))
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 5.dp, top = 5.dp)
                        .size(width = 12.dp, height = 16.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(palette.surfaceContainerHigh))
                )
                Box(
                    modifier = Modifier
                        .padding(start = 5.dp, top = 23.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            if (mode == AppThemeMode.LIGHT ||
                                (mode == AppThemeMode.SYSTEM && palette == ThemePalettes.LIGHT)
                            ) {
                                Color(com.typeassist.app.ui.LimeScale.LIME_700)
                            } else {
                                Color(com.typeassist.app.ui.LimeScale.LIME_300)
                            }
                        )
                )
            }
        }
    }
}
