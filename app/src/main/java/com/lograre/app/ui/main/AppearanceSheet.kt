package com.lograre.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lograre.app.R
import com.lograre.app.ui.components.ZenBackground
import com.lograre.app.ui.components.ZenPillChip
import com.lograre.app.ui.components.waterRipple
import com.lograre.app.ui.theme.AppTheme
import com.lograre.app.ui.theme.LocalZenPalette
import com.lograre.app.ui.theme.LocalZenTypography
import com.lograre.app.ui.theme.ThemeMode
import com.lograre.app.ui.theme.ZenDimens
import com.lograre.app.ui.theme.ZenShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceBottomSheet(
    currentThemeMode: ThemeMode,
    accessibilityEnabled: Boolean,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onOpenPermissionsClick: () -> Unit,
    onOpenChangePinClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalZenPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetContainerColor = palette.surface.compositeOver(palette.bgTop)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = ZenShapes.BottomSheet,
        containerColor = sheetContainerColor,
        contentColor = palette.text,
        tonalElevation = 0.dp
    ) {
        AppearanceSheetContent(
            currentThemeMode = currentThemeMode,
            accessibilityEnabled = accessibilityEnabled,
            onSelectThemeMode = onSelectThemeMode,
            onOpenPermissionsClick = {
                onDismiss()
                onOpenPermissionsClick()
            },
            onOpenChangePinClick = {
                onDismiss()
                onOpenChangePinClick()
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearanceSheetContent(
    currentThemeMode: ThemeMode,
    accessibilityEnabled: Boolean,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onOpenPermissionsClick: () -> Unit,
    onOpenChangePinClick: () -> Unit
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current
    val largeFont = LocalDensity.current.fontScale >= 1.5f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = ZenDimens.ScreenHorizontalPadding,
                end = ZenDimens.ScreenHorizontalPadding,
                top = ZenDimens.Space8,
                bottom = ZenDimens.Space24
            ),
        verticalArrangement = Arrangement.spacedBy(ZenDimens.Space16)
    ) {
        Text(
            text = stringResource(R.string.appearance_title),
            style = typo.title,
            color = palette.text
        )

        if (largeFont) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ZenDimens.Space8),
                verticalArrangement = Arrangement.spacedBy(ZenDimens.Space8)
            ) {
                ZenPillChip(
                    text = stringResource(R.string.theme_mode_system),
                    selected = currentThemeMode == ThemeMode.SYSTEM,
                    onClick = { onSelectThemeMode(ThemeMode.SYSTEM) }
                )
                ZenPillChip(
                    text = stringResource(R.string.theme_mode_light),
                    selected = currentThemeMode == ThemeMode.LIGHT,
                    onClick = { onSelectThemeMode(ThemeMode.LIGHT) }
                )
                ZenPillChip(
                    text = stringResource(R.string.theme_mode_dark),
                    selected = currentThemeMode == ThemeMode.DARK,
                    onClick = { onSelectThemeMode(ThemeMode.DARK) }
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ZenDimens.Space8)
            ) {
                ZenPillChip(
                    text = stringResource(R.string.theme_mode_system),
                    selected = currentThemeMode == ThemeMode.SYSTEM,
                    onClick = { onSelectThemeMode(ThemeMode.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )
                ZenPillChip(
                    text = stringResource(R.string.theme_mode_light),
                    selected = currentThemeMode == ThemeMode.LIGHT,
                    onClick = { onSelectThemeMode(ThemeMode.LIGHT) },
                    modifier = Modifier.weight(1f)
                )
                ZenPillChip(
                    text = stringResource(R.string.theme_mode_dark),
                    selected = currentThemeMode == ThemeMode.DARK,
                    onClick = { onSelectThemeMode(ThemeMode.DARK) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(ZenDimens.Space4))
        HorizontalDivider(color = palette.border)

        SheetActionRow(
            title = stringResource(R.string.settings_permissions_item),
            badge = if (!accessibilityEnabled) stringResource(R.string.settings_permissions_pending) else null,
            onClick = onOpenPermissionsClick
        )

        SheetActionRow(
            title = stringResource(R.string.settings_change_pin_item),
            badge = null,
            onClick = onOpenChangePinClick
        )
    }
}

@Composable
private fun SheetActionRow(
    title: String,
    badge: String?,
    onClick: () -> Unit
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ZenDimens.MinTouchTarget)
            .clip(ZenShapes.Pill)
            .background(palette.surface, ZenShapes.Pill)
            .border(ZenDimens.BorderWidth, palette.border, ZenShapes.Pill)
            .waterRipple(role = Role.Button, onClick = onClick)
            .padding(horizontal = ZenDimens.Space16, vertical = ZenDimens.Space12),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = typo.subtitle,
            color = palette.text
        )
        if (badge != null) {
            Box(
                modifier = Modifier
                    .background(palette.accent2, ZenShapes.Pill)
                    .padding(horizontal = ZenDimens.Space12, vertical = ZenDimens.Space4)
            ) {
                Text(
                    text = badge,
                    style = typo.label,
                    color = palette.onAccent2
                )
            }
        }
    }
}

@Preview(name = "Apariencia — Claro", showBackground = true, widthDp = 360)
@Composable
private fun AppearanceSheetLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ZenBackground {
            AppearanceSheetContent(
                currentThemeMode = ThemeMode.SYSTEM,
                accessibilityEnabled = false,
                onSelectThemeMode = {},
                onOpenPermissionsClick = {},
                onOpenChangePinClick = {}
            )
        }
    }
}

@Preview(name = "Apariencia — Oscuro", showBackground = true, widthDp = 360)
@Composable
private fun AppearanceSheetDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ZenBackground {
            AppearanceSheetContent(
                currentThemeMode = ThemeMode.DARK,
                accessibilityEnabled = true,
                onSelectThemeMode = {},
                onOpenPermissionsClick = {},
                onOpenChangePinClick = {}
            )
        }
    }
}
