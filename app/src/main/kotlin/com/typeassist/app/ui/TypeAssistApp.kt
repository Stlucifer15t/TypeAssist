package com.typeassist.app.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.gson.GsonBuilder
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.LoadingIndicatorStyle
import com.typeassist.app.data.createDefaultConfig
import com.typeassist.app.data.model.GitHubRelease
import com.typeassist.app.ui.screens.*
import okhttp3.OkHttpClient

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TypeAssistApp(client: OkHttpClient, updateInfo: GitHubRelease?) {
    val context = LocalContext.current
    val gson = GsonBuilder().setPrettyPrinting().create()
    val prefs = context.getSharedPreferences("GeminiConfig", Context.MODE_PRIVATE)

    // Determine initial screen
    val hasSeenOnboarding = prefs.getBoolean("has_seen_onboarding", false)
    var currentScreen by rememberSaveable { mutableStateOf(if (hasSeenOnboarding) "home" else "welcome") }
    var previousScreen by rememberSaveable { mutableStateOf("home") } // Track previous screen for animation

    var config by remember(currentScreen) { 
        mutableStateOf(try {
            val json = prefs.getString("config_json", null)
            if (json != null) {
                val loadedConfig = gson.fromJson(json, AppConfig::class.java)
                // Handle missing fields from older versions
                LoadingIndicatorStyle.sanitize(loadedConfig)
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
                if (loadedConfig.snippets == null) {
                    loadedConfig.snippets = mutableListOf()
                }
                // Migration: Convert old single content to contents list
                loadedConfig.snippets?.forEach { snippet ->
                    if (snippet.contents == null) snippet.contents = mutableListOf()
                    if (snippet.content != null && snippet.content!!.isNotEmpty()) {
                        if (!snippet.contents.contains(snippet.content!!)) {
                            snippet.contents.add(snippet.content!!)
                        }
                        snippet.content = ""
                    }
                }
                loadedConfig
            } else createDefaultConfig()
        } catch (e: Exception) { createDefaultConfig() })
    }

    fun saveConfig(newConfig: AppConfig) {
        val sanitized = newConfig.copy(appThemeMode = com.typeassist.app.data.AppThemeMode.sanitize(newConfig.appThemeMode))
        val merged = com.typeassist.app.data.mergeDuplicateProfiles(sanitized)
        config = merged
        com.typeassist.app.ui.ThemeController.syncFrom(merged)
        prefs.edit().putString("config_json", gson.toJson(merged)).apply()
    }

    // Custom navigate function to track previous screen
    val navigateTo: (String) -> Unit = { screen ->
        previousScreen = currentScreen
        currentScreen = screen
    }

    BackHandler(enabled = currentScreen != "home" && currentScreen != "welcome") {
        if (currentScreen == "library") {
            navigateTo("commands")
        } else {
            navigateTo("home")
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = currentScreen,
            label = "Screen Animation",
            transitionSpec = {
                val isBackTransition = (targetState == "home" && previousScreen != "home") ||
                    (targetState == "commands" && previousScreen == "library") ||
                    (targetState == "settings" && previousScreen == "permissions")
                val direction = if (isBackTransition) -1 else 1
                (fadeIn(animationSpec = tween(150)) +
                    slideInHorizontally(animationSpec = tween(180)) { width -> direction * width / 24 }) togetherWith
                    (fadeOut(animationSpec = tween(120)) +
                        slideOutHorizontally(animationSpec = tween(150)) { width -> -direction * width / 24 })
            }
        ) { screen ->
            val route = screen.substringBefore(":")
            when (route) {
                "welcome" -> WelcomeScreen(
                    onFinished = {
                        prefs.edit().putBoolean("has_seen_onboarding", true).apply()
                        navigateTo("home")
                    }
                )
                "permissions" -> PermissionsScreen(
                    onFinished = { navigateTo("settings:0") },
                    isStandalone = true
                )
                "home" -> HomeScreen(
                    config = config,
                    context = context,
                    updateInfo = updateInfo, // Pass updateInfo down
                    onToggle = { newState -> 
                        saveConfig(config.copy(isAppEnabled = newState)) 
                    },
                    onNavigate = { navigateTo(it) } // Use custom navigate
                )
                "commands" -> CommandsScreen(config, { saveConfig(it) }, { navigateTo("home") }, onNavigateLibrary = { navigateTo("library") })
                "library" -> CommandLibraryScreen(config, { saveConfig(it) }, { navigateTo("commands") })
                "settings" -> {
                    val tab = try { screen.split(":")[1].toInt() } catch (e: Exception) { 0 }
                    SettingsScreen(
                        config = config,
                        client = client,
                        onSave = { saveConfig(it) },
                        onBack = { navigateTo("home") },
                        onNavigate = { navigateTo(it) },
                        initialTab = tab
                    )
                }
                "json" -> JsonScreen(config, { saveConfig(it) }, { navigateTo("home") }) // Use custom navigate
                "history" -> HistoryScreen({ navigateTo("home") }) // Use custom navigate
                "usage" -> UsageScreen(onBack = { navigateTo("home") })
                "snippets" -> SnippetsScreen(config, { saveConfig(it) }, { navigateTo("home") })
                "guide" -> GuideScreen({ navigateTo("home") })
                "did_you_know" -> DidYouKnowScreen(onFinished = {
                    prefs.edit().putInt("did_you_know_version", 2).apply()
                    navigateTo("home")
                })
                "test" -> TestScreen(
                    onStartTest = { prefs.edit().putBoolean("is_testing_active", true).apply() },
                    onStopTest = { prefs.edit().putBoolean("is_testing_active", false).apply() },
                    onBack = { navigateTo("home") } // Use custom navigate
                )
            }
        }
    }
}