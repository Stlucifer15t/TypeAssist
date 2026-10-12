package com.typeassist.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.typeassist.app.BuildConfig
import com.typeassist.app.MainActivity
import com.typeassist.app.data.AppConfig
import com.typeassist.app.ui.AppThemeMode
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.components.AppScaffold
import com.typeassist.app.ui.components.AppTopBar
import com.typeassist.app.ui.components.GroupCard
import com.typeassist.app.ui.components.NavRow
import com.typeassist.app.ui.components.RowDivider
import com.typeassist.app.ui.components.SectionHeader
import com.typeassist.app.ui.components.SettingRow
import com.typeassist.app.ui.components.StatusPill
import com.typeassist.app.ui.components.StatusTone

/**
 * ============================== SETTINGS ==============================
 *
 * A hub, not a wall of switches. It states the state of the app (provider, appearance, permissions)
 * and links to the four areas that hold the controls. Everything that used to be a tab is now one
 * tap away and titled in plain language, so nothing is buried behind an unlabelled icon.
 */
@Composable
fun SettingsScreen(
    config: AppConfig,
    onSave: (AppConfig) -> Unit,
    onNavigate: (String) -> Unit,
    themeMode: String
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? MainActivity
    var accessibilityOn by remember { mutableStateOf(activity?.isAccessibilityEnabled() ?: false) }

    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityOn = activity?.isAccessibilityEnabled() ?: false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val providerLabel = when (config.provider) {
        "gemini" -> "Google Gemini"
        "cloudflare" -> "Cloudflare Workers AI"
        "custom" -> "OpenAI-compatible API"
        "local" -> "On-device model"
        else -> config.provider.replaceFirstChar { it.uppercase() }
    }
    val themeLabel = when (AppThemeMode.sanitize(themeMode)) {
        AppThemeMode.LIGHT -> "Light"
        AppThemeMode.DARK -> "Dark"
        AppThemeMode.AMOLED -> "AMOLED black"
        else -> "System"
    }

    AppScaffold(
        topBar = { AppTopBar(title = "Settings") }
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
            item { SectionHeader(title = "Assistant") }
            item {
                GroupCard {
                    NavRow(
                        title = "Provider & model",
                        description = "Which AI answers, and which model it uses.",
                        icon = Icons.Default.Bolt,
                        value = providerLabel,
                        onClick = { onNavigate("settings/provider") }
                    )
                    RowDivider()
                    NavRow(
                        title = "Behaviour",
                        description = "Replies, triggers, history, previews and privacy.",
                        icon = Icons.Default.Tune,
                        onClick = { onNavigate("settings/behaviour") }
                    )
                    RowDivider()
                    NavRow(
                        title = "Appearance",
                        description = "Theme, floating controls and the loading indicator.",
                        icon = Icons.Default.Palette,
                        value = themeLabel,
                        onClick = { onNavigate("settings/appearance") }
                    )
                }
            }

            item { SectionHeader(title = "Reliability") }
            item {
                GroupCard {
                    SettingRow(
                        title = "Permissions & service health",
                        description = "Accessibility, notifications and battery settings.",
                        icon = Icons.Default.Accessibility,
                        onClick = { onNavigate("permissions") }
                    ) {
                        StatusPill(
                            text = if (accessibilityOn) "Service on" else "Needs setup",
                            tone = if (accessibilityOn) StatusTone.Active else StatusTone.Warning
                        )
                    }
                }
            }

            item { SectionHeader(title = "Data") }
            item {
                GroupCard {
                    NavRow(
                        title = "Backup & restore",
                        description = "Export everything to a file, or bring it back.",
                        icon = Icons.Default.Code,
                        onClick = { onNavigate("backup") }
                    )
                }
            }

            item { SectionHeader(title = "Learn & about") }
            item {
                GroupCard {
                    NavRow(
                        title = "User guide",
                        description = "Every trigger, utility and shortcut.",
                        icon = Icons.Default.Info,
                        onClick = { onNavigate("guide") }
                    )
                    RowDivider()
                    NavRow(
                        title = "Tips & examples",
                        description = "Features worth knowing about.",
                        icon = Icons.Default.Lightbulb,
                        onClick = { onNavigate("did_you_know") }
                    )
                    RowDivider()
                    SettingRow(
                        title = "Version",
                        description = if (BuildConfig.SHOW_UPDATES) {
                            "Prompt AI ${BuildConfig.VERSION_NAME}"
                        } else {
                            "Prompt AI ${BuildConfig.VERSION_NAME} (F-Droid build)"
                        }
                    ) {
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RowDivider()
                    NavRow(
                        title = "Interface preview",
                        description = "Every colour, control and state in one place.",
                        icon = Icons.Default.Palette,
                        onClick = { onNavigate("preview") }
                    )
                    RowDivider()
                    NavRow(
                        title = "Source code",
                        description = "Prompt AI is open source (GPL-3.0).",
                        icon = Icons.Default.Code,
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/Stlucifer15t/TypeAssist")
                                )
                            )
                        }
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(top = Spacing.sm)) {
                    Spacer(Modifier.height(Spacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Changes save as you make them.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
