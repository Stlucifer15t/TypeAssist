package com.typeassist.app.ui

import android.content.Context
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.gson.GsonBuilder
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.ConfigMigrations
import com.typeassist.app.data.LoadingIndicatorStyle
import com.typeassist.app.data.ModelSelectionPreferences
import com.typeassist.app.data.SecretStore
import com.typeassist.app.data.Secrets
import com.typeassist.app.data.createDefaultConfig
import com.typeassist.app.data.mergeDuplicateProfiles
import com.typeassist.app.data.model.GitHubRelease
import com.typeassist.app.ui.components.hairlineColor
import com.typeassist.app.ui.screens.AppearanceSettingsScreen
import com.typeassist.app.ui.screens.BackupScreen
import com.typeassist.app.ui.screens.BehaviourSettingsScreen
import com.typeassist.app.ui.screens.CommandLibraryScreen
import com.typeassist.app.ui.screens.CommandsScreen
import com.typeassist.app.ui.screens.DidYouKnowScreen
import com.typeassist.app.ui.screens.GenerationSettingsScreen
import com.typeassist.app.ui.screens.GuideScreen
import com.typeassist.app.ui.screens.HistoryScreen
import com.typeassist.app.ui.screens.HomeScreen
import com.typeassist.app.ui.screens.LocalLlmScreen
import com.typeassist.app.ui.screens.PermissionsScreen
import com.typeassist.app.ui.screens.ProviderSettingsScreen
import com.typeassist.app.ui.screens.SettingsScreen
import com.typeassist.app.ui.screens.SnippetsScreen
import com.typeassist.app.ui.screens.SystemPreviewScreen
import com.typeassist.app.ui.screens.TestScreen
import com.typeassist.app.ui.screens.UsageScreen
import com.typeassist.app.ui.screens.WelcomeScreen
import okhttp3.OkHttpClient

/**
 * ============================== NAVIGATION ==============================
 *
 * Five destinations sit in the bottom bar - Home, Commands, Snippets, History, Settings - because
 * those are the places people come back to. Everything else is one tap away and never more than two
 * levels deep: the command gallery opens from Commands, backup/test/guide/tips open from Home, and
 * each Settings area opens from the Settings hub. Every screen below a tab has a back arrow, and
 * the system back button walks the same path.
 *
 * Navigation is driven by [androidx.navigation.compose.NavHost] with a real back stack, replacing
 * the hand-rolled currentScreen/previousScreen state machine. Tab taps replace the stack root;
 * sub-screens push onto the stack; the system back button pops it - including state restoration
 * after process death.
 */
private data class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val TAB_DESTINATIONS = listOf(
    AppDestination("home", "Home", Icons.Default.Home),
    AppDestination("commands", "Commands", Icons.Default.Bolt),
    AppDestination("snippets", "Snippets", Icons.Default.BookmarkBorder),
    AppDestination("history", "History", Icons.Default.Schedule),
    AppDestination("settings", "Settings", Icons.Default.Settings)
)

/** A tab the bottom bar knows about, i.e. a top-level screen. */
private fun isTab(route: String?): Boolean = TAB_DESTINATIONS.any { it.route == route }

/**
 * Brings a config up to the current shape: applies pending migrations, drops duplicate saved
 * profiles (one row per endpoint + key) and duplicate model history entries.
 * Returns the same instance when nothing had to change, so callers can detect it cheaply.
 */
private fun cleanConfig(config: AppConfig): AppConfig {
    val migrated = ConfigMigrations.apply(config)
    val sanitizedPreferences = ModelSelectionPreferences.sanitize(migrated.modelPreferences)
    val withPreferences =
        if (sanitizedPreferences == migrated.modelPreferences) migrated
        else migrated.copy(modelPreferences = sanitizedPreferences)
    return mergeDuplicateProfiles(withPreferences)
}

/**
 * The app's only bottom bar. Flat, hairline-topped, and the selected item is marked with a soft
 * lime capsule behind its icon - no ripple-heavy Material navigation bar, no raised surface.
 */
