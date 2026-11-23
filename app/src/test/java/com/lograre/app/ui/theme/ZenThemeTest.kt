package com.lograre.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ZenThemeTest {

    private class InMemoryThemeStorage(
        private var current: ThemeMode = ThemeMode.SYSTEM
    ) : ThemePreferenceStorage {
        override fun readThemeMode(): ThemeMode = current
        override fun writeThemeMode(mode: ThemeMode) {
            current = mode
        }
    }

    @Test
    fun `ThemeMode resolves dark state accurately for system, light and dark`() {
        assertFalse(ThemeMode.SYSTEM.resolveIsDark(isSystemInDarkTheme = false))
        assertTrue(ThemeMode.SYSTEM.resolveIsDark(isSystemInDarkTheme = true))

        assertFalse(ThemeMode.LIGHT.resolveIsDark(isSystemInDarkTheme = false))
        assertFalse(ThemeMode.LIGHT.resolveIsDark(isSystemInDarkTheme = true))

        assertTrue(ThemeMode.DARK.resolveIsDark(isSystemInDarkTheme = false))
        assertTrue(ThemeMode.DARK.resolveIsDark(isSystemInDarkTheme = true))
    }

    @Test
    fun `ThemeMode parses stored values and falls back to SYSTEM`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageValue("system"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorageValue("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorageValue("dark"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageValue(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageValue("unknown"))
    }

    @Test
    fun `ThemePreferenceRepository reads synchronously and persists updates`() {
        val storage = InMemoryThemeStorage(current = ThemeMode.DARK)
        val repository = ThemePreferenceRepository(storage)

        assertEquals(ThemeMode.DARK, repository.getCurrentModeSync())
        assertEquals(ThemeMode.DARK, repository.themeMode.value)

        repository.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repository.getCurrentModeSync())
        assertEquals(ThemeMode.LIGHT, storage.readThemeMode())

        repository.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, repository.themeMode.value)
        assertEquals(ThemeMode.SYSTEM, storage.readThemeMode())
    }

    @Test
    fun `LightZenPalette satisfies WCAG AA contrast for body text and buttons`() {
        assertWcagAa("Light text on bgTop", LightZenPalette.text, LightZenPalette.bgTop)
        assertWcagAa("Light text on bgBottom", LightZenPalette.text, LightZenPalette.bgBottom)

        val glassOverBottom = LightZenPalette.surface.compositeOver(LightZenPalette.bgBottom)
        assertWcagAa("Light text on GlassCard", LightZenPalette.text, glassOverBottom)

        val secondaryOverBottom = LightZenPalette.textSecondary.compositeOver(LightZenPalette.bgBottom)
        assertWcagAa("Light textSecondary on bgBottom", secondaryOverBottom, LightZenPalette.bgBottom)

        val secondaryOverGlass = LightZenPalette.textSecondary.compositeOver(glassOverBottom)
        assertWcagAa("Light textSecondary on GlassCard", secondaryOverGlass, glassOverBottom)

        assertWcagAa("Light onAccent on accent", LightZenPalette.onAccent, LightZenPalette.accent)
        assertWcagAa("Light onAccent2 on accent2", LightZenPalette.onAccent2, LightZenPalette.accent2)
    }

    @Test
    fun `DarkZenPalette satisfies WCAG AA contrast for body text and buttons`() {
        assertWcagAa("Dark text on bgTop", DarkZenPalette.text, DarkZenPalette.bgTop)
        assertWcagAa("Dark text on bgBottom", DarkZenPalette.text, DarkZenPalette.bgBottom)

        val glassOverBottom = DarkZenPalette.surface.compositeOver(DarkZenPalette.bgBottom)
        assertWcagAa("Dark text on GlassCard", DarkZenPalette.text, glassOverBottom)

        val secondaryOverBottom = DarkZenPalette.textSecondary.compositeOver(DarkZenPalette.bgBottom)
        assertWcagAa("Dark textSecondary on bgBottom", secondaryOverBottom, DarkZenPalette.bgBottom)

        val secondaryOverGlass = DarkZenPalette.textSecondary.compositeOver(glassOverBottom)
        assertWcagAa("Dark textSecondary on GlassCard", secondaryOverGlass, glassOverBottom)

        assertWcagAa("Dark onAccent on accent", DarkZenPalette.onAccent, DarkZenPalette.accent)
        assertWcagAa("Dark onAccent2 on accent2", DarkZenPalette.onAccent2, DarkZenPalette.accent2)
    }

    private fun assertWcagAa(label: String, foreground: Color, background: Color) {
        val ratio = wcagContrastRatio(foreground, background)
        assertTrue(
            "$label contrast ratio was %.2f:1 (expected >= 4.50:1)".format(ratio),
            ratio >= 4.5
        )
    }

    private fun wcagContrastRatio(c1: Color, c2: Color): Double {
        val l1 = relativeLuminance(c1)
        val l2 = relativeLuminance(c2)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(c: Float): Double {
            val d = c.toDouble()
            return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }
}
