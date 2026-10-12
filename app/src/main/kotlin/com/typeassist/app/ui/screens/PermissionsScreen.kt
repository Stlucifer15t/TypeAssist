package com.typeassist.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.typeassist.app.ui.Spacing
import com.typeassist.app.ui.components.AppCard
import com.typeassist.app.ui.components.AppDialog
import com.typeassist.app.ui.components.AppScaffold
import com.typeassist.app.ui.components.AppTopBar
import com.typeassist.app.ui.components.GroupCard
import com.typeassist.app.ui.components.NoticeCard
import com.typeassist.app.ui.components.NoticeTone
import com.typeassist.app.ui.components.PrimaryButton
import com.typeassist.app.ui.components.RowDivider
import com.typeassist.app.ui.components.ScreenIntro
import com.typeassist.app.ui.components.SectionHeader
import com.typeassist.app.ui.components.SettingRow
import com.typeassist.app.ui.components.StatusPill
import com.typeassist.app.ui.components.StatusTone
import com.typeassist.app.ui.extra
import com.typeassist.app.utils.XiaomiUtils

/**
 * ============================== PERMISSIONS ==============================
 *
 * Two groups, clearly separated: the one permission the app cannot work without, and the settings
 * that keep Android from pausing it in the background. Every row shows its own state and opens the
 * exact system screen it needs, so this list can be worked through without guesswork.
 */
@Composable
fun PermissionsScreen(
    onFinished: () -> Unit,
    isStandalone: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isNotificationsEnabled by remember { mutableStateOf(false) }
    var isBatteryOptimized by remember { mutableStateOf(true) }
    val isXiaomi = remember { XiaomiUtils.isXiaomi() }
    var isXiaomiBgStartEnabled by remember { mutableStateOf(false) }
    var showSkipDialog by remember { mutableStateOf(false) }

    fun checkPermissions() {
        val prefString = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        isAccessibilityEnabled =
            prefString?.contains("${context.packageName}/com.typeassist.app.service.MyAccessibilityService") == true

        isNotificationsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        isBatteryOptimized = !powerManager.isIgnoringBatteryOptimizations(context.packageName)

        if (isXiaomi) {
            isXiaomiBgStartEnabled = XiaomiUtils.hasBackgroundStartPermission(context)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { checkPermissions() }

    val recommendedGranted = isNotificationsEnabled && !isBatteryOptimized &&
        (!isXiaomi || isXiaomiBgStartEnabled)

    AppScaffold(
        topBar = {
            if (isStandalone) {
                AppTopBar(title = "Permissions", onBack = onFinished)
            } else {
                AppTopBar(title = "Set up Prompt AI")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    top = Spacing.sm,
                    bottom = Spacing.lg
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                item {
                    ScreenIntro(
                        if (isStandalone) {
                            "Check that the service still has what it needs. Android may revoke " +
                                "these after an update or a long period of inactivity."
                        } else {
                            "Accessibility is the one permission Prompt AI needs. The rest keep " +
                                "Android from pausing the service in the background."
                        }
                    )
                }

                item {
                    AppCard(
                        tone = if (isAccessibilityEnabled) com.typeassist.app.ui.components.CardTone.Surface
                        else com.typeassist.app.ui.components.CardTone.Warning
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Accessibility service",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(Spacing.xs))
                                StatusPill(
                                    text = if (isAccessibilityEnabled) "Running" else "Off",
                                    tone = if (isAccessibilityEnabled) StatusTone.Active else StatusTone.Warning
                                )
                            }
                        }
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            text = if (isAccessibilityEnabled) {
                                "Prompt AI can read the field you are typing in and replace the text " +
                                    "with the result. It only looks at the field that has focus."
                            } else {
                                "Without this, nothing works: the app cannot read the text you are " +
                                    "writing or put the answer back. Find “Prompt AI Accessibility " +
                                    "Service” in the list and turn it on."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Spacing.md))
                        PrimaryButton(
                            text = if (isAccessibilityEnabled) "Open accessibility settings"
                            else "Turn on accessibility",
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    SectionHeader(
                        title = "Recommended for stability",
                        description = "Voluntary, but the service may stop without them."
                    )
                }
                item {
                    GroupCard {
                        PermissionStatusRow(
                            title = "Notifications",
                            description = "Android keeps a service alive more reliably when it can post a notification.",
                            granted = isNotificationsEnabled,
                            icon = Icons.Default.Notifications,
                            visible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                            onOpen = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                )
                            }
                        )
                        RowDivider()
                        PermissionStatusRow(
                            title = "Ignore battery optimisation",
                            description = "Stops the system from killing the service when the screen is off.",
                            granted = !isBatteryOptimized,
                            icon = Icons.Default.BatteryFull,
                            onOpen = {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                )
                            }
                        )
                        if (isXiaomi) {
                            RowDivider()
                            PermissionStatusRow(
                                title = "Xiaomi autostart & pop-ups",
                                description = "MIUI needs both enabled for the service to survive a restart.",
                                granted = isXiaomiBgStartEnabled,
                                icon = Icons.Default.PhoneAndroid,
                                onOpen = {
                                    XiaomiUtils.openAutostartSettings(context)
                                    XiaomiUtils.openPermissionSettings(context)
                                }
                            )
                        }
                    }
                }

                if (!recommendedGranted) {
                    item {
                        NoticeCard(
                            title = "Stability settings are incomplete",
                            message = "Prompt AI usually still works, but Android may pause it. If " +
                                "shortcuts stop responding, check this screen first.",
                            tone = NoticeTone.Warning,
                            icon = Icons.Default.Warning
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(Spacing.screen)) {
                PrimaryButton(
                    text = if (isStandalone) "Done" else "Continue",
                    onClick = {
                        if (recommendedGranted) onFinished() else showSkipDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isAccessibilityEnabled
                )
                if (!isAccessibilityEnabled) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = "Turn on the accessibility service to continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showSkipDialog) {
        AppDialog(
            title = "Continue without them?",
            message = "Prompt AI may stop working unexpectedly, especially after the screen has " +
                "been off for a while. You can grant these later in Settings → Reliability.",
            onDismiss = { showSkipDialog = false },
            confirmLabel = "Continue anyway",
            onConfirm = {
                showSkipDialog = false
                onFinished()
            },
            dismissLabel = "Go back",
            icon = Icons.Default.Warning,
            iconTint = MaterialTheme.extra.warning
        )
    }
}

/**
 * A recommended setting with its own state. Tapping anywhere on the row opens the system screen that
 * fixes it, which keeps the list to one tap per item.
 */
@Composable
private fun PermissionStatusRow(
    title: String,
    description: String,
    granted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onOpen: () -> Unit,
    visible: Boolean = true
) {
    if (!visible) return
    SettingRow(
        title = title,
        description = description,
        icon = icon,
        onClick = onOpen
    ) {
        Icon(
            imageVector = if (granted) Icons.Default.Check else Icons.Default.Warning,
            contentDescription = if (granted) "Granted" else "Not granted",
            tint = if (granted) MaterialTheme.extra.success else MaterialTheme.extra.warning,
            modifier = Modifier.size(20.dp)
        )
    }
}
