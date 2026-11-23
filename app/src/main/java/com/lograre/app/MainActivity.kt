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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.lifecycleScope
import com.lograre.app.data.apps.AppIconLoader
import com.lograre.app.service.AccessibilityServiceState
import com.lograre.app.service.PermissionStatusChecker
import com.lograre.app.ui.main.AppPickerScreen
import com.lograre.app.ui.main.ChangePinScreen
import com.lograre.app.ui.main.HomeScreen
import com.lograre.app.ui.main.InitialPinSetupScreen
import com.lograre.app.ui.main.LimitEditorDialog
import com.lograre.app.ui.main.MainDestination
import com.lograre.app.ui.main.MainScreenController
import com.lograre.app.ui.main.PermissionsGuideScreen
import com.lograre.app.ui.main.PinConfirmationScreen
import com.lograre.app.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

                val pendingAction = uiState.pendingProtectedAction
                if (pendingAction != null) {
                    PinConfirmationScreen(
                        action = pendingAction,
                        pinLength = uiState.pinInput.length,
                        pinError = uiState.pinError,
                        isProcessing = uiState.isProcessing,
                        onDigitClick = controller::onPinDigitPressed,
                        onBackspaceClick = controller::onPinBackspacePressed,
                        onClearClick = controller::onPinClearPressed,
                        onConfirmClick = {
                            scope.launch(Dispatchers.Default) {
                                controller.submitProtectedActionPin()
                            }
                        },
                        onCancelClick = controller::cancelProtectedAction
                    )
                } else {
                    when (uiState.destination) {
                        MainDestination.INITIAL_PIN_SETUP -> {
                            InitialPinSetupScreen(
                                uiState = uiState,
                                onDigitClick = controller::onPinDigitPressed,
                                onBackspaceClick = controller::onPinBackspacePressed,
                                onClearClick = controller::onPinClearPressed,
                                onConfirmClick = {
                                    scope.launch(Dispatchers.Default) {
                                        controller.submitInitialPinStep()
                                    }
                                },
                                onRestartClick = controller::restartPinSetup
                            )
                        }

                        MainDestination.HOME -> {
                            HomeScreen(
                                uiState = uiState,
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
                                onBackClick = controller::navigateHome,
                                onDigitClick = controller::onPinDigitPressed,
                                onBackspaceClick = controller::onPinBackspacePressed,
                                onClearClick = controller::onPinClearPressed,
                                onConfirmClick = {
                                    scope.launch(Dispatchers.Default) {
                                        controller.submitChangePinStep()
                                    }
                                }
                            )
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
        val directIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        try {
            startActivity(directIntent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
