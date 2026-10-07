package com.typeassist.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadingIndicatorStyleTest {

    @Test
    fun legacyConfigsFallBackToTheDefaults() {
        // Gson instantiates AppConfig without its constructor, so a config saved before the
        // colour/size settings existed arrives with 0 for both.
        assertEquals(LoadingIndicatorStyle.DEFAULT_COLOR, LoadingIndicatorStyle.sanitizeColor(0))
        assertEquals(100, LoadingIndicatorStyle.sanitizeSizePercent(0))
    }

    @Test
    fun sizeIsClampedToTheSliderRange() {
        assertEquals(50, LoadingIndicatorStyle.sanitizeSizePercent(-20))
        assertEquals(50, LoadingIndicatorStyle.sanitizeSizePercent(1))
        assertEquals(100, LoadingIndicatorStyle.sanitizeSizePercent(100))
        assertEquals(200, LoadingIndicatorStyle.sanitizeSizePercent(999))
    }

    @Test
    fun scaleIsTheSizeInHundredths() {
        assertEquals(1f, LoadingIndicatorStyle.scaleOf(100), 0.0001f)
        assertEquals(0.5f, LoadingIndicatorStyle.scaleOf(50), 0.0001f)
        assertEquals(2f, LoadingIndicatorStyle.scaleOf(200), 0.0001f)
    }

    @Test
    fun parsesHexColours() {
        assertEquals(0xFF22D3EE.toInt(), LoadingIndicatorStyle.parseHexColor("#22D3EE"))
        assertEquals(0xFF22D3EE.toInt(), LoadingIndicatorStyle.parseHexColor("22d3ee"))
        assertEquals(0xFF22D3EE.toInt(), LoadingIndicatorStyle.parseHexColor("  #22D3EE  "))
        assertEquals(0xFFFFFFFF.toInt(), LoadingIndicatorStyle.parseHexColor("#FFF"))
        assertEquals(0xFFAABBCC.toInt(), LoadingIndicatorStyle.parseHexColor("#ABC"))
        assertEquals(0x8022D3EE.toInt(), LoadingIndicatorStyle.parseHexColor("#8022D3EE"))
    }

    @Test
    fun rejectsIncompleteOrInvalidHexInput() {
        assertNull(LoadingIndicatorStyle.parseHexColor(""))
        assertNull(LoadingIndicatorStyle.parseHexColor("#"))
        assertNull(LoadingIndicatorStyle.parseHexColor("#22D3"))
        assertNull(LoadingIndicatorStyle.parseHexColor("#22D3EE00FF"))
        assertNull(LoadingIndicatorStyle.parseHexColor("#GGGGGG"))
    }

    @Test
    fun hexRoundTrips() {
        assertEquals("#22D3EE", LoadingIndicatorStyle.toHexRgb(0xFF22D3EE.toInt()))
        // Alpha is not shown in the hex field, but survives withAlpha().
        assertEquals("#22D3EE", LoadingIndicatorStyle.toHexRgb(0x3322D3EE))
        assertEquals(0x33, LoadingIndicatorStyle.alphaOf(LoadingIndicatorStyle.withAlpha(0xFF22D3EE.toInt(), 0x33)))
    }

    @Test
    fun aTransparentPickIsMadeOpaqueSoTheIndicatorStaysVisible() {
        val transparent = 0x0022D3EE
        assertEquals(0, LoadingIndicatorStyle.alphaOf(transparent))
        assertEquals(0xFF22D3EE.toInt(), LoadingIndicatorStyle.ensureOpaque(transparent))
        // An already opaque colour is left alone.
        assertEquals(0xFF22D3EE.toInt(), LoadingIndicatorStyle.ensureOpaque(0xFF22D3EE.toInt()))
    }

    @Test
    fun theRingCoreContrastsWithTheChosenColour() {
        // Pale yellow -> dark core, deep blue -> white core.
        assertEquals(0xFF101010.toInt(), LoadingIndicatorStyle.contrastColor(0xFFFACC15.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), LoadingIndicatorStyle.contrastColor(0xFF1E3A8A.toInt()))
    }

    @Test
    fun neonUsesItsLockedVividPaletteAndOtherStylesUseThePicker() {
        val picked = 0xFFFB7185.toInt()
        assertEquals(
            LoadingIndicatorStyle.NEON_PALETTE.first(),
            LoadingIndicatorStyle.effectiveColor("neon", picked)
        )
        assertEquals(picked, LoadingIndicatorStyle.effectiveColor("dots", picked))
        assertEquals(
            LoadingIndicatorStyle.DEFAULT_COLOR,
            LoadingIndicatorStyle.effectiveColor("classic", 0)
        )
        assertTrue(LoadingIndicatorStyle.NEON_PALETTE.size >= 4)
        assertTrue(LoadingIndicatorStyle.NEON_PALETTE.distinct().size >= 4)
        LoadingIndicatorStyle.NEON_PALETTE.forEach { neon ->
            assertEquals(255, LoadingIndicatorStyle.alphaOf(neon))
        }
    }

    @Test
    fun presetsAreAllOpaqueAndInsideTheRange() {
        assertTrue(LoadingIndicatorStyle.PRESET_COLORS.isNotEmpty())
        LoadingIndicatorStyle.PRESET_COLORS.forEach { preset ->
            assertEquals(255, LoadingIndicatorStyle.alphaOf(preset))
        }
        assertEquals(
            LoadingIndicatorStyle.DEFAULT_COLOR,
            LoadingIndicatorStyle.sanitizeColor(LoadingIndicatorStyle.PRESET_COLORS.first())
        )
    }

    @Test
    fun sanitizeRepairsALegacyConfigInPlace() {
        val legacy = AppConfig(
            loadingIndicatorStyle = "",
            loadingIndicatorColor = 0,
            loadingIndicatorSizePercent = 0
        )
        LoadingIndicatorStyle.sanitize(legacy)
        assertEquals("classic", legacy.loadingIndicatorStyle)
        assertEquals(LoadingIndicatorStyle.DEFAULT_COLOR, legacy.loadingIndicatorColor)
        assertEquals(100, legacy.loadingIndicatorSizePercent)
    }

    @Test
    fun sanitizeKeepsAValidConfigUntouched() {
        val tuned = AppConfig(
            loadingIndicatorStyle = "neon",
            loadingIndicatorColor = 0xFF22D3EE.toInt(),
            loadingIndicatorSizePercent = 150
        )
        LoadingIndicatorStyle.sanitize(tuned)
        assertEquals("neon", tuned.loadingIndicatorStyle)
        assertEquals(0xFF22D3EE.toInt(), tuned.loadingIndicatorColor)
        assertEquals(150, tuned.loadingIndicatorSizePercent)
    }
}
