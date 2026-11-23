package com.lograre.app.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lograre.app.R
import com.lograre.app.service.AppPermissionsStatus
import com.lograre.app.ui.components.GlassCard
import com.lograre.app.ui.components.ZenBackButton
import com.lograre.app.ui.components.ZenBackground
import com.lograre.app.ui.components.ZenButton
import com.lograre.app.ui.components.ZenButtonStyle
import com.lograre.app.ui.theme.AppTheme
import com.lograre.app.ui.theme.LocalZenPalette
import com.lograre.app.ui.theme.LocalZenTypography
import com.lograre.app.ui.theme.ThemeMode
import com.lograre.app.ui.theme.ZenDimens
import com.lograre.app.ui.theme.ZenShapes

@Composable
fun PermissionsGuideScreen(
    status: AppPermissionsStatus,
    onBackClick: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestNotificationsPermission: () -> Unit,
    onRequestBatteryExemption: () -> Unit
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current

    ZenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = ZenDimens.ScreenHorizontalPadding,
                    vertical = ZenDimens.Space16
                ),
            verticalArrangement = Arrangement.spacedBy(ZenDimens.Space16)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ZenDimens.Space8)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ZenDimens.Space12),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZenBackButton(onClick = onBackClick)
                    Text(
                        text = stringResource(R.string.permissions_title),
                        style = typo.title,
                        color = palette.text,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = stringResource(R.string.permissions_subtitle),
                    style = typo.body,
                    color = palette.textSecondary
                )
            }

            PermissionStepCard(
                stepNumber = 1,
                iconRes = R.drawable.ic_zen_accessibility,
                title = stringResource(R.string.perm_accessibility_title),
                description = stringResource(R.string.perm_accessibility_desc),
                isGranted = status.accessibilityEnabled,
                actionLabel = if (status.accessibilityEnabled) {
                    stringResource(R.string.perm_accessibility_action_review)
                } else {
                    stringResource(R.string.perm_accessibility_action_enable)
                },
                onActionClick = onOpenAccessibilitySettings
            )

            PermissionStepCard(
                stepNumber = 2,
                iconRes = R.drawable.ic_zen_notifications,
                title = stringResource(R.string.perm_notifications_title),
                description = stringResource(R.string.perm_notifications_desc),
                isGranted = status.notificationsGranted,
                actionLabel = if (status.notificationsGranted) {
                    stringResource(R.string.perm_notifications_action_review)
                } else {
                    stringResource(R.string.perm_notifications_action_enable)
                },
                onActionClick = onRequestNotificationsPermission
            )

            PermissionStepCard(
                stepNumber = 3,
                iconRes = R.drawable.ic_zen_battery,
                title = stringResource(R.string.perm_battery_title),
                description = stringResource(R.string.perm_battery_desc),
                isGranted = status.batteryOptimizationIgnored,
                actionLabel = if (status.batteryOptimizationIgnored) {
                    stringResource(R.string.perm_battery_action_review)
                } else {
                    stringResource(R.string.perm_battery_action_enable)
                },
                onActionClick = onRequestBatteryExemption
            )
        }
    }
}

@Composable
private fun PermissionStepCard(
    stepNumber: Int,
    @DrawableRes iconRes: Int,
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onActionClick: () -> Unit
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ZenDimens.Space12)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ZenDimens.Space12),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (isGranted) palette.accent else palette.accent2,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = if (isGranted) palette.onAccent else palette.onAccent2,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ZenDimens.Space4)
                ) {
                    Text(
                        text = "$stepNumber. $title",
                        style = typo.subtitle,
                        color = palette.text
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isGranted) palette.accent else palette.accent2,
                                shape = ZenShapes.Pill
                            )
                            .padding(horizontal = ZenDimens.Space12, vertical = ZenDimens.Space4)
                    ) {
                        Text(
                            text = if (isGranted) {
                                stringResource(R.string.permissions_status_active)
                            } else {
                                stringResource(R.string.permissions_status_pending)
                            },
                            style = typo.label,
                            color = if (isGranted) palette.onAccent else palette.onAccent2
                        )
                    }
                }
            }

            Text(
                text = description,
                style = typo.body,
                color = palette.textSecondary
            )

            ZenButton(
                text = actionLabel,
                onClick = onActionClick,
                style = if (isGranted) ZenButtonStyle.SECONDARY else ZenButtonStyle.PRIMARY
            )
        }
    }
}

@Preview(name = "Permisos — Claro", showBackground = true, widthDp = 380, heightDp = 760)
@Composable
private fun PermissionsGuideLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        PermissionsGuideScreen(
            status = AppPermissionsStatus(
                accessibilityEnabled = true,
                notificationsGranted = false,
                batteryOptimizationIgnored = false
            ),
            onBackClick = {},
            onOpenAccessibilitySettings = {},
            onRequestNotificationsPermission = {},
            onRequestBatteryExemption = {}
        )
    }
}

@Preview(name = "Permisos — Oscuro", showBackground = true, widthDp = 380, heightDp = 760)
@Composable
private fun PermissionsGuideDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        PermissionsGuideScreen(
            status = AppPermissionsStatus(
                accessibilityEnabled = false,
                notificationsGranted = true,
                batteryOptimizationIgnored = true
            ),
            onBackClick = {},
            onOpenAccessibilitySettings = {},
            onRequestNotificationsPermission = {},
            onRequestBatteryExemption = {}
        )
    }
}
