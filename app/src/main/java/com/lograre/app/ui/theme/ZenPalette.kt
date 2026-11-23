package com.lograre.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Color tokens for the "Lago y bosque" theme (light and dark modes).
 *
 * WCAG AA (4.5:1) adjustments applied vs. raw draft values:
 * - Light `accent` darkened slightly from `#2F8F78` (3.93:1 with white) to `#277D68` (4.93:1 with white).
 * - Light `textSecondaryAlpha` adjusted from `0.70f` to `0.78f` so secondary text directly over `bgBottom`
 *   maintains >= 5.0:1 contrast (and >= 5.7:1 on `GlassCard`).
 */
@Immutable
data class ZenPalette(
    val bgTop: Color,
    val bgBottom: Color,
    val accent: Color,
    val accent2: Color,
    val text: Color,
    val onAccent: Color,
    val onAccent2: Color,
    val textSecondaryAlpha: Float,
    val surfaceAlpha: Float,
    val borderAlpha: Float,
    val backgroundBlobWhiteAlpha: Float,
    val isDark: Boolean
) {
    val textSecondary: Color
        get() = text.copy(alpha = textSecondaryAlpha)

    val surface: Color
        get() = Color.White.copy(alpha = surfaceAlpha)

    val border: Color
        get() = Color.White.copy(alpha = borderAlpha)

    val rippleColor: Color
        get() = if (isDark) accent else Color.White
}

val LightZenPalette = ZenPalette(
    bgTop = Color(0xFFDDEFE3),
    bgBottom = Color(0xFFB7DCCB),
    accent = Color(0xFF277D68),
    accent2 = Color(0xFFF5C45E),
    text = Color(0xFF17352C),
    onAccent = Color(0xFFFFFFFF),
    onAccent2 = Color(0xFF17352C),
    textSecondaryAlpha = 0.78f,
    surfaceAlpha = 0.45f,
    borderAlpha = 0.70f,
    backgroundBlobWhiteAlpha = 0.25f,
    isDark = false
)

val DarkZenPalette = ZenPalette(
    bgTop = Color(0xFF0D1F1C),
    bgBottom = Color(0xFF1B3A35),
    accent = Color(0xFF86E0C2),
    accent2 = Color(0xFFB79CFF),
    text = Color(0xFFE4F2EC),
    onAccent = Color(0xFF0D1F1C),
    onAccent2 = Color(0xFF0D1F1C),
    textSecondaryAlpha = 0.72f,
    surfaceAlpha = 0.10f,
    borderAlpha = 0.18f,
    backgroundBlobWhiteAlpha = 0.10f,
    isDark = true
)

val LocalZenPalette = compositionLocalOf { LightZenPalette }
