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

    /**
     * The same legacy config also predates the generation sliders, the screen-aware reply settings
     * and the longer history window, and Gson leaves all of those as null/0.
     */
    @Test
    fun legacyConfigsGetUsableGenerationAndReplySettings() {
        val migrated = ConfigMigrations.apply(legacyConfig())

        assertEquals(GenerationSettings.DEFAULT_TEMPERATURE, migrated.generationConfig.temperature, 0.0001)
        assertEquals(GenerationSettings.DEFAULT_TOP_P, migrated.generationConfig.topP, 0.0001)
        assertEquals(0, migrated.generationConfig.maxTokens)

        // One hour, not the five minutes the old build hardcoded.
        assertEquals(HistoryManager.DEFAULT_RETENTION_MINUTES, migrated.historyRetentionMinutes)
        assertEquals(ReplyStyle.TONE_MATCH, migrated.replyTone)
        assertEquals(ReplyStyle.LENGTH_SHORT, migrated.replyLength)
    }

    @Test
    fun aMissingGenerationObjectIsRebuilt() {
        val config = Gson().fromJson(
            """{"configVersion":$CURRENT_CONFIG_VERSION,"generationConfig":null}""",
            AppConfig::class.java
        )

        val migrated = ConfigMigrations.apply(config)

        assertEquals(GenerationSettings.defaults(), migrated.generationConfig)
    }

    @Test
    fun storedValuesOutOfRangeAreRepairedInsteadOfBeingSent() {
        val config = Gson().fromJson(
            """
            {
              "configVersion": $CURRENT_CONFIG_VERSION,
              "generationConfig": {"temperature": 42.0, "topP": -1.0, "maxTokens": -7},
              "historyRetentionMinutes": -3,
              "replyTone": "shouty",
              "replyLength": "epic"
            }
            """.trimIndent(),
            AppConfig::class.java
        )

        val migrated = ConfigMigrations.apply(config)

        assertEquals(GenerationSettings.MAX_TEMPERATURE, migrated.generationConfig.temperature, 0.0001)
        assertEquals(GenerationSettings.MIN_TOP_P, migrated.generationConfig.topP, 0.0001)
        assertEquals(0, migrated.generationConfig.maxTokens)
        assertEquals(HistoryManager.DEFAULT_RETENTION_MINUTES, migrated.historyRetentionMinutes)
        assertEquals(ReplyStyle.TONE_MATCH, migrated.replyTone)
        assertEquals(ReplyStyle.LENGTH_SHORT, migrated.replyLength)
    }

    @Test
    fun chosenGenerationAndReplySettingsSurviveAMigration() {
        val config = createDefaultConfig().copy(
            configVersion = CURRENT_CONFIG_VERSION,
            generationConfig = GenConfig(temperature = 0.9, topP = 1.0, maxTokens = 512),
            historyRetentionMinutes = 240,
            replyTone = ReplyStyle.TONE_FORMAL,
            replyLength = ReplyStyle.LENGTH_DETAILED
        )

        val migrated = ConfigMigrations.apply(config)

        assertSame(config, migrated)
        assertEquals(0.9, migrated.generationConfig.temperature, 0.0001)
        assertEquals(512, migrated.generationConfig.maxTokens)
        assertEquals(240, migrated.historyRetentionMinutes)
        assertEquals(ReplyStyle.TONE_FORMAL, migrated.replyTone)
        assertEquals(ReplyStyle.LENGTH_DETAILED, migrated.replyLength)
    }

    @Test
    fun theOldNeonStyleBecomesTheSpectrumRingWithTheNeonLookOn() {
        val legacy = Gson().fromJson(
            """{"configVersion":4,"loadingIndicatorStyle":"neon"}""",
            AppConfig::class.java
        )

        val migrated = ConfigMigrations.apply(legacy)

        assertEquals(LoadingIndicatorStyle.STYLE_SPECTRUM, migrated.loadingIndicatorStyle)
        assertTrue(migrated.loadingIndicatorNeon)
        // The colour the spectrum is built from falls back to the default hue.
        assertEquals(
            LoadingIndicatorStyle.DEFAULT_NEON_COLOR,
            migrated.loadingIndicatorNeonColor
        )
    }

    @Test
    fun aMissingNeonColourIsRestoredForCurrentConfigsToo() {
        val config = Gson().fromJson(
            """{"configVersion":$CURRENT_CONFIG_VERSION}""",
            AppConfig::class.java
        )

        val migrated = ConfigMigrations.apply(config)

        assertEquals(
            LoadingIndicatorStyle.DEFAULT_NEON_COLOR,
            migrated.loadingIndicatorNeonColor
        )
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
