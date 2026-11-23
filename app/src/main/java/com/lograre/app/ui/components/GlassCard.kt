package com.lograre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.lograre.app.ui.theme.AppTheme
import com.lograre.app.ui.theme.LocalZenPalette
import com.lograre.app.ui.theme.LocalZenTypography
import com.lograre.app.ui.theme.ThemeMode
import com.lograre.app.ui.theme.ZenDimens
import com.lograre.app.ui.theme.ZenShapes

/**
 * Translucent "water glass" surface without blur or drop shadows (works identically from API 26+).
 * Uses a subtle top-to-bottom translucent white gradient and a 1 dp fine border.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = ZenShapes.Card,
    contentPadding: PaddingValues = PaddingValues(ZenDimens.AppCardPadding),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalZenPalette.current
    val topAlpha = (palette.surfaceAlpha * 1.12f).coerceAtMost(0.65f)
    val bottomAlpha = (palette.surfaceAlpha * 0.88f).coerceAtLeast(0.05f)

    val glassBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = topAlpha),
            Color.White.copy(alpha = bottomAlpha)
        )
    )

    val interactiveModifier = if (onClick != null) {
        Modifier.waterRipple(
            onClickLabel = onClickLabel,
            role = Role.Button,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(brush = glassBrush, shape = shape)
            .border(width = ZenDimens.BorderWidth, color = palette.border, shape = shape)
            .then(interactiveModifier)
            .padding(contentPadding),
        content = content
    )
}

@Preview(name = "GlassCard — Claro", showBackground = true, widthDp = 360, heightDp = 240)
@Composable
private fun GlassCardLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ZenBackground {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ZenDimens.ScreenHorizontalPadding),
                onClick = {}
            ) {
                val palette = LocalZenPalette.current
                val typo = LocalZenTypography.current
                Column(verticalArrangement = Arrangement.spacedBy(ZenDimens.Space4)) {
                    Text("Instagram", style = typo.subtitle, color = palette.text)
                    Text("12 min usados de 30 min", style = typo.body, color = palette.textSecondary)
                }
            }
        }
    }
}

@Preview(name = "GlassCard — Oscuro", showBackground = true, widthDp = 360, heightDp = 240)
@Composable
private fun GlassCardDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ZenBackground {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ZenDimens.ScreenHorizontalPadding),
                onClick = {}
            ) {
                val palette = LocalZenPalette.current
                val typo = LocalZenTypography.current
                Column(verticalArrangement = Arrangement.spacedBy(ZenDimens.Space4)) {
                    Text("Instagram", style = typo.subtitle, color = palette.text)
                    Text("12 min usados de 30 min", style = typo.body, color = palette.textSecondary)
                }
            }
        }
    }
}
