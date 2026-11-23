package com.lograre.app.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lograre.app.R
import com.lograre.app.domain.model.AppLimitStatus
import com.lograre.app.domain.model.TrackedApp
import com.lograre.app.service.AppPermissionsStatus
import com.lograre.app.ui.components.GlassCard
import com.lograre.app.ui.components.WaterLevelIndicator
import com.lograre.app.ui.components.ZenBackground
import com.lograre.app.ui.components.ZenButton
import com.lograre.app.ui.components.ZenButtonStyle
import com.lograre.app.ui.components.ZenTextButton
import com.lograre.app.ui.components.rememberAnimationsEnabled
import com.lograre.app.ui.components.waterRipple
import com.lograre.app.ui.theme.AppTheme
import com.lograre.app.ui.theme.LocalZenPalette
import com.lograre.app.ui.theme.LocalZenTypography
import com.lograre.app.ui.theme.ThemeMode
import com.lograre.app.ui.theme.ZenDimens
import com.lograre.app.ui.theme.ZenShapes
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: MainUiState,
    currentThemeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onAddAppClick: () -> Unit,
    onEditAppClick: (TrackedApp) -> Unit,
    onRemoveAppClick: (TrackedApp) -> Unit,
    onOpenPermissionsClick: () -> Unit,
    onOpenChangePinClick: () -> Unit,
    onDismissStatusMessage: () -> Unit
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current
    val animationsEnabled = rememberAnimationsEnabled()

    var showAppearanceSheet by remember { mutableStateOf(false) }
    var selectedAppForActions by remember { mutableStateOf<TrackedApp?>(null) }

    val greetingResId = remember {
        HomeGreeting.greetingResIdForHour(LocalTime.now().hour)
    }

    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null) {
            delay(2_600L)
            onDismissStatusMessage()
        }
    }

    ZenBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(
                    start = ZenDimens.ScreenHorizontalPadding,
                    end = ZenDimens.ScreenHorizontalPadding,
                    top = ZenDimens.Space24,
                    bottom = 136.dp
                ),
                verticalArrangement = Arrangement.spacedBy(ZenDimens.AppCardSpacing)
            ) {
                item(key = "home_header") {
                    HomeHeader(
                        greeting = stringResource(greetingResId),
                        summary = buildSummaryText(uiState.trackedApps),
                        accessibilityEnabled = uiState.permissionsStatus.accessibilityEnabled,
                        onOpenAppearanceClick = { showAppearanceSheet = true }
                    )
                    Spacer(modifier = Modifier.height(ZenDimens.Space8))
                }

                if (!uiState.permissionsStatus.accessibilityEnabled) {
                    item(key = "accessibility_notice") {
                        AccessibilityNoticeCard(onOpenPermissionsClick = onOpenPermissionsClick)
                    }
                }

                if (uiState.trackedApps.isEmpty()) {
                    item(key = "empty_state") {
                        EmptyTrackedAppsCard()
                    }
                } else {
                    itemsIndexed(
                        items = uiState.trackedApps,
                        key = { _, app -> app.packageName }
                    ) { index, app ->
                        val label = uiState.appLabelsByPackage[app.packageName] ?: app.packageName
                        StaggeredItemContainer(
                            index = index,
                            animationsEnabled = animationsEnabled
                        ) {
                            TrackedAppZenCard(
                                app = app,
                                appLabel = label,
                                onClick = { selectedAppForActions = app }
                            )
                        }
                    }
                }
            }

            // Bottom floating calm status toast + fixed "Añadir app" pill button
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        horizontal = ZenDimens.ScreenHorizontalPadding,
                        vertical = ZenDimens.Space24
                    ),
                verticalArrangement = Arrangement.spacedBy(ZenDimens.Space12),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = uiState.statusMessage != null,
                    enter = fadeIn(tween(if (animationsEnabled) 250 else 0)),
                    exit = fadeOut(tween(if (animationsEnabled) 250 else 0))
                ) {
                    val message = uiState.statusMessage.orEmpty()
                    GlassCard(
                        shape = ZenShapes.Pill,
                        contentPadding = PaddingValues(
                            horizontal = ZenDimens.Space16,
                            vertical = ZenDimens.Space12
                        )
                    ) {
                        Text(
                            text = message,
                            style = typo.body,
                            color = palette.text
                        )
                    }
                }

                ZenButton(
                    text = stringResource(R.string.home_add_app),
                    onClick = onAddAppClick,
                    style = ZenButtonStyle.SECONDARY
                )
            }
        }
    }

    if (showAppearanceSheet) {
        AppearanceBottomSheet(
            currentThemeMode = currentThemeMode,
            accessibilityEnabled = uiState.permissionsStatus.accessibilityEnabled,
            onSelectThemeMode = onSelectThemeMode,
            onOpenPermissionsClick = onOpenPermissionsClick,
            onOpenChangePinClick = onOpenChangePinClick,
            onDismiss = { showAppearanceSheet = false }
        )
    }

    val appForSheet = selectedAppForActions
    if (appForSheet != null) {
        val label = uiState.appLabelsByPackage[appForSheet.packageName] ?: appForSheet.packageName
        AppActionsBottomSheet(
            appLabel = label,
            onAdjustTime = {
                selectedAppForActions = null
                onEditAppClick(appForSheet)
            },
            onRemoveApp = {
                selectedAppForActions = null
                onRemoveAppClick(appForSheet)
            },
            onDismiss = { selectedAppForActions = null }
        )
    }
}

