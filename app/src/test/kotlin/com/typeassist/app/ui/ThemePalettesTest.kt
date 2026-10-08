package com.typeassist.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePalettesTest {

    @Test
    fun amoledIsTrueBlackAndDarkIsNot() {
        assertEquals(0xFF000000.toInt(), ThemePalettes.AMOLED.background)
        assertEquals(0xFF000000.toInt(), ThemePalettes.AMOLED.surfaceContainerLowest)
        assertNotEquals(0xFF000000.toInt(), ThemePalettes.DARK.background)
    }

    @Test
    fun darkAndAmoledLookDifferentOnBackgroundAndCards() {
        // The 4.5.0 bug: AMOLED inherited the Dark container roles, so Dark and AMOLED drew the
        // same cards and dialogs. Each role must now differ by a visible margin.
        val dark = ThemePalettes.DARK
        val amoled = ThemePalettes.AMOLED
        assertTrue(channelSum(dark.background) - channelSum(amoled.background) >= 20)
        assertTrue(channelSum(dark.surfaceContainer) - channelSum(amoled.surfaceContainer) >= 20)
        assertTrue(channelSum(dark.surfaceContainerHighest) - channelSum(amoled.surfaceContainerHighest) >= 20)
    }

    @Test
    fun everyPaletteWritesOutTheWholeSurfaceFamily() {
        listOf(ThemePalettes.LIGHT, ThemePalettes.DARK, ThemePalettes.AMOLED).forEach { p ->
            listOf(
                p.background, p.onBackground, p.surface, p.onSurface, p.surfaceVariant,
                p.onSurfaceVariant, p.outline, p.outlineVariant, p.surfaceDim, p.surfaceBright,
                p.surfaceContainerLowest, p.surfaceContainerLow, p.surfaceContainer,
                p.surfaceContainerHigh, p.surfaceContainerHighest
            ).forEach { argb ->
                assertEquals("every role must be opaque", 0xFF, argb ushr 24)
            }
        }
    }

    @Test
    fun containerRolesStepInOneDirection() {
        listOf(ThemePalettes.LIGHT, ThemePalettes.DARK, ThemePalettes.AMOLED).forEach { p ->
            val steps = listOf(
                p.surfaceContainerLowest, p.surfaceContainerLow, p.surfaceContainer,
                p.surfaceContainerHigh, p.surfaceContainerHighest
            ).map { luminance(it) }
            val ascending = steps.zipWithNext().all { (a, b) -> b > a }
            val descending = steps.zipWithNext().all { (a, b) -> b < a }
            assertTrue("container ladder should be strictly monotonic", ascending || descending)
        }
    }

    @Test
    fun resolveModeFollowsTheStoredChoiceAndSystemNightMode() {
        assertEquals(AppThemeMode.LIGHT, ThemePalettes.resolveMode("light", systemDark = true))
        assertEquals(AppThemeMode.DARK, ThemePalettes.resolveMode("dark", systemDark = false))
        assertEquals(AppThemeMode.AMOLED, ThemePalettes.resolveMode("amoled", systemDark = false))
        assertEquals(AppThemeMode.DARK, ThemePalettes.resolveMode("system", systemDark = true))
        assertEquals(AppThemeMode.LIGHT, ThemePalettes.resolveMode("system", systemDark = false))
    }

    @Test
    fun missingOrUnknownStoredValuesCountAsSystem() {
        assertEquals(AppThemeMode.DARK, ThemePalettes.resolveMode(null, systemDark = true))
        assertEquals(AppThemeMode.LIGHT, ThemePalettes.resolveMode("", systemDark = false))
        assertEquals(AppThemeMode.DARK, ThemePalettes.resolveMode("midnight", systemDark = true))
        assertEquals(AppThemeMode.AMOLED, ThemePalettes.resolveMode("AMOLED", systemDark = false))
    }

    @Test
    fun sanitizeAcceptsAnyCaseAndFallsBackToSystem() {
        assertEquals(AppThemeMode.AMOLED, AppThemeMode.sanitize("Amoled"))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.sanitize(null))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.sanitize("weird"))
    }

    @Test
    fun overlayColoursFollowTheResolvedTheme() {
        val light = ThemePalettes.overlayColors(AppThemeMode.LIGHT)
        val dark = ThemePalettes.overlayColors(AppThemeMode.DARK)
        val amoled = ThemePalettes.overlayColors(AppThemeMode.AMOLED)
        assertNotEquals(light.cardBackground, dark.cardBackground)
        assertNotEquals(dark.cardBackground, amoled.cardBackground)
        assertNotEquals(dark.chipBackground, amoled.chipBackground)
        // An unresolved value should never reach the overlays, but if it did it draws as Light.
        assertEquals(light, ThemePalettes.overlayColors("system"))
    }

    @Test
    fun overlayChipsStayMostlyOpaque() {
        listOf(AppThemeMode.LIGHT, AppThemeMode.DARK, AppThemeMode.AMOLED).forEach { mode ->
            assertTrue(mode, (ThemePalettes.overlayColors(mode).chipBackground ushr 24) >= 0xE0)
        }
    }

    @Test
    fun overlayTextAndAccentsStayReadable() {
        listOf(AppThemeMode.LIGHT, AppThemeMode.DARK, AppThemeMode.AMOLED).forEach { mode ->
            val c = ThemePalettes.overlayColors(mode)
            assertTrue(mode, contrast(c.titleText, c.cardBackground) >= 4.5)
            assertTrue(mode, contrast(c.bodyText, c.cardBackground) >= 4.5)
            assertTrue(mode, contrast(c.mutedText, c.cardBackground) >= 4.5)
            assertTrue(mode, contrast(c.accept, c.chipBackground) >= 4.5)
            assertTrue(mode, contrast(c.reject, c.chipBackground) >= 4.5)
            assertTrue(mode, contrast(c.accent, c.chipBackground) >= 4.5)
        }
    }

    @Test
    fun surfaceTextStaysReadable() {
        listOf(ThemePalettes.LIGHT, ThemePalettes.DARK, ThemePalettes.AMOLED).forEach { p ->
            assertTrue(contrast(p.onBackground, p.background) >= 4.5)
            assertTrue(contrast(p.onSurface, p.surfaceContainerHighest) >= 4.5)
            assertTrue(contrast(p.onSurfaceVariant, p.surfaceContainerHigh) >= 4.5)
        }
    }

    private fun channelSum(argb: Int): Int =
        ((argb shr 16) and 0xFF) + ((argb shr 8) and 0xFF) + (argb and 0xFF)

    /** WCAG relative luminance from the RGB channels (alpha is ignored). */
    private fun luminance(argb: Int): Double {
        fun linear(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
