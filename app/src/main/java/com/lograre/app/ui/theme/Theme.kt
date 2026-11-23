package com.lograre.app.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Root theme for "Lago y bosque".
 *
 * - Always disables Android 12+ dynamic color (`dynamicColor = false`).
 * - Smoothly interpolates all palette colors over 400 ms on light/dark mode switches.
 * - Updates status and navigation bar icon contrast to match the active mode.
 */
@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = themeMode.resolveIsDark(isSystemInDarkTheme())
    val targetPalette = if (isDark) DarkZenPalette else LightZenPalette
    val palette = if (LocalInspectionMode.current) {
        targetPalette
    } else {
        animateZenPalette(targetPalette)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    val colorScheme = palette.toMaterialColorScheme()

    CompositionLocalProvider(
        LocalZenPalette provides palette,
        LocalZenTypography provides DefaultZenTypography
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ZenMaterialTypography,
            shapes = ZenMaterialShapes,
            content = content
        )
    }
}

@Composable
fun LograreTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    AppTheme(themeMode = themeMode, content = content)
}

@Composable
private fun animateZenPalette(target: ZenPalette): ZenPalette {
    val colorSpec = tween<Color>(durationMillis = ZenDimens.ThemeTransitionDurationMs)
    val floatSpec = tween<Float>(durationMillis = ZenDimens.ThemeTransitionDurationMs)

    val bgTop by animateColorAsState(target.bgTop, colorSpec, label = "bgTop")
    val bgBottom by animateColorAsState(target.bgBottom, colorSpec, label = "bgBottom")
    val accent by animateColorAsState(target.accent, colorSpec, label = "accent")
    val accent2 by animateColorAsState(target.accent2, colorSpec, label = "accent2")
    val text by animateColorAsState(target.text, colorSpec, label = "text")
    val onAccent by animateColorAsState(target.onAccent, colorSpec, label = "onAccent")
    val onAccent2 by animateColorAsState(target.onAccent2, colorSpec, label = "onAccent2")
    val textSecondaryAlpha by animateFloatAsState(
        target.textSecondaryAlpha,
        floatSpec,
        label = "textSecondaryAlpha"
    )
    val surfaceAlpha by animateFloatAsState(
        target.surfaceAlpha,
        floatSpec,
        label = "surfaceAlpha"
    )
    val borderAlpha by animateFloatAsState(
        target.borderAlpha,
        floatSpec,
        label = "borderAlpha"
    )
    val blobWhiteAlpha by animateFloatAsState(
        target.backgroundBlobWhiteAlpha,
        floatSpec,
        label = "blobWhiteAlpha"
    )

    return ZenPalette(
        bgTop = bgTop,
        bgBottom = bgBottom,
        accent = accent,
        accent2 = accent2,
        text = text,
        onAccent = onAccent,
        onAccent2 = onAccent2,
        textSecondaryAlpha = textSecondaryAlpha,
        surfaceAlpha = surfaceAlpha,
        borderAlpha = borderAlpha,
        backgroundBlobWhiteAlpha = blobWhiteAlpha,
        isDark = target.isDark
    )
}

private fun ZenPalette.toMaterialColorScheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.22f),
        onPrimaryContainer = text,
        secondary = accent2,
        onSecondary = onAccent2,
        secondaryContainer = accent2.copy(alpha = 0.22f),
        onSecondaryContainer = text,
        background = bgBottom,
        onBackground = text,
        surface = bgTop,
        onSurface = text,
        surfaceVariant = surface,
        onSurfaceVariant = textSecondary,
        outline = border,
        error = accent2,
        onError = onAccent2,
        errorContainer = accent2.copy(alpha = 0.22f),
        onErrorContainer = text
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.16f),
        onPrimaryContainer = text,
        secondary = accent2,
        onSecondary = onAccent2,
        secondaryContainer = accent2.copy(alpha = 0.32f),
        onSecondaryContainer = text,
        background = bgBottom,
        onBackground = text,
        surface = bgTop,
        onSurface = text,
        surfaceVariant = surface,
        onSurfaceVariant = textSecondary,
        outline = border,
        error = accent2,
        onError = onAccent2,
        errorContainer = accent2.copy(alpha = 0.35f),
        onErrorContainer = text
    )
}

@Composable
private fun ZenThemePreviewCard() {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(palette.bgTop, palette.bgBottom)))
            .padding(ZenDimens.ScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(ZenDimens.Space16)
    ) {
        Text(
            text = "Buenos días",
            style = typo.headline,
            color = palette.text
        )
        Text(
            text = "3 apps con tiempo disponible hoy",
            style = typo.body,
            color = palette.textSecondary
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surface, ZenShapes.Card)
                .border(ZenDimens.BorderWidth, palette.border, ZenShapes.Card)
                .padding(ZenDimens.AppCardPadding),
            verticalArrangement = Arrangement.spacedBy(ZenDimens.Space8)
        ) {
            Text(
                text = "Respira un momento",
                style = typo.title,
                color = palette.text
            )
            Text(
                text = "Instagram",
                style = typo.subtitle,
                color = palette.text
            )
            Text(
                text = "12:30",
                style = typo.time,
                color = palette.text
            )
            Text(
                text = "EN PAUSA · LIBRE HOY",
                style = typo.label,
                color = palette.textSecondary
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZenDimens.Space12)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(ZenDimens.ButtonHeight)
                    .background(palette.accent, ZenShapes.Pill),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Primario",
                    style = typo.subtitle,
                    color = palette.onAccent
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(ZenDimens.ButtonHeight)
                    .background(palette.accent2, ZenShapes.Pill),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Secundario",
                    style = typo.subtitle,
                    color = palette.onAccent2
                )
            }
        }
    }
}

@Preview(name = "Lago y bosque — Claro", showBackground = true)
@Composable
private fun AppThemeLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ZenThemePreviewCard()
    }
}

@Preview(name = "Lago y bosque — Oscuro", showBackground = true)
@Composable
private fun AppThemeDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ZenThemePreviewCard()
    }
}
