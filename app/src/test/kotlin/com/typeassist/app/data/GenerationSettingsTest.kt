package com.typeassist.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The values behind the sliders in Settings → Text generation. Whatever happens to a stored config
 * (an old install, a hand-edited backup, a JSON `null`), a request must still be sendable, so the
 * clamping rules are checked here rather than trusted.
 */
class GenerationSettingsTest {

    @Test
    fun defaultsAreTheDocumentedOnes() {
        val gen = GenerationSettings.defaults()

        assertEquals(0.2, gen.temperature, 0.0001)
        assertEquals(0.95, gen.topP, 0.0001)
        assertEquals(0, gen.maxTokens)
    }

    @Test
    fun aNullConfigBecomesTheDefaults() {
        assertEquals(GenerationSettings.defaults(), GenerationSettings.sanitize(null))
    }

    @Test
    fun aCleanConfigIsReturnedUnchanged() {
        val gen = GenConfig(temperature = 0.7, topP = 0.8, maxTokens = 512)

        assertSame(gen, GenerationSettings.sanitize(gen))
    }

    @Test
    fun outOfRangeValuesAreClamped() {
        val stored = GenConfig(temperature = 99.0, topP = -3.0, maxTokens = 999_999)

        val gen = GenerationSettings.sanitize(stored)

        assertEquals(GenerationSettings.MAX_TEMPERATURE, gen.temperature, 0.0001)
        assertEquals(GenerationSettings.MIN_TOP_P, gen.topP, 0.0001)
        assertEquals(GenerationSettings.MAX_MAX_TOKENS, gen.maxTokens)
        // Repaired values are a new object; the caller's instance is left alone.
        assertNotSame(stored, gen)
        assertEquals(99.0, stored.temperature, 0.0001)
    }

    @Test
    fun nonFiniteNumbersFallBackInsteadOfReachingTheRequest() {
        val nan = GenerationSettings.sanitize(GenConfig(temperature = Double.NaN, topP = Double.NaN))
        assertEquals(GenerationSettings.DEFAULT_TEMPERATURE, nan.temperature, 0.0001)
        assertEquals(GenerationSettings.DEFAULT_TOP_P, nan.topP, 0.0001)

        val positive = GenerationSettings.sanitize(
            GenConfig(temperature = Double.POSITIVE_INFINITY, topP = Double.POSITIVE_INFINITY)
        )
        assertEquals(GenerationSettings.MAX_TEMPERATURE, positive.temperature, 0.0001)
        assertEquals(GenerationSettings.MAX_TOP_P, positive.topP, 0.0001)

        val negative = GenerationSettings.sanitize(
            GenConfig(temperature = Double.NEGATIVE_INFINITY, topP = Double.NEGATIVE_INFINITY)
        )
        assertEquals(GenerationSettings.MIN_TEMPERATURE, negative.temperature, 0.0001)
        assertEquals(GenerationSettings.MIN_TOP_P, negative.topP, 0.0001)
    }

    @Test
    fun aZeroTokenLimitMeansNoLengthFieldIsSent() {
        val wire = GenerationSettings.wireParameters(GenConfig(temperature = 0.2, topP = 0.95, maxTokens = 0))

        assertNull(wire.maxTokens)
        assertEquals(0.2, wire.temperature, 0.0001)
        assertEquals(0.95, wire.topP, 0.0001)
    }

    @Test
    fun aPositiveTokenLimitIsSentClamped() {
        assertEquals(
            Integer.valueOf(700),
            GenerationSettings.wireParameters(GenConfig(maxTokens = 700)).maxTokens
        )
        assertEquals(
            Integer.valueOf(GenerationSettings.MAX_MAX_TOKENS),
            GenerationSettings.wireParameters(GenConfig(maxTokens = 100_000)).maxTokens
        )
        assertNull(GenerationSettings.wireParameters(GenConfig(maxTokens = -20)).maxTokens)
    }

    @Test
    fun wireParametersAlwaysCarrySanitisedNumbers() {
        val wire = GenerationSettings.wireParameters(
            GenConfig(temperature = -5.0, topP = 4.0, maxTokens = 256)
        )

        assertEquals(GenerationSettings.MIN_TEMPERATURE, wire.temperature, 0.0001)
        assertEquals(GenerationSettings.MAX_TOP_P, wire.topP, 0.0001)
        assertEquals(Integer.valueOf(256), wire.maxTokens)
    }

    @Test
    fun presetsMapBackToTheirIds() {
        assertEquals("precise", GenerationSettings.presetIdOf(0.1, 0.9))
        assertEquals("balanced", GenerationSettings.presetIdOf(0.2, 0.95))
        assertEquals("creative", GenerationSettings.presetIdOf(0.9, 1.0))
        // Between two presets: reported as custom rather than rounded to the nearest name.
        assertNull(GenerationSettings.presetIdOf(0.55, 0.5))
    }

    @Test
    fun everyPresetIsInsideTheAllowedRange() {
        GenerationSettings.PRESETS.forEach { preset ->
            assertTrue(preset.name.isNotBlank())
            assertTrue(preset.description.isNotBlank())
            assertEquals(preset.temperature, GenerationSettings.clampTemperature(preset.temperature), 0.0001)
            assertEquals(preset.topP, GenerationSettings.clampTopP(preset.topP), 0.0001)
            assertEquals(preset.id, GenerationSettings.presetIdOf(preset.temperature, preset.topP))
        }
    }

    @Test
    fun unknownPresetIdsAreRejected() {
        assertNull(GenerationSettings.presetById("wild"))
        assertEquals("Balanced", GenerationSettings.presetById("balanced")?.name)
    }

    @Test
    fun valuesAreFormattedForTheSettingsScreen() {
        assertEquals("0.20", GenerationSettings.formatTemperature(0.2))
        assertEquals("0.05", GenerationSettings.formatTopP(0.05))
        assertEquals("Provider default", GenerationSettings.formatMaxTokens(0))
        assertEquals("512 tokens", GenerationSettings.formatMaxTokens(512))
    }

    @Test
    fun everySliderValueHasAnExplanation() {
        val temperatures = listOf(0.0, 0.2, 0.5, 1.0, 2.0)
        temperatures.forEach { assertTrue(GenerationSettings.describeTemperature(it).isNotBlank()) }

        val topPs = listOf(0.05, 0.5, 0.9, 1.0)
        topPs.forEach { assertTrue(GenerationSettings.describeTopP(it).isNotBlank()) }

        val tokenLimits = listOf(0, 128, 512, 4096)
        tokenLimits.forEach { assertTrue(GenerationSettings.describeMaxTokens(it).isNotBlank()) }
    }

    @Test
    fun theSummaryNamesThePresetAndTheNumbers() {
        val balanced = createDefaultConfig()
        assertEquals("Balanced · 0.20 · 0.95", GenerationSettings.summary(balanced))

        val custom = createDefaultConfig().copy(
            generationConfig = GenConfig(temperature = 0.55, topP = 0.5, maxTokens = 300)
        )
        assertEquals("Custom · 0.55 · 0.50", GenerationSettings.summary(custom))
    }

    @Test
    fun theSummarySurvivesAMissingConfig() {
        val values = GenerationSettings.summary(null)

        assertTrue(values.startsWith("Balanced"))
        // The hub prints this straight into a row, so it must never be empty.
        assertTrue(values.isNotBlank())
    }
}