@Composable
private fun buildSummaryText(apps: List<TrackedApp>): String {
    if (apps.isEmpty()) return stringResource(R.string.home_summary_empty)
    val availableCount = apps.count { !it.isBlocked }
    return when {
        availableCount == 0 -> stringResource(R.string.home_summary_all_paused)
        availableCount == 1 -> stringResource(R.string.home_summary_available_one)
        else -> stringResource(R.string.home_summary_available_many, availableCount)
    }
}

@Composable
private fun HomeHeader(
    greeting: String,
    summary: String,
    accessibilityEnabled: Boolean,
    onOpenAppearanceClick: () -> Unit
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current

    Column(verticalArrangement = Arrangement.spacedBy(ZenDimens.Space8)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = greeting,
                style = typo.headline,
                color = palette.text,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(ZenDimens.MinTouchTarget)
                    .clip(CircleShape)
                    .background(palette.surface, CircleShape)
                    .border(ZenDimens.BorderWidth, palette.border, CircleShape)
                    .waterRipple(
                        onClickLabel = stringResource(R.string.home_settings_cd),
                        role = Role.Button,
                        onClick = onOpenAppearanceClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_zen_settings),
                    contentDescription = stringResource(R.string.home_settings_cd),
                    tint = palette.text,
                    modifier = Modifier.size(22.dp)
                )
                if (!accessibilityEnabled) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(8.dp)
                            .background(palette.accent2, CircleShape)
                    )
                }
            }
        }

        Text(
            text = summary,
            style = typo.body,
            color = palette.textSecondary
        )
    }
}

@Composable
private fun AccessibilityNoticeCard(
    onOpenPermissionsClick: () -> Unit
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ZenDimens.Space12)) {
            Text(
                text = stringResource(R.string.home_accessibility_needed_title),
                style = typo.subtitle,
                color = palette.text
            )
            Text(
                text = stringResource(R.string.home_accessibility_needed_body),
                style = typo.body,
                color = palette.textSecondary
            )
            ZenButton(
                text = stringResource(R.string.home_open_permissions),
                onClick = onOpenPermissionsClick,
                style = ZenButtonStyle.PRIMARY
            )
        }
    }
}

@Composable
private fun EmptyTrackedAppsCard() {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ZenDimens.Space16),
        contentPadding = PaddingValues(ZenDimens.Space24)
    ) {
        Text(
            text = stringResource(R.string.home_empty_message),
            style = typo.body,
            color = palette.textSecondary
        )
    }
}

