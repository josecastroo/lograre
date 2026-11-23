package com.lograre.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.lograre.app.data.apps.AppIconLoader
import com.lograre.app.service.AccessibilityServiceState
import com.lograre.app.service.PermissionStatusChecker
import com.lograre.app.ui.components.ZenHaptics
import com.lograre.app.ui.components.rememberAnimationsEnabled
import com.lograre.app.ui.main.AppPickerScreen
import com.lograre.app.ui.main.ChangePinScreen
import com.lograre.app.ui.main.HomeScreen
import com.lograre.app.ui.main.InitialPinSetupScreen
import com.lograre.app.ui.main.LimitEditorDialog
import com.lograre.app.ui.main.MainDestination
import com.lograre.app.ui.main.MainScreenController
import com.lograre.app.ui.main.PendingProtectedAction
import com.lograre.app.ui.main.PermissionsGuideScreen
import com.lograre.app.ui.main.PinConfirmationScreen
import com.lograre.app.ui.theme.AppTheme
import com.lograre.app.ui.theme.ZenDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface ActiveScreenKey {
    data class Destination(val destination: MainDestination) : ActiveScreenKey
    data object ProtectedPinConfirmation : ActiveScreenKey
}

class MainActivity : ComponentActivity() {

    private lateinit var controller: MainScreenController

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissionsStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as LograreApplication).container

        controller = MainScreenController(
            pinRepository = container.pinRepository,
            appLimitRepository = container.appLimitRepository,
            dailyResetCoordinator = container.dailyResetCoordinator,
            installedAppsProvider = container.installedAppsProvider,
            labelResolver = { pkg -> AppIconLoader.resolveAppLabel(this, pkg) }
        )

        lifecycleScope.launch {
            container.appLimitRepository.observeAllApps().collect { apps ->
                controller.updateTrackedApps(apps)
            }
        }

        lifecycleScope.launch {
            AccessibilityServiceState.isRunning.collect {
                refreshPermissionsStatus()
            }
        }

        refreshPermissionsStatus()

        setContent {
            val themeMode by container.themePreferenceRepository.themeMode.collectAsState()
            AppTheme(themeMode = themeMode) {
                val uiState by controller.uiState.collectAsState()
                val scope = rememberCoroutineScope()
                val view = LocalView.current
                val density = LocalDensity.current
                val animationsEnabled = rememberAnimationsEnabled()

                var pinErrorTrigger by remember { mutableIntStateOf(0) }

                val isPinScreenVisible = uiState.pendingProtectedAction != null ||
                    uiState.destination == MainDestination.INITIAL_PIN_SETUP ||
                    uiState.destination == MainDestination.CHANGE_PIN

                LaunchedEffect(isPinScreenVisible) {
                    if (isPinScreenVisible) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                BackHandler(
                    enabled = uiState.pendingProtectedAction != null ||
                        (uiState.destination != MainDestination.HOME &&
                            uiState.destination != MainDestination.INITIAL_PIN_SETUP)
                ) {
                    if (uiState.pendingProtectedAction != null) {
                        controller.cancelProtectedAction()
                    } else {
                        controller.navigateHome()
                    }
                }

                val screenKey: ActiveScreenKey = if (uiState.pendingProtectedAction != null) {
                    ActiveScreenKey.ProtectedPinConfirmation
                } else {
                    ActiveScreenKey.Destination(uiState.destination)
                }

                val slideOffsetPx = with(density) { 16.dp.roundToPx() }

                AnimatedContent(
                    targetState = screenKey,
                    transitionSpec = {
                        if (!animationsEnabled) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            val spec = tween<Float>(
                                durationMillis = ZenDimens.ScreenTransitionDurationMs,
                                easing = FastOutSlowInEasing
                            )
                            val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(
                                durationMillis = ZenDimens.ScreenTransitionDurationMs,
                                easing = FastOutSlowInEasing
                            )
                            (fadeIn(spec) + slideInVertically(slideSpec) { slideOffsetPx }) togetherWith
                                (fadeOut(spec) + slideOutVertically(slideSpec) { -slideOffsetPx / 2 })
                        }
                    },
                    label = "main_screen_transition"
                ) { activeKey ->
                    when (activeKey) {
                        ActiveScreenKey.ProtectedPinConfirmation -> {
                            val pendingAction: PendingProtectedAction? = uiState.pendingProtectedAction
                            if (pendingAction != null) {
                                PinConfirmationScreen(
                                    action = pendingAction,
                                    pinLength = uiState.pinInput.length,
                                    pinError = uiState.pinError,
                                    pinErrorTrigger = pinErrorTrigger,
                                    isProcessing = uiState.isProcessing,
                                    onDigitClick = controller::onPinDigitPressed,
                                    onBackspaceClick = controller::onPinBackspacePressed,
                                    onClearClick = controller::onPinClearPressed,
                                    onConfirmClick = {
                                        scope.launch(Dispatchers.Default) {
                                            val ok = controller.submitProtectedActionPin()
                                            withContext(Dispatchers.Main) {
                                                if (ok) {
                                                    ZenHaptics.confirm(view)
                                                } else {
                                                    pinErrorTrigger += 1
                                                    ZenHaptics.reject(view)
                                                }
                                            }
                                        }
                                    },
                                    onCancelClick = controller::cancelProtectedAction
                                )
                            }
                        }

                        is ActiveScreenKey.Destination -> when (activeKey.destination) {
                            MainDestination.INITIAL_PIN_SETUP -> {
                                InitialPinSetupScreen(
                                    uiState = uiState,
                                    pinErrorTrigger = pinErrorTrigger,
                                    onDigitClick = controller::onPinDigitPressed,
                                    onBackspaceClick = controller::onPinBackspacePressed,
                                    onClearClick = controller::onPinClearPressed,
                                    onConfirmClick = {
                                        scope.launch(Dispatchers.Default) {
                                            val ok = controller.submitInitialPinStep()
                                            withContext(Dispatchers.Main) {
                                                if (ok) {
                                                    ZenHaptics.confirm(view)
                                                } else {
                                                    pinErrorTrigger += 1
                                                    ZenHaptics.reject(view)
                                                }
                                            }
                                        }
                                    },
                                    onRestartClick = controller::restartPinSetup
                                )
                            }

                            MainDestination.HOME -> {
                                HomeScreen(
                                    uiState = uiState,
                                    currentThemeMode = themeMode,
                                    onSelectThemeMode = { mode ->
                                        container.themePreferenceRepository.setThemeMode(mode)
                                    },
                                    onAddAppClick = {
                                        scope.launch {
                                            controller.openAppPicker()
                                        }
                                    },
                                    onEditAppClick = controller::openEditAppLimitDialog,
                                    onRemoveAppClick = controller::requestRemoveApp,
                                    onOpenPermissionsClick = {
                                        refreshPermissionsStatus()
                                        controller.openPermissionsGuide()
                                    },
                                    onOpenChangePinClick = controller::openChangePin,
                                    onDismissStatusMessage = controller::clearStatusMessage
                                )
                            }

                            MainDestination.APP_PICKER -> {
                                AppPickerScreen(
                                    uiState = uiState,
                                    onBackClick = controller::navigateHome,
                                    onSearchQueryChanged = controller::onAppSearchQueryChanged,
                                    onAppSelected = controller::openAddAppLimitDialog
                                )
                            }

                            MainDestination.PERMISSIONS_GUIDE -> {
                                PermissionsGuideScreen(
                                    status = uiState.permissionsStatus,
                                    onBackClick = controller::navigateHome,
                                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
                                    onRequestNotificationsPermission = ::requestNotificationsOrOpenSettings,
                                    onRequestBatteryExemption = ::requestBatteryOptimizationExemption
                                )
                            }

                            MainDestination.CHANGE_PIN -> {
                                ChangePinScreen(
                                    uiState = uiState,
                                    pinErrorTrigger = pinErrorTrigger,
                                    onBackClick = controller::navigateHome,
                                    onDigitClick = controller::onPinDigitPressed,
                                    onBackspaceClick = controller::onPinBackspacePressed,
                                    onClearClick = controller::onPinClearPressed,
                                    onConfirmClick = {
                                        scope.launch(Dispatchers.Default) {
                                            val ok = controller.submitChangePinStep()
                                            withContext(Dispatchers.Main) {
                                                if (ok) {
                                                    ZenHaptics.confirm(view)
                                                } else {
                                                    pinErrorTrigger += 1
                                                    ZenHaptics.reject(view)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                val limitEditor = uiState.limitEditor
                if (limitEditor != null) {
                    LimitEditorDialog(
                        state = limitEditor,
                        onMinutesChanged = controller::onLimitMinutesInputChanged,
                        onConfirm = controller::confirmLimitEditorAndRequestPin,
                        onDismiss = controller::dismissLimitEditor
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionsStatus()
        val container = (application as LograreApplication).container
        lifecycleScope.launch(Dispatchers.IO) {
            container.dailyResetCoordinator.checkAndPerformDailyReset(attemptNtpSync = true)
        }
    }

    private fun refreshPermissionsStatus() {
        if (!::controller.isInitialized) return
        controller.updatePermissionsStatus(PermissionStatusChecker.check(this))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestNotificationsOrOpenSettings() {
        val status = PermissionStatusChecker.check(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !status.notificationsGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationExemption() {
        val status = PermissionStatusChecker.check(this)
        val appDetailsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        )

        if (status.batteryOptimizationIgnored) {
            try {
                startActivity(appDetailsIntent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            return
        }

        val directIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        try {
            startActivity(directIntent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                startActivity(appDetailsIntent)
            }
        }
    }
}
