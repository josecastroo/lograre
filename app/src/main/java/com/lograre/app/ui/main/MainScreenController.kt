package com.lograre.app.ui.main

import com.lograre.app.data.apps.InstalledAppInfo
import com.lograre.app.data.apps.InstalledAppsProvider
import com.lograre.app.data.repository.AppLimitRepository
import com.lograre.app.data.security.PinOperationResult
import com.lograre.app.data.security.PinRepository
import com.lograre.app.data.security.PinValidationResult
import com.lograre.app.data.security.PinValidator
import com.lograre.app.domain.clock.DailyResetCoordinator
import com.lograre.app.domain.clock.DailyResetResult
import com.lograre.app.domain.model.TrackedApp
import com.lograre.app.service.AppPermissionsStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class MainDestination {
    INITIAL_PIN_SETUP,
    HOME,
    APP_PICKER,
    PERMISSIONS_GUIDE,
    CHANGE_PIN
}

enum class PinSetupStep {
    ENTER_NEW_PIN,
    CONFIRM_NEW_PIN
}

enum class ChangePinStep {
    VERIFY_CURRENT_PIN,
    ENTER_NEW_PIN,
    CONFIRM_NEW_PIN
}

sealed interface PendingProtectedAction {
    val title: String
    val description: String

    data class AddApp(
        val packageName: String,
        val appLabel: String,
        val dailyLimitMinutes: Int
    ) : PendingProtectedAction {
        override val title: String = "Confirmar nueva app"
        override val description: String =
            "Introduce el PIN para añadir $appLabel con límite de ${DurationFormatter.formatMinutes(dailyLimitMinutes)}."
    }

    data class EditAppLimit(
        val packageName: String,
        val appLabel: String,
        val newLimitMinutes: Int
    ) : PendingProtectedAction {
        override val title: String = "Confirmar cambio de límite"
        override val description: String =
            "Introduce el PIN para cambiar el límite de $appLabel a ${DurationFormatter.formatMinutes(newLimitMinutes)}."
    }

    data class RemoveApp(
        val packageName: String,
        val appLabel: String
    ) : PendingProtectedAction {
        override val title: String = "Confirmar eliminación"
        override val description: String =
            "Introduce el PIN para quitar $appLabel de la lista supervisada."
    }
}

data class LimitEditorDialogState(
    val packageName: String,
    val appLabel: String,
    val isNewApp: Boolean,
    val minutesInput: String,
    val error: String? = null
)

data class MainUiState(
    val destination: MainDestination,
    val trackedApps: List<TrackedApp> = emptyList(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val appLabelsByPackage: Map<String, String> = emptyMap(),
    val appSearchQuery: String = "",
    val isLoadingInstalledApps: Boolean = false,
    val pinSetupStep: PinSetupStep = PinSetupStep.ENTER_NEW_PIN,
    val pinSetupFirstEntry: String = "",
    val changePinStep: ChangePinStep = ChangePinStep.VERIFY_CURRENT_PIN,
    val changePinCurrentVerified: String = "",
    val changePinNewEntry: String = "",
    val pinInput: String = "",
    val pinError: String? = null,
    val limitEditor: LimitEditorDialogState? = null,
    val pendingProtectedAction: PendingProtectedAction? = null,
    val permissionsStatus: AppPermissionsStatus = AppPermissionsStatus(),
    val statusMessage: String? = null,
    val isProcessing: Boolean = false
) {
    val availableAppsForPicker: List<InstalledAppInfo>
        get() {
            val trackedPackages = trackedApps.mapTo(HashSet()) { it.packageName }
            val query = appSearchQuery.trim()
            return installedApps.filter { app ->
                app.packageName !in trackedPackages &&
                    (query.isEmpty() ||
                        app.label.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true))
            }
        }
}

/**
 * Pure Kotlin state machine for all main flows in lograre:
 * initial PIN setup, tracked apps management, PIN-gated edits, PIN change, and permissions guide.
 */