@Composable
private fun TrackedAppZenCard(
    app: TrackedApp,
    appLabel: String,
    onClick: () -> Unit
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current

    val extraMinutes = (app.extraMillisToday / TrackedApp.MILLIS_PER_MINUTE).toInt()
    val usedFormatted = DurationFormatter.formatMillis(app.usedMillisToday)
    val baseLimitFormatted = DurationFormatter.formatMinutes(app.dailyLimitMinutes)

    val usageText = if (extraMinutes > 0) {
        stringResource(R.string.app_usage_summary_with_extra, usedFormatted, baseLimitFormatted, extraMinutes)
    } else {
        stringResource(R.string.app_usage_summary, usedFormatted, baseLimitFormatted)
    }

    val totalEffectiveMinutes = (app.effectiveLimitMillis / TrackedApp.MILLIS_PER_MINUTE).toInt()
    val remainingMinutes = ceil(app.remainingMillis / TrackedApp.MILLIS_PER_MINUTE.toDouble()).toInt()
    val waterDescription = when (app.status) {
        AppLimitStatus.BLOCKED -> stringResource(R.string.water_cd_paused, appLabel)
        AppLimitStatus.IGNORED_TODAY -> stringResource(R.string.water_cd_free_today, appLabel)
        AppLimitStatus.ACTIVE -> stringResource(
            R.string.water_cd_remaining,
            appLabel,
            remainingMinutes,
            totalEffectiveMinutes
        )
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ZenDimens.AppCardMinHeight),
        onClick = onClick,
        onClickLabel = stringResource(R.string.adjust_time)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZenDimens.Space16),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconBadge(
                packageName = app.packageName,
                appLabel = appLabel,
                size = ZenDimens.AppIconSize
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ZenDimens.Space4)
            ) {
                Text(
                    text = appLabel,
                    style = typo.subtitle,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = usageText,
                    style = typo.body,
                    color = palette.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                when (app.status) {
                    AppLimitStatus.BLOCKED -> {
                        Text(
                            text = stringResource(R.string.status_paused),
                            style = typo.label,
                            color = palette.textSecondary
                        )
                    }
                    AppLimitStatus.IGNORED_TODAY -> {
                        Text(
                            text = stringResource(R.string.status_free_today),
                            style = typo.label,
                            color = palette.textSecondary
                        )
                    }
                    AppLimitStatus.ACTIVE -> Unit
                }
            }

            WaterLevelIndicator(
                status = app.status,
                remainingMillis = app.remainingMillis,
                effectiveLimitMillis = app.effectiveLimitMillis,
                accessibilityDescription = waterDescription
            )
        }
    }
}

@Composable
private fun StaggeredItemContainer(
    index: Int,
    animationsEnabled: Boolean,
    content: @Composable () -> Unit
) {
    if (!animationsEnabled) {
        content()
        return
    }

    val progress = remember { Animatable(0f) }
    val offsetPx = with(LocalDensity.current) { 16.dp.toPx() }

    LaunchedEffect(Unit) {
        val delayMs = (index.coerceAtMost(8) * ZenDimens.ListItemStaggerMs).toLong()
        if (delayMs > 0L) delay(delayMs)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = ZenDimens.ListItemAppearDurationMs,
                easing = FastOutSlowInEasing
            )
        )
    }

    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * offsetPx
        }
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppActionsBottomSheet(
    appLabel: String,
    onAdjustTime: () -> Unit,
    onRemoveApp: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current
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
            verticalArrangement = Arrangement.spacedBy(ZenDimens.Space12)
        ) {
            Text(
                text = appLabel,
                style = typo.title,
                color = palette.text
            )
            ZenButton(
                text = stringResource(R.string.adjust_time),
                onClick = onAdjustTime,
                style = ZenButtonStyle.PRIMARY
            )
            ZenButton(
                text = stringResource(R.string.remove_app),
                onClick = onRemoveApp,
                style = ZenButtonStyle.SECONDARY
            )
        }
    }
}

