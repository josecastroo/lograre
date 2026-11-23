package com.lograre.app.ui.lock

import com.lograre.app.data.repository.AppLimitRepository
import com.lograre.app.data.security.PinRepository
import com.lograre.app.domain.clock.DailyResetCoordinator
import com.lograre.app.domain.clock.DailyResetResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LockScreenStage {
    PIN_ENTRY,
    UNLOCKED_OPTIONS,
    PIN_NOT_CONFIGURED
}

sealed interface LockScreenOutcome {
    data object UnlockedReturnToApp : LockScreenOutcome
    data object ExitToLauncher : LockScreenOutcome
}

data class LockScreenUiState(
    val packageName: String,
    val appLabel: String,
    val stage: LockScreenStage,
    val pinInput: String = "",
    val pinError: String? = null,
    val customMinutesInput: String = "",
    val customMinutesError: String? = null,
    val isProcessing: Boolean = false,
    val outcome: LockScreenOutcome? = null
)

class LockScreenController(
    packageName: String,
    appLabel: String,
    private val pinRepository: PinRepository,
    private val appLimitRepository: AppLimitRepository,
    private val dailyResetCoordinator: DailyResetCoordinator
) {
    private val _uiState = MutableStateFlow(
        LockScreenUiState(
            packageName = packageName,
            appLabel = appLabel,
            stage = if (pinRepository.isPinConfigured()) {
                LockScreenStage.PIN_ENTRY
            } else {
                LockScreenStage.PIN_NOT_CONFIGURED
            }
        )
    )
    val uiState: StateFlow<LockScreenUiState> = _uiState.asStateFlow()

    fun onDigitPressed(digit: Char) {
        if (digit !in '0'..'9') return
        _uiState.update { state ->
            if (state.stage != LockScreenStage.PIN_ENTRY || state.isProcessing) return@update state
            state.copy(
                pinInput = state.pinInput + digit,
                pinError = null
            )
        }
    }

    fun onBackspacePressed() {
        _uiState.update { state ->
            if (state.stage != LockScreenStage.PIN_ENTRY || state.isProcessing || state.pinInput.isEmpty()) {
                return@update state
            }
            state.copy(
                pinInput = state.pinInput.dropLast(1),
                pinError = null
            )
        }
    }

    fun onClearPin() {
        _uiState.update { state ->
            if (state.stage != LockScreenStage.PIN_ENTRY || state.isProcessing) return@update state
            state.copy(pinInput = "", pinError = null)
        }
    }

    fun submitPin(): Boolean {
        val current = _uiState.value
        if (current.stage != LockScreenStage.PIN_ENTRY || current.isProcessing) return false
        if (current.pinInput.isEmpty()) {
            _uiState.update { it.copy(pinError = "Introduce el PIN") }
            return false
        }

        val isValid = pinRepository.verifyPin(current.pinInput)
        return if (isValid) {
            _uiState.update {
                it.copy(
                    stage = LockScreenStage.UNLOCKED_OPTIONS,
                    pinInput = "",
                    pinError = null
                )
            }
            true
        } else {
            _uiState.update {
                it.copy(
                    pinInput = "",
                    pinError = "PIN incorrecto"
                )
            }
            false
        }
    }

    fun onCustomMinutesChanged(input: String) {
        val digitsOnly = input.filter { it in '0'..'9' }.take(4)
        _uiState.update { state ->
            state.copy(
                customMinutesInput = digitsOnly,
                customMinutesError = null
            )
        }
    }

    suspend fun grantExtraMinutes(minutes: Int): Boolean {
        val current = _uiState.value
        if (current.stage != LockScreenStage.UNLOCKED_OPTIONS || current.isProcessing) {
            return false
        }
        if (minutes <= 0) {
            _uiState.update { it.copy(customMinutesError = "Ingresa una cantidad mayor a 0 minutos") }
            return false
        }

        _uiState.update { it.copy(isProcessing = true, customMinutesError = null) }
        return try {
            val dayId = currentDayId()
            appLimitRepository.addExtraMinutes(
                packageName = current.packageName,
                extraMinutes = minutes,
                dayId = dayId
            )
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    outcome = LockScreenOutcome.UnlockedReturnToApp
                )
            }
            true
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    customMinutesError = "No se pudo añadir el tiempo extra"
                )
            }
            false
        }
    }

    suspend fun grantCustomExtraMinutes(): Boolean {
        val parsed = _uiState.value.customMinutesInput.toIntOrNull()
        if (parsed == null || parsed <= 0) {
            _uiState.update { it.copy(customMinutesError = "Ingresa una cantidad mayor a 0 minutos") }
            return false
        }
        return grantExtraMinutes(parsed)
    }

    suspend fun ignoreForToday(): Boolean {
        val current = _uiState.value
        if (current.stage != LockScreenStage.UNLOCKED_OPTIONS || current.isProcessing) {
            return false
        }

        _uiState.update { it.copy(isProcessing = true) }
        return try {
            val dayId = currentDayId()
            appLimitRepository.ignoreForToday(
                packageName = current.packageName,
                dayId = dayId
            )
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    outcome = LockScreenOutcome.UnlockedReturnToApp
                )
            }
            true
        } catch (_: Exception) {
            _uiState.update { it.copy(isProcessing = false) }
            false
        }
    }

    fun onExitRequested() {
        _uiState.update { it.copy(outcome = LockScreenOutcome.ExitToLauncher) }
    }

    private suspend fun currentDayId(): Long =
        when (val result = dailyResetCoordinator.checkAndPerformDailyReset()) {
            is DailyResetResult.ResetPerformed -> result.newDayId
            is DailyResetResult.NoResetNeeded -> result.currentDayId
        }

    companion object {
        val PRESET_EXTRA_MINUTES: List<Int> = listOf(5, 15, 30)
    }
}
