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
    fun theNeonLookUsesThePickedNeonColourAndOtherStylesUseThePicker() {
        val picked = 0xFFFB7185.toInt()
        val neon = 0xFF00F5FF.toInt()
        // With the neon look on, the body is drawn in the neon colour.
        assertEquals(neon, LoadingIndicatorStyle.effectiveColor("dots", picked, true, neon))
        // The spectrum ring is always neon, whatever the toggle says.
        assertEquals(
            neon,
            LoadingIndicatorStyle.effectiveColor(LoadingIndicatorStyle.STYLE_SPECTRUM, picked, false, neon)
        )
        // Without it, the ordinary colour picker wins.
        assertEquals(picked, LoadingIndicatorStyle.effectiveColor("dots", picked, false, neon))
        assertEquals(
            LoadingIndicatorStyle.DEFAULT_COLOR,
            LoadingIndicatorStyle.effectiveColor("classic", 0, false, neon)
        )
    }

    @Test
    fun theNeonPaletteIsVividAndDistinct() {
        assertTrue(LoadingIndicatorStyle.NEON_PALETTE.size >= 4)
        assertTrue(LoadingIndicatorStyle.NEON_PALETTE.distinct().size >= 4)
        LoadingIndicatorStyle.NEON_PALETTE.forEach { neon ->
            assertEquals(255, LoadingIndicatorStyle.alphaOf(neon))
        }
        // The default neon colour is the first swatch, so the picker opens on it.
        assertEquals(
            LoadingIndicatorStyle.NEON_PALETTE.first(),
            LoadingIndicatorStyle.DEFAULT_NEON_COLOR
        )
    }

    @Test
    fun theSpectrumIsDerivedFromTheChosenNeonColour() {
        val cyan = LoadingIndicatorStyle.DEFAULT_NEON_COLOR
        val magenta = 0xFFFF00D4.toInt()
        val cyanSpectrum = LoadingIndicatorStyle.neonSpectrum(cyan)
        val magentaSpectrum = LoadingIndicatorStyle.neonSpectrum(magenta)

        assertEquals(5, cyanSpectrum.size)
        // The first shade is the colour the user picked.
        assertEquals(cyan, cyanSpectrum.first())
        assertEquals(magenta, magentaSpectrum.first())
        // A different hue gives a different spectrum, so picking a colour really re-tints it.
        assertTrue(cyanSpectrum != magentaSpectrum)
        cyanSpectrum.forEach { shade ->
            assertEquals(255, LoadingIndicatorStyle.alphaOf(shade))
        }
    }

    @Test
    fun aMissingNeonColourFallsBackToTheDefault() {
        // Gson writes 0 for a missing int, which would be a transparent black.
        assertEquals(
            LoadingIndicatorStyle.DEFAULT_NEON_COLOR,
            LoadingIndicatorStyle.sanitizeNeonColor(0)
        )
        val magenta = 0xFFFF00D4.toInt()
        assertEquals(magenta, LoadingIndicatorStyle.sanitizeNeonColor(magenta))
    }

    @Test
    fun theOldNeonStyleBecomesTheSpectrumRingWithTheNeonLookOn() {
        assertEquals(
            LoadingIndicatorStyle.STYLE_SPECTRUM,
            LoadingIndicatorStyle.normalizeStyle(LoadingIndicatorStyle.LEGACY_STYLE_NEON)
        )
        assertEquals("dots", LoadingIndicatorStyle.normalizeStyle("dots"))
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
            loadingIndicatorSizePercent = 0,
            loadingIndicatorNeonColor = 0
        )
        LoadingIndicatorStyle.sanitize(legacy)
        assertEquals("classic", legacy.loadingIndicatorStyle)
        assertEquals(LoadingIndicatorStyle.DEFAULT_COLOR, legacy.loadingIndicatorColor)
        assertEquals(100, legacy.loadingIndicatorSizePercent)
        assertEquals(
            LoadingIndicatorStyle.DEFAULT_NEON_COLOR,
            legacy.loadingIndicatorNeonColor
        )
    }

    @Test
    fun sanitizeUpgradesTheOldNeonStyleToTheSpectrumRing() {
        val old = AppConfig(
            loadingIndicatorStyle = LoadingIndicatorStyle.LEGACY_STYLE_NEON,
            loadingIndicatorColor = 0xFF22D3EE.toInt(),
            loadingIndicatorSizePercent = 150
        )
        LoadingIndicatorStyle.sanitize(old)
        assertEquals(LoadingIndicatorStyle.STYLE_SPECTRUM, old.loadingIndicatorStyle)
        assertTrue(old.loadingIndicatorNeon)
        assertEquals(0xFF22D3EE.toInt(), old.loadingIndicatorColor)
        assertEquals(150, old.loadingIndicatorSizePercent)
    }

    @Test
    fun sanitizeKeepsAValidConfigUntouched() {
        val tuned = AppConfig(
            loadingIndicatorStyle = LoadingIndicatorStyle.STYLE_SPECTRUM,
            loadingIndicatorColor = 0xFF22D3EE.toInt(),
            loadingIndicatorSizePercent = 150
        )
        LoadingIndicatorStyle.sanitize(tuned)
        assertEquals(LoadingIndicatorStyle.STYLE_SPECTRUM, tuned.loadingIndicatorStyle)
        assertEquals(0xFF22D3EE.toInt(), tuned.loadingIndicatorColor)
        assertEquals(150, tuned.loadingIndicatorSizePercent)
    }
}
