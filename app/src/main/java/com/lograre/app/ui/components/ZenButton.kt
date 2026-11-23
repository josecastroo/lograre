package com.lograre.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lograre.app.R
import com.lograre.app.ui.theme.AppTheme
import com.lograre.app.ui.theme.LocalZenPalette
import com.lograre.app.ui.theme.LocalZenTypography
import com.lograre.app.ui.theme.ThemeMode
import com.lograre.app.ui.theme.ZenDimens
import com.lograre.app.ui.theme.ZenShapes

enum class ZenButtonStyle {
    PRIMARY,
    SECONDARY
}

/**
 * Pill-shaped 56 dp button with custom [waterRipple], zero drop shadows,
 * and WCAG AA contrast in both light and dark modes.
 */
@Composable
fun ZenButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ZenButtonStyle = ZenButtonStyle.PRIMARY,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = true
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current

    val containerColor = when (style) {
        ZenButtonStyle.PRIMARY -> palette.accent
        ZenButtonStyle.SECONDARY -> palette.surface
    }
    val contentColor = when (style) {
        ZenButtonStyle.PRIMARY -> palette.onAccent
        ZenButtonStyle.SECONDARY -> palette.text
    }
    val borderColor = when (style) {
        ZenButtonStyle.PRIMARY -> Color.Transparent
        ZenButtonStyle.SECONDARY -> palette.border
    }

    val widthModifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier

    Box(
        modifier = modifier
            .then(widthModifier)
            .defaultMinSize(minWidth = ZenDimens.MinTouchTarget, minHeight = ZenDimens.ButtonHeight)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(ZenShapes.Pill)
            .background(containerColor, ZenShapes.Pill)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(ZenDimens.BorderWidth, borderColor, ZenShapes.Pill)
                } else {
                    Modifier
                }
            )
            .waterRipple(
                enabled = enabled,
                role = Role.Button,
                color = if (style == ZenButtonStyle.PRIMARY) palette.onAccent else palette.rippleColor,
                onClick = onClick
            )
            .padding(horizontal = ZenDimens.Space24, vertical = ZenDimens.Space12),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = typo.subtitle,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Circular 48 dp translucent back arrow button matching the top settings icon.
 */
@Composable
fun ZenBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val palette = LocalZenPalette.current
    val backLabel = stringResource(R.string.action_back)

    Box(
        modifier = modifier
            .size(ZenDimens.MinTouchTarget)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .background(palette.surface, CircleShape)
            .border(ZenDimens.BorderWidth, palette.border, CircleShape)
            .waterRipple(
                enabled = enabled,
                onClickLabel = backLabel,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_zen_arrow_back),
            contentDescription = backLabel,
            tint = palette.text,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Minimal calm text button with >= 48 dp touch target and [waterRipple].
 */
@Composable
fun ZenTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color? = null
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current
    val resolvedColor = color ?: palette.text

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = ZenDimens.MinTouchTarget, minHeight = ZenDimens.MinTouchTarget)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(ZenShapes.Pill)
            .waterRipple(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = ZenDimens.Space16, vertical = ZenDimens.Space12),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = typo.subtitle,
            color = resolvedColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Pill-shaped selectable chip used for Appearance modes and extra-time presets.
 * Selected state uses `accent2` fill with high-contrast `onAccent2` text.
 */
@Composable
fun ZenPillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current
    val animationsEnabled = rememberAnimationsEnabled()

    val targetContainer = if (selected) palette.accent2 else palette.surface
    val targetContent = if (selected) palette.onAccent2 else palette.text
    val targetBorder = if (selected) Color.Transparent else palette.border

    val containerColor by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = tween(durationMillis = if (animationsEnabled) 250 else 0),
        label = "chip_container"
    )
    val contentColor by animateColorAsState(
        targetValue = targetContent,
        animationSpec = tween(durationMillis = if (animationsEnabled) 250 else 0),
        label = "chip_content"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = ZenDimens.MinTouchTarget, minHeight = ZenDimens.MinTouchTarget)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(ZenShapes.Pill)
            .background(containerColor, ZenShapes.Pill)
            .then(
                if (targetBorder != Color.Transparent) {
                    Modifier.border(ZenDimens.BorderWidth, targetBorder, ZenShapes.Pill)
                } else {
                    Modifier
                }
            )
            .semantics { this.selected = selected }
            .waterRipple(
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = ZenDimens.Space12, vertical = ZenDimens.Space12),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = typo.subtitle,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ZenButtonsPreviewContent() {
    ZenBackground {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZenDimens.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(ZenDimens.Space16),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ZenButton(
                text = "Continuar",
                onClick = {},
                style = ZenButtonStyle.PRIMARY
            )
            ZenButton(
                text = "Añadir app",
                onClick = {},
                style = ZenButtonStyle.SECONDARY
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ZenDimens.Space8)
            ) {
                ZenPillChip(text = "Sistema", selected = true, onClick = {}, modifier = Modifier.weight(1f))
                ZenPillChip(text = "Claro", selected = false, onClick = {}, modifier = Modifier.weight(1f))
                ZenPillChip(text = "Oscuro", selected = false, onClick = {}, modifier = Modifier.weight(1f))
            }
            ZenTextButton(text = "Salir", onClick = {})
        }
    }
}

@Preview(name = "ZenButtons — Claro", showBackground = true, widthDp = 360, heightDp = 320)
@Composable
private fun ZenButtonsLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ZenButtonsPreviewContent()
    }
}

@Preview(name = "ZenButtons — Oscuro", showBackground = true, widthDp = 360, heightDp = 320)
@Composable
private fun ZenButtonsDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ZenButtonsPreviewContent()
    }
}