@Composable
private fun AppBottomBar(
    currentRoute: String,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(hairlineColor())
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TAB_DESTINATIONS.forEach { destination ->
                    val selected = destination.route == currentRoute
                    val tint = if (selected) MaterialTheme.extra.onAccentSoft
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(destination.route) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(width = 44.dp, height = 26.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(
                                        if (selected) MaterialTheme.extra.accentSoft
                                        else MaterialTheme.colorScheme.background
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else tint,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else tint
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TypeAssistApp(
    client: OkHttpClient,
    updateInfo: GitHubRelease?,
    themeMode: String,
    onThemeModeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val gson = GsonBuilder().setPrettyPrinting().create()
    val prefs = context.getSharedPreferences("GeminiConfig", Context.MODE_PRIVATE)
    // Keys are encrypted with a keystore-backed key when they are written, and opened again when
    // they are read; the in-memory config always holds them in plain text.
    val secretStore = remember { SecretStore() }

    // Determine the start destination: onboarding the first time, Home afterwards.
    val hasSeenOnboarding = prefs.getBoolean("has_seen_onboarding", false)
    val startDestination = if (hasSeenOnboarding) "home" else "welcome"

    // Load the config once; every screen below receives the same instance, updated by saveConfig.
    var config by remember {
        mutableStateOf(try {
            val json = prefs.getString("config_json", null)
            if (json != null) {
                val loadedConfig = gson.fromJson(json, AppConfig::class.java)
                // Handle missing fields from older versions
                ConfigMigrations.apply(LoadingIndicatorStyle.sanitize(loadedConfig))
                if (loadedConfig.savedCustomConfigs == null) loadedConfig.savedCustomConfigs = mutableListOf()
                if (loadedConfig.savedGeminiConfigs == null) loadedConfig.savedGeminiConfigs = mutableListOf()
                if (loadedConfig.savedCloudflareConfigs == null) loadedConfig.savedCloudflareConfigs = mutableListOf()
                if (loadedConfig.savedLocalModels == null) loadedConfig.savedLocalModels = mutableListOf()
                if (loadedConfig.modelPreferences == null) loadedConfig.modelPreferences = mutableListOf()
                if (loadedConfig.triggers == null) loadedConfig.triggers = createDefaultConfig().triggers
                if (loadedConfig.inlineCommands == null) loadedConfig.inlineCommands = createDefaultConfig().inlineCommands
                if (loadedConfig.customApiConfig == null) loadedConfig.customApiConfig = com.typeassist.app.data.CustomApiConfig()
                if (loadedConfig.cloudflareConfig == null) loadedConfig.cloudflareConfig = com.typeassist.app.data.CloudflareConfig()
                if (loadedConfig.localLlmConfig == null) loadedConfig.localLlmConfig = com.typeassist.app.data.LocalLlmConfig()
                if (loadedConfig.snippets == null) loadedConfig.snippets = mutableListOf()
                // Migration: convert old single content to the contents list
                loadedConfig.snippets?.forEach { snippet ->
                    if (snippet.contents == null) snippet.contents = mutableListOf()
                    if (snippet.content != null && snippet.content!!.isNotEmpty()) {
                        if (!snippet.contents.contains(snippet.content!!)) {
                            snippet.contents.add(snippet.content!!)
                        }
                        snippet.content = ""
                    }
                }
                // Migration: drop duplicate saved profiles / model history from older configs,
                // and persist the cleaned config so the duplicates stay gone.
                val cleanedConfig = cleanConfig(loadedConfig)
                // The keys on disk are encrypted (SecretStore); hand the app the plain values.
                val plainConfig = Secrets.decryptConfig(cleanedConfig, secretStore::decrypt)
                // A config written by an older version still carries plain keys: persist the
                // encrypted form now, so the migration happens once, right here. The dedupe
                // migration is persisted in the same write.
                if (cleanedConfig !== loadedConfig || Secrets.hasPlainSecrets(cleanedConfig)) {
                    prefs.edit().putString(
                        "config_json",
                        gson.toJson(Secrets.encryptConfig(plainConfig, secretStore::encrypt))
                    ).apply()
                }
                plainConfig
            } else createDefaultConfig()
        } catch (e: Exception) {
            createDefaultConfig()
        })
    }

    fun saveConfig(newConfig: AppConfig) {
        val cleaned = cleanConfig(newConfig)
        config = cleaned
        prefs.edit().putString(
            "config_json",
            gson.toJson(Secrets.encryptConfig(cleaned, secretStore::encrypt))
        ).apply()
    }

    val navController = rememberNavController()

    // Observe the current route so the bottom bar knows which tab to highlight.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = isTab(currentRoute)

    // Tab navigation replaces the stack: tapping a tab always lands on that tab's root,
    // and the tab's own state (scroll position, selected list) is kept across switches.
    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Sub-screen navigation pushes onto the back stack; the system back button pops it.
    fun navigateTo(route: String) {
        navController.navigate(route)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(currentRoute = currentRoute ?: "home") { navigateToTab(it) }
            }
        }
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(170)) +
                    slideInHorizontally(animationSpec = tween(200)) { it / 28 }
            },
            exitTransition = {
                fadeOut(animationSpec = tween(120))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(170))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(120)) +
                    slideOutHorizontally(animationSpec = tween(170)) { width -> width / 28 }
            }
        ) {
            composable("welcome") {
                WelcomeScreen(
                    onFinished = {
                        prefs.edit().putBoolean("has_seen_onboarding", true).apply()
                        navController.navigate("home") {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable("permissions") {
                PermissionsScreen(
                    onFinished = { navController.navigate("settings") },
                    isStandalone = true
                )
            }

            composable("home") {
                HomeScreen(
                    config = config,
                    context = context,
                    updateInfo = updateInfo,
                    onToggle = { newState ->
                        saveConfig(config.copy(isAppEnabled = newState))
                    },
                    onNavigate = { route -> navController.navigate(route) }
                )
            }

            composable("commands") {
                CommandsScreen(
                    config = config,
                    onSave = { saveConfig(it) },
                    onBack = { navController.popBackStack() },
                    onNavigateLibrary = { navController.navigate("library") }
                )
            }

            composable("library") {
                CommandLibraryScreen(
                    config = config,
                    onSave = { saveConfig(it) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("snippets") {
                SnippetsScreen(
                    config = config,
                    onSave = { saveConfig(it) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("history") {
                HistoryScreen(
                    retentionMinutes = config.historyRetentionMinutes,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("settings") {
                SettingsScreen(
                    config = config,
                    onSave = { saveConfig(it) },
                    onNavigate = { navController.navigate(it) },
                    themeMode = themeMode
                )
            }

            composable("settings/appearance") {
                AppearanceSettingsScreen(
                    config = config,
                    onSave = { saveConfig(it) },
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("settings/provider") {
                ProviderSettingsScreen(
                    config = config,
                    client = client,
                    onSave = { saveConfig(it) },
                    onBack = { navController.popBackStack() },
                    onNavigateLocal = { navController.navigate("settings/local") },
                    onNavigateGeneration = { navController.navigate("settings/generation") },
                    onNavigateBehaviour = { navController.navigate("settings/behaviour") }
                )
            }

            composable("settings/generation") {
                GenerationSettingsScreen(
                    config = config,
                    onSave = { saveConfig(it) },
                    onBack = { navController.popBackStack() },
                    onNavigateLocal = { navController.navigate("settings/local") }
                )
            }

            composable("settings/usage") {
                UsageScreen(
                    onBack = { navController.popBackStack() },
                    onOpenGeneration = { navController.navigate("settings/generation") },
                    onOpenProviders = { navController.navigate("settings/provider") }
                )
            }

            composable("settings/behaviour") {
                BehaviourSettingsScreen(
                    config = config,
                    onSave = { saveConfig(it) },
                    onNavigate = { navController.navigate(it) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("settings/local") {
                LocalLlmScreen(
                    config = config,
                    onSave = { saveConfig(it) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("backup") {
                BackupScreen(
                    config = config,
                    onSave = { saveConfig(it) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("guide") {
                GuideScreen(
                    config = config,
                    onBack = { navController.popBackStack() },
                    onOpenTest = { navController.navigate("test") },
                    onOpenCommands = { navController.navigate("commands") }
                )
            }

            composable("preview") {
                SystemPreviewScreen(
                    config = config,
                    onBack = { navController.popBackStack() },
                    onOpenAppearance = { navController.navigate("settings/appearance") }
                )
            }

            composable("did_you_know") {
                DidYouKnowScreen(
                    config = config,
                    onFinished = {
                        prefs.edit().putInt("did_you_know_version", 2).apply()
                        navController.navigate("home") {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable("test") {
                TestScreen(
                    config = config,
                    onStartTest = { prefs.edit().putBoolean("is_testing_active", true).apply() },
                    onStopTest = { prefs.edit().putBoolean("is_testing_active", false).apply() },
                    onOpenPermissions = { navController.navigate("permissions") },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