class MainScreenController(
    private val pinRepository: PinRepository,
    private val appLimitRepository: AppLimitRepository,
    private val dailyResetCoordinator: DailyResetCoordinator,
    private val installedAppsProvider: InstalledAppsProvider,
    private val labelResolver: (String) -> String = { it }
) {
    private val _uiState = MutableStateFlow(
        MainUiState(
            destination = if (pinRepository.isPinConfigured()) {
                MainDestination.HOME
            } else {
                MainDestination.INITIAL_PIN_SETUP
            }
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun updateTrackedApps(apps: List<TrackedApp>) {
        _uiState.update { state ->
            val updatedLabels = state.appLabelsByPackage.toMutableMap()
            apps.forEach { app ->
                if (app.packageName !in updatedLabels) {
                    updatedLabels[app.packageName] = labelResolver(app.packageName)
                }
            }
            state.copy(
                trackedApps = apps,
                appLabelsByPackage = updatedLabels
            )
        }
    }

    fun updatePermissionsStatus(status: AppPermissionsStatus) {
        _uiState.update { it.copy(permissionsStatus = status) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    // --- Navigation ---

    suspend fun openAppPicker() {
        _uiState.update {
            it.copy(
                destination = MainDestination.APP_PICKER,
                appSearchQuery = "",
                isLoadingInstalledApps = true
            )
        }
        val apps = try {
            installedAppsProvider.getLaunchableApps()
        } catch (_: Exception) {
            emptyList()
        }
        _uiState.update { state ->
            val updatedLabels = state.appLabelsByPackage.toMutableMap()
            apps.forEach { updatedLabels[it.packageName] = it.label }
            state.copy(
                installedApps = apps,
                appLabelsByPackage = updatedLabels,
                isLoadingInstalledApps = false
            )
        }
    }

    fun openPermissionsGuide() {
        _uiState.update { it.copy(destination = MainDestination.PERMISSIONS_GUIDE) }
    }

    fun openChangePin() {
        _uiState.update {
            it.copy(
                destination = MainDestination.CHANGE_PIN,
                changePinStep = ChangePinStep.VERIFY_CURRENT_PIN,
                changePinCurrentVerified = "",
                changePinNewEntry = "",
                pinInput = "",
                pinError = null
            )
        }
    }

    fun navigateHome() {
        if (!pinRepository.isPinConfigured()) return
        _uiState.update {
            it.copy(
                destination = MainDestination.HOME,
                pinInput = "",
                pinError = null,
                limitEditor = null,
                pendingProtectedAction = null,
                changePinCurrentVerified = "",
                changePinNewEntry = ""
            )
        }
    }

    fun onAppSearchQueryChanged(query: String) {
        _uiState.update { it.copy(appSearchQuery = query) }
    }

    // --- Shared PIN Keypad Inputs ---

    fun onPinDigitPressed(digit: Char) {
        if (digit !in '0'..'9') return
        _uiState.update { state ->
            if (state.isProcessing) return@update state
            state.copy(pinInput = state.pinInput + digit, pinError = null)
        }
    }

    fun onPinBackspacePressed() {
        _uiState.update { state ->
            if (state.isProcessing || state.pinInput.isEmpty()) return@update state
            state.copy(pinInput = state.pinInput.dropLast(1), pinError = null)
        }
    }

    fun onPinClearPressed() {
        _uiState.update { state ->
            if (state.isProcessing) return@update state
            state.copy(pinInput = "", pinError = null)
        }
    }

    // --- Initial PIN Setup ---

    fun restartPinSetup() {
        _uiState.update {
            it.copy(
                pinSetupStep = PinSetupStep.ENTER_NEW_PIN,
                pinSetupFirstEntry = "",
                pinInput = "",
                pinError = null
            )
        }
    }

    fun submitInitialPinStep(): Boolean {
        val state = _uiState.value
        if (state.destination != MainDestination.INITIAL_PIN_SETUP || state.isProcessing) return false

        return when (state.pinSetupStep) {
            PinSetupStep.ENTER_NEW_PIN -> {
                when (val validation = PinValidator.validate(state.pinInput)) {
                    PinValidationResult.Valid -> {
                        _uiState.update {
                            it.copy(
                                pinSetupFirstEntry = state.pinInput,
                                pinSetupStep = PinSetupStep.CONFIRM_NEW_PIN,
                                pinInput = "",
                                pinError = null
                            )
                        }
                        true
                    }
                    is PinValidationResult.TooShort -> {
                        _uiState.update {
                            it.copy(pinError = "El PIN debe tener al menos ${validation.minLength} dígitos")
                        }
                        false
                    }
                    PinValidationResult.InvalidCharacters -> {
                        _uiState.update { it.copy(pinError = "El PIN solo puede contener números") }
                        false
                    }
                }
            }

            PinSetupStep.CONFIRM_NEW_PIN -> {
                when (val result = pinRepository.setupPin(state.pinSetupFirstEntry, state.pinInput)) {
                    PinOperationResult.Success -> {
                        _uiState.update {
                            it.copy(
                                destination = MainDestination.HOME,
                                pinSetupFirstEntry = "",
                                pinInput = "",
                                pinError = null,
                                statusMessage = "PIN configurado correctamente"
                            )
                        }
                        true
                    }
                    PinOperationResult.ConfirmationMismatch -> {
                        _uiState.update {
                            it.copy(
                                pinInput = "",
                                pinError = "Los PIN no coinciden. Vuelve a confirmarlo o reinicia."
                            )
                        }
                        false
                    }
                    PinOperationResult.PinAlreadyConfigured -> {
                        _uiState.update {
                            it.copy(
                                destination = MainDestination.HOME,
                                pinSetupFirstEntry = "",
                                pinInput = "",
                                pinError = null
                            )
                        }
                        true
                    }
                    is PinOperationResult.ValidationError -> {
                        _uiState.update {
                            it.copy(
                                pinSetupStep = PinSetupStep.ENTER_NEW_PIN,
                                pinSetupFirstEntry = "",
                                pinInput = "",
                                pinError = validationMessage(result.reason)
                            )
                        }
                        false
                    }
                    else -> false
                }
            }
        }
    }

    // --- Change PIN ---

    fun submitChangePinStep(): Boolean {
        val state = _uiState.value
        if (state.destination != MainDestination.CHANGE_PIN || state.isProcessing) return false

        return when (state.changePinStep) {
            ChangePinStep.VERIFY_CURRENT_PIN -> {
                if (pinRepository.verifyPin(state.pinInput)) {
                    _uiState.update {
                        it.copy(
                            changePinCurrentVerified = state.pinInput,
                            changePinStep = ChangePinStep.ENTER_NEW_PIN,
                            pinInput = "",
                            pinError = null
                        )
                    }
                    true
                } else {
                    _uiState.update {
                        it.copy(pinInput = "", pinError = "PIN actual incorrecto")
                    }
                    false
                }
            }

            ChangePinStep.ENTER_NEW_PIN -> {
                when (val validation = PinValidator.validate(state.pinInput)) {
                    PinValidationResult.Valid -> {
                        _uiState.update {
                            it.copy(
                                changePinNewEntry = state.pinInput,
                                changePinStep = ChangePinStep.CONFIRM_NEW_PIN,
                                pinInput = "",
                                pinError = null
                            )
                        }
                        true
                    }
                    else -> {
                        _uiState.update { it.copy(pinError = validationMessage(validation)) }
                        false
                    }
                }
            }

            ChangePinStep.CONFIRM_NEW_PIN -> {
                val result = pinRepository.changePin(
                    currentPin = state.changePinCurrentVerified,
                    newPin = state.changePinNewEntry,
                    newConfirmation = state.pinInput
                )
                when (result) {
                    PinOperationResult.Success -> {
                        _uiState.update {
                            it.copy(
                                destination = MainDestination.HOME,
                                changePinCurrentVerified = "",
                                changePinNewEntry = "",
                                pinInput = "",
                                pinError = null,
                                statusMessage = "PIN cambiado correctamente"
                            )
                        }
                        true
                    }
                    PinOperationResult.ConfirmationMismatch -> {
                        _uiState.update {
                            it.copy(
                                pinInput = "",
                                pinError = "La confirmación no coincide con el nuevo PIN"
                            )
                        }
                        false
                    }
                    PinOperationResult.InvalidCurrentPin -> {
                        _uiState.update {
                            it.copy(
                                changePinStep = ChangePinStep.VERIFY_CURRENT_PIN,
                                changePinCurrentVerified = "",
                                changePinNewEntry = "",
                                pinInput = "",
                                pinError = "El PIN actual no es válido"
                            )
                        }
                        false
                    }
                    is PinOperationResult.ValidationError -> {
                        _uiState.update {
                            it.copy(
                                changePinStep = ChangePinStep.ENTER_NEW_PIN,
                                changePinNewEntry = "",
                                pinInput = "",
                                pinError = validationMessage(result.reason)
                            )
                        }
                        false
                    }
                    else -> false
                }
            }
        }
    }

    // --- Add / Edit / Remove Apps (PIN-gated) ---

    fun openAddAppLimitDialog(app: InstalledAppInfo) {
        _uiState.update {
            it.copy(
                limitEditor = LimitEditorDialogState(
                    packageName = app.packageName,
                    appLabel = app.label,
                    isNewApp = true,
                    minutesInput = DEFAULT_NEW_LIMIT_MINUTES
                )
            )
        }
    }

    fun openEditAppLimitDialog(app: TrackedApp) {
        val label = _uiState.value.appLabelsByPackage[app.packageName] ?: labelResolver(app.packageName)
        _uiState.update {
            it.copy(
                limitEditor = LimitEditorDialogState(
                    packageName = app.packageName,
                    appLabel = label,
                    isNewApp = false,
                    minutesInput = app.dailyLimitMinutes.toString()
                )
            )
        }
    }

    fun onLimitMinutesInputChanged(input: String) {
        val digitsOnly = input.filter { it in '0'..'9' }.take(4)
        _uiState.update { state ->
            val editor = state.limitEditor ?: return@update state
            state.copy(limitEditor = editor.copy(minutesInput = digitsOnly, error = null))
        }
    }

    fun dismissLimitEditor() {
        _uiState.update { it.copy(limitEditor = null) }
    }

    suspend fun confirmLimitEditor(): Boolean {
        val editor = _uiState.value.limitEditor ?: return false
        val minutes = editor.minutesInput.toIntOrNull()
        if (minutes == null || minutes < 0) {
            _uiState.update {
                it.copy(limitEditor = editor.copy(error = "Introduce un número de minutos mayor o igual a 0"))
            }
            return false
        }

        if (editor.isNewApp) {
            _uiState.update { it.copy(isProcessing = true) }
            return try {
                val resetResult = dailyResetCoordinator.checkAndPerformDailyReset()
                val (dayId, nowUtc) = when (resetResult) {
                    is DailyResetResult.ResetPerformed -> resetResult.newDayId to resetResult.snapshot.utcMillis
                    is DailyResetResult.NoResetNeeded -> resetResult.currentDayId to resetResult.snapshot.utcMillis
                }
                appLimitRepository.addOrUpdateApp(
                    packageName = editor.packageName,
                    dailyLimitMinutes = minutes,
                    currentDayId = dayId,
                    nowMillis = nowUtc
                )
                _uiState.update {
                    it.copy(
                        destination = MainDestination.HOME,
                        limitEditor = null,
                        pendingProtectedAction = null,
                        isProcessing = false,
                        statusMessage = "${editor.appLabel} añadida (${DurationFormatter.formatMinutes(minutes)})"
                    )
                }
                true
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        limitEditor = editor.copy(error = "No se pudo añadir la aplicación")
                    )
                }
                false
            }
        }

        val action = PendingProtectedAction.EditAppLimit(
            packageName = editor.packageName,
            appLabel = editor.appLabel,
            newLimitMinutes = minutes
        )

        _uiState.update {
            it.copy(
                limitEditor = null,
                pendingProtectedAction = action,
                pinInput = "",
                pinError = null
            )
        }
        return true
    }

    fun requestRemoveApp(app: TrackedApp) {
        val label = _uiState.value.appLabelsByPackage[app.packageName] ?: labelResolver(app.packageName)
        _uiState.update {
            it.copy(
                pendingProtectedAction = PendingProtectedAction.RemoveApp(
                    packageName = app.packageName,
                    appLabel = label
                ),
                pinInput = "",
                pinError = null
            )
        }
    }

    fun cancelProtectedAction() {
        _uiState.update {
            it.copy(
                pendingProtectedAction = null,
                pinInput = "",
                pinError = null
            )
        }
    }

    suspend fun submitProtectedActionPin(): Boolean {
        val state = _uiState.value
        val action = state.pendingProtectedAction ?: return false
        if (state.isProcessing) return false

        if (!pinRepository.verifyPin(state.pinInput)) {
            _uiState.update { it.copy(pinInput = "", pinError = "PIN incorrecto") }
            return false
        }

        _uiState.update { it.copy(isProcessing = true, pinError = null) }
        return try {
            val resetResult = dailyResetCoordinator.checkAndPerformDailyReset()
            val (dayId, nowUtc) = when (resetResult) {
                is DailyResetResult.ResetPerformed -> resetResult.newDayId to resetResult.snapshot.utcMillis
                is DailyResetResult.NoResetNeeded -> resetResult.currentDayId to resetResult.snapshot.utcMillis
            }

            val message = when (action) {
                is PendingProtectedAction.AddApp -> {
                    appLimitRepository.addOrUpdateApp(
                        packageName = action.packageName,
                        dailyLimitMinutes = action.dailyLimitMinutes,
                        currentDayId = dayId,
                        nowMillis = nowUtc
                    )
                    "${action.appLabel} añadida (${DurationFormatter.formatMinutes(action.dailyLimitMinutes)})"
                }

                is PendingProtectedAction.EditAppLimit -> {
                    appLimitRepository.addOrUpdateApp(
                        packageName = action.packageName,
                        dailyLimitMinutes = action.newLimitMinutes,
                        currentDayId = dayId,
                        nowMillis = nowUtc
                    )
                    "Límite de ${action.appLabel} actualizado"
                }

                is PendingProtectedAction.RemoveApp -> {
                    appLimitRepository.removeApp(action.packageName)
                    "${action.appLabel} eliminada de la lista"
                }
            }

            _uiState.update {
                it.copy(
                    destination = MainDestination.HOME,
                    pendingProtectedAction = null,
                    pinInput = "",
                    pinError = null,
                    isProcessing = false,
                    statusMessage = message
                )
            }
            true
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    pinInput = "",
                    pinError = "No se pudo guardar el cambio"
                )
            }
            false
        }
    }

    private fun validationMessage(result: PinValidationResult): String = when (result) {
        PinValidationResult.Valid -> ""
        is PinValidationResult.TooShort -> "El PIN debe tener al menos ${result.minLength} dígitos"
        PinValidationResult.InvalidCharacters -> "El PIN solo puede contener números"
    }

    companion object {
        private const val DEFAULT_NEW_LIMIT_MINUTES = ""
    }
}
