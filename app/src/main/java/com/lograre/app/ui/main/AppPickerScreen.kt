package com.lograre.app.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lograre.app.R
import com.lograre.app.data.apps.InstalledAppInfo
import com.lograre.app.ui.components.GlassCard
import com.lograre.app.ui.components.ZenBackground
import com.lograre.app.ui.components.ZenTextButton
import com.lograre.app.ui.components.rememberAnimationsEnabled
import com.lograre.app.ui.theme.AppTheme
import com.lograre.app.ui.theme.LocalZenPalette
import com.lograre.app.ui.theme.LocalZenTypography
import com.lograre.app.ui.theme.ThemeMode
import com.lograre.app.ui.theme.ZenDimens
import com.lograre.app.ui.theme.ZenShapes

@Composable
fun AppPickerScreen(
    uiState: MainUiState,
    onBackClick: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onAppSelected: (InstalledAppInfo) -> Unit
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current
    val selectedPackage = uiState.limitEditor?.packageName

    ZenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = ZenDimens.ScreenHorizontalPadding,
                        end = ZenDimens.ScreenHorizontalPadding,
                        top = ZenDimens.Space12,
                        bottom = ZenDimens.Space12
                    ),
                verticalArrangement = Arrangement.spacedBy(ZenDimens.Space12)
            ) {
                ZenTextButton(
                    text = stringResource(R.string.action_back),
                    onClick = onBackClick,
                    color = palette.textSecondary
                )

                Text(
                    text = stringResource(R.string.picker_title),
                    style = typo.title,
                    color = palette.text
                )

                TranslucentSearchPill(
                    query = uiState.appSearchQuery,
                    onQueryChanged = onSearchQueryChanged
                )
            }

            when {
                uiState.isLoadingInstalledApps -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = palette.accent)
                    }
                }

                uiState.availableAppsForPicker.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(ZenDimens.ScreenHorizontalPadding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(ZenDimens.Space24)
                        ) {
                            Text(
                                text = if (uiState.appSearchQuery.isBlank()) {
                                    stringResource(R.string.picker_empty_all)
                                } else {
                                    stringResource(R.string.picker_empty_query, uiState.appSearchQuery)
                                },
                                style = typo.body,
                                color = palette.textSecondary
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = ZenDimens.ScreenHorizontalPadding,
                            end = ZenDimens.ScreenHorizontalPadding,
                            top = ZenDimens.Space4,
                            bottom = ZenDimens.Space32
                        ),
                        verticalArrangement = Arrangement.spacedBy(ZenDimens.AppCardSpacing)
                    ) {
                        items(
                            items = uiState.availableAppsForPicker,
                            key = { it.packageName }
                        ) { app ->
                            PickerAppCard(
                                app = app,
                                isSelected = app.packageName == selectedPackage,
                                onClick = { onAppSelected(app) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslucentSearchPill(
    query: String,
    onQueryChanged: (String) -> Unit
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ZenDimens.MinTouchTarget)
            .background(palette.surface, ZenShapes.Pill)
            .border(ZenDimens.BorderWidth, palette.border, ZenShapes.Pill)
            .padding(horizontal = ZenDimens.Space24, vertical = ZenDimens.Space12),
        contentAlignment = Alignment.CenterStart
    ) {
        if (query.isEmpty()) {
            Text(
                text = stringResource(R.string.picker_search_hint),
                style = typo.body,
                color = palette.textSecondary
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChanged,
            singleLine = true,
            textStyle = typo.body.copy(color = palette.text),
            cursorBrush = SolidColor(palette.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PickerAppCard(
    app: InstalledAppInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current
    val animationsEnabled = rememberAnimationsEnabled()

    val indicatorFill by animateColorAsState(
        targetValue = if (isSelected) palette.accent else Color.Transparent,
        animationSpec = tween(durationMillis = if (animationsEnabled) 220 else 0),
        label = "picker_circle_fill"
    )

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        onClickLabel = stringResource(R.string.picker_select_cd, app.label)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZenDimens.Space16),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconBadge(
                packageName = app.packageName,
                appLabel = app.label,
                size = ZenDimens.AppIconSize
            )

            Text(
                text = app.label,
                style = typo.subtitle,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(indicatorFill, CircleShape)
                    .border(
                        width = ZenDimens.PinDotBorderWidth,
                        color = palette.accent,
                        shape = CircleShape
                    )
            )
        }
    }
}

private fun samplePickerState(): MainUiState = MainUiState(
    destination = MainDestination.APP_PICKER,
    installedApps = listOf(
        InstalledAppInfo("com.instagram.android", "Instagram"),
        InstalledAppInfo("com.zhiliaoapp.musically", "TikTok"),
        InstalledAppInfo("com.google.android.youtube", "YouTube")
    ),
    limitEditor = LimitEditorDialogState(
        packageName = "com.instagram.android",
        appLabel = "Instagram",
        isNewApp = true,
        minutesInput = "30"
    )
)

@Preview(name = "AppPicker — Claro", showBackground = true, widthDp = 380, heightDp = 760)
@Composable
private fun AppPickerLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        AppPickerScreen(
            uiState = samplePickerState(),
            onBackClick = {},
            onSearchQueryChanged = {},
            onAppSelected = {}
        )
    }
}

@Preview(name = "AppPicker — Oscuro", showBackground = true, widthDp = 380, heightDp = 760)
@Composable
private fun AppPickerDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        AppPickerScreen(
            uiState = samplePickerState(),
            onBackClick = {},
            onSearchQueryChanged = {},
            onAppSelected = {}
        )
    }
}
