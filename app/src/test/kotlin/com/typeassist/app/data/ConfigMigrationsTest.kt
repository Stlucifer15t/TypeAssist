package com.typeassist.app.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigMigrationsTest {

    /**
     * A config exactly as it is loaded from an installed version that did not know about the
     * result chip, streaming or screen context: Gson skips the constructor, so those fields
     * arrive as zero values (false / null) instead of their Kotlin defaults.
     */
    private fun legacyConfig(): AppConfig = Gson().fromJson(
        """
        {
          "isAppEnabled": true,
          "provider": "custom",
          "apiKey": "sk-legacy",
          "model": "gpt-4o",
          "isHistoryEnabled": true,
          "loadingIndicatorStyle": "dots"
        }
        """.trimIndent(),
        AppConfig::class.java
    )

    @Test
    fun legacyConfigsGetTheNewFeatureDefaults() {
        val migrated = ConfigMigrations.apply(legacyConfig())

        assertEquals(CURRENT_CONFIG_VERSION, migrated.configVersion)
        assertTrue(migrated.showResultChip)
        assertTrue(migrated.streamResponses)
        assertTrue(migrated.screenContextChatLabels)
        // Opt-in by design: screen reading must never switch itself on.
        assertFalse(migrated.screenContextEnabled)
        assertTrue(migrated.screenContextBlockedPackages.contains("com.bitwarden.android"))
        assertTrue(migrated.screenContextBlockedPackages.contains("com.chase.sig.android"))
        // Everything the user had chosen before is untouched.
        assertTrue(migrated.isAppEnabled)
        assertEquals("sk-legacy", migrated.apiKey)
        assertEquals("dots", migrated.loadingIndicatorStyle)
    }

    @Test
    fun currentConfigsKeepTheirChosenValues() {
        val config = createDefaultConfig().copy(
            showResultChip = false,
            streamResponses = false,
            screenContextChatLabels = false,
            screenContextBlockedPackages = mutableListOf("com.example.bank")
        )

        val migrated = ConfigMigrations.apply(config)

        assertSame(config, migrated)
        assertFalse(migrated.showResultChip)
        assertFalse(migrated.streamResponses)
        assertFalse(migrated.screenContextChatLabels)
        assertEquals(listOf("com.example.bank"), migrated.screenContextBlockedPackages)
    }

    @Test
    fun aMissingBlocklistIsRestoredForCurrentConfigsToo() {
        // Another Gson-shaped config, this time carrying the current version number but no list.
        val config = Gson().fromJson(
            """{"configVersion":$CURRENT_CONFIG_VERSION,"showResultChip":false}""",
            AppConfig::class.java
        )

        val migrated = ConfigMigrations.apply(config)

        assertTrue(migrated.screenContextBlockedPackages.isNotEmpty())
        // The chip stays off: that choice belongs to the user.
        assertFalse(migrated.showResultChip)
    }

    @Test
    fun aNewerConfigVersionIsNotDowngraded() {
        val config = createDefaultConfig().copy(configVersion = CURRENT_CONFIG_VERSION + 5)

        val migrated = ConfigMigrations.apply(config)

        assertEquals(CURRENT_CONFIG_VERSION + 5, migrated.configVersion)
    }
}
