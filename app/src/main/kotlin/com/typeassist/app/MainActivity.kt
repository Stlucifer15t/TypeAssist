package com.typeassist.app

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.typeassist.app.data.model.GitHubRelease
import com.typeassist.app.data.repository.UpdateRepository
import com.typeassist.app.ui.AppTheme
import com.typeassist.app.ui.AppThemeMode
import com.typeassist.app.ui.TypeAssistApp
import com.typeassist.app.ui.components.UpdateDialog
import com.typeassist.app.ui.components.VersionAnnouncementDialog
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {

    private val client = OkHttpClient()
    private var updateInfoState by mutableStateOf<GitHubRelease?>(null)
    private var appThemeMode by mutableStateOf(AppThemeMode.SYSTEM)
    private var showVersionAnnouncement by mutableStateOf(false)
    private lateinit var updateRepository: UpdateRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val appPreferences = getSharedPreferences("GeminiConfig", Context.MODE_PRIVATE)
        appThemeMode = AppThemeMode.sanitize(
            appPreferences.getString("theme_mode", AppThemeMode.SYSTEM)
        )
        val updatePreferences = getSharedPreferences("UpdateInfo", Context.MODE_PRIVATE)
        showVersionAnnouncement = updatePreferences.getInt("announced_app_version_code", 0) < BuildConfig.VERSION_CODE

        updateRepository = UpdateRepository(this)

        if (BuildConfig.SHOW_UPDATES) {
            loadCachedUpdateInfo()
            checkForUpdates()
        }

        setContent {
            AppTheme(themeMode = appThemeMode) {
                TypeAssistApp(
                    client = client,
                    updateInfo = updateInfoState,
                    themeMode = appThemeMode,
                    onThemeModeChange = ::saveThemeMode
                )

                if (showVersionAnnouncement) {
                    VersionAnnouncementDialog(
                        versionName = BuildConfig.VERSION_NAME,
                        onDismiss = ::dismissVersionAnnouncement
                    )
                } else {
                    updateInfoState?.let { release ->
                        UpdateDialog(
                            release = release,
                            onDismiss = { dismissUpdate(release) }
                        )
                    }
                }
            }
        }
    }

    private fun saveThemeMode(mode: String) {
        val safeMode = AppThemeMode.sanitize(mode)
        getSharedPreferences("GeminiConfig", Context.MODE_PRIVATE)
            .edit()
            .putString("theme_mode", safeMode)
            .apply()
        appThemeMode = safeMode
    }

    private fun dismissVersionAnnouncement() {
        getSharedPreferences("UpdateInfo", Context.MODE_PRIVATE)
            .edit()
            .putInt("announced_app_version_code", BuildConfig.VERSION_CODE)
            .apply()
        showVersionAnnouncement = false
    }

    private fun dismissUpdate(release: GitHubRelease) {
        getSharedPreferences("UpdateInfo", Context.MODE_PRIVATE)
            .edit()
            .putString("dismissed_release_tag", release.tagName)
            .apply()
        if (updateInfoState?.tagName == release.tagName) updateInfoState = null
    }

    private fun isReleaseDismissed(tagName: String): Boolean =
        getSharedPreferences("UpdateInfo", Context.MODE_PRIVATE)
            .getString("dismissed_release_tag", null) == tagName

    private fun loadCachedUpdateInfo() {
        val prefs = getSharedPreferences("UpdateInfo", Context.MODE_PRIVATE)

        // Migration: Clear old update info key if it exists.
        if (prefs.contains("update_json")) {
            prefs.edit().remove("update_json").apply()
        }

        val json = prefs.getString("github_release_json", null) ?: return
        try {
            val info = Gson().fromJson(json, GitHubRelease::class.java)
            if (!info.tagName.isNullOrBlank() &&
                !info.htmlUrl.isNullOrBlank() &&
                isNewerThanInstalled(info.tagName) &&
                !isReleaseDismissed(info.tagName)
            ) {
                updateInfoState = info
            }
        } catch (_: Exception) {
            prefs.edit().remove("github_release_json").apply()
        }
    }

    private fun checkForUpdates() {
        lifecycleScope.launch {
            val result = updateRepository.checkForUpdate()
            val prefs = getSharedPreferences("UpdateInfo", Context.MODE_PRIVATE)

            result.onSuccess { release ->
                if (release != null) {
                    prefs.edit().putString("github_release_json", Gson().toJson(release)).apply()
                    updateInfoState = if (isReleaseDismissed(release.tagName)) null else release
                } else {
                    prefs.edit().remove("github_release_json").apply()
                    updateInfoState = null
                }
            }.onFailure {
                // Keep a valid cached release if the network is temporarily unavailable.
            }
        }
    }

    private fun isNewerThanInstalled(tagName: String): Boolean {
        val candidate = tagName.removePrefix("v").substringBefore("-")
            .split(".").map { it.toIntOrNull() ?: 0 }
        val installed = BuildConfig.VERSION_NAME.removePrefix("v").substringBefore("-")
            .split(".").map { it.toIntOrNull() ?: 0 }
        val length = maxOf(candidate.size, installed.size)

        for (index in 0 until length) {
            val candidatePart = candidate.getOrElse(index) { 0 }
            val installedPart = installed.getOrElse(index) { 0 }
            if (candidatePart != installedPart) return candidatePart > installedPart
        }
        return false
    }

    fun isAccessibilityEnabled(): Boolean {
        val prefString = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return prefString?.contains("$packageName/com.typeassist.app.service.MyAccessibilityService") == true
    }
}