@Composable
fun LimitEditorDialog(
    state: LimitEditorDialogState,
    onMinutesChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current
    val dialogBackground = palette.surface.compositeOver(palette.bgTop)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZenShapes.Dialog)
                .background(dialogBackground, ZenShapes.Dialog)
                .border(ZenDimens.BorderWidth, palette.border, ZenShapes.Dialog)
                .padding(ZenDimens.Space24),
            verticalArrangement = Arrangement.spacedBy(ZenDimens.Space16)
        ) {
            Text(
                text = if (state.isNewApp) {
                    stringResource(R.string.dialog_add_app_title, state.appLabel)
                } else {
                    stringResource(R.string.adjust_time)
                },
                style = typo.title,
                color = palette.text
            )

            Text(
                text = stringResource(R.string.dialog_limit_hint),
                style = typo.body,
                color = palette.textSecondary
            )

            OutlinedTextField(
                value = state.minutesInput,
                onValueChange = onMinutesChanged,
                label = { Text(stringResource(R.string.dialog_limit_label)) },
                singleLine = true,
                shape = ZenShapes.WaterCapsule,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.error != null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.border,
                    focusedTextColor = palette.text,
                    unfocusedTextColor = palette.text,
                    focusedLabelColor = palette.accent,
                    unfocusedLabelColor = palette.textSecondary,
                    cursorColor = palette.accent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (state.error != null) {
                Text(
                    text = state.error,
                    style = typo.label,
                    color = palette.text
                )
            }

            ZenButton(
                text = stringResource(R.string.action_continue_with_pin),
                onClick = onConfirm,
                style = ZenButtonStyle.PRIMARY
            )

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ZenTextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    color = palette.textSecondary
                )
            }
        }
    }
}

private fun sampleHomeUiState(): MainUiState = MainUiState(
    destination = MainDestination.HOME,
    trackedApps = listOf(
        TrackedApp("com.instagram.android", dailyLimitMinutes = 30, usedMillisToday = 10 * 60_000L),
        TrackedApp("com.google.android.youtube", dailyLimitMinutes = 30, usedMillisToday = 26 * 60_000L),
        TrackedApp("com.zhiliaoapp.musically", dailyLimitMinutes = 15, usedMillisToday = 15 * 60_000L),
        TrackedApp("com.reddit.frontpage", dailyLimitMinutes = 20, usedMillisToday = 5 * 60_000L, ignoredToday = true)
    ),
    appLabelsByPackage = mapOf(
        "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.reddit.frontpage" to "Reddit"
    ),
    permissionsStatus = AppPermissionsStatus(
        accessibilityEnabled = true,
        notificationsGranted = true,
        batteryOptimizationIgnored = true
    )
)

@Preview(name = "HomeScreen — Claro", showBackground = true, widthDp = 380, heightDp = 760)
@Composable
private fun HomeScreenLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        HomeScreen(
            uiState = sampleHomeUiState(),
            currentThemeMode = ThemeMode.LIGHT,
            onSelectThemeMode = {},
            onAddAppClick = {},
            onEditAppClick = {},
            onRemoveAppClick = {},
            onOpenPermissionsClick = {},
            onOpenChangePinClick = {},
            onDismissStatusMessage = {}
        )
    }
}

@Preview(name = "HomeScreen — Oscuro", showBackground = true, widthDp = 380, heightDp = 760)
@Composable
private fun HomeScreenDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        HomeScreen(
            uiState = sampleHomeUiState(),
            currentThemeMode = ThemeMode.DARK,
            onSelectThemeMode = {},
            onAddAppClick = {},
            onEditAppClick = {},
            onRemoveAppClick = {},
            onOpenPermissionsClick = {},
            onOpenChangePinClick = {},
            onDismissStatusMessage = {}
        )
    }
}

@Preview(
    name = "HomeScreen 200% — Claro",
    showBackground = true,
    widthDp = 380,
    heightDp = 760,
    fontScale = 2.0f
)
@Composable
private fun HomeScreenLargeFontPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        HomeScreen(
            uiState = sampleHomeUiState(),
            currentThemeMode = ThemeMode.LIGHT,
            onSelectThemeMode = {},
            onAddAppClick = {},
            onEditAppClick = {},
            onRemoveAppClick = {},
            onOpenPermissionsClick = {},
            onOpenChangePinClick = {},
            onDismissStatusMessage = {}
        )
    }
}
