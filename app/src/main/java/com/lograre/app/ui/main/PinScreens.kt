package com.lograre.app.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lograre.app.ui.components.PinKeypad

@Composable
fun InitialPinSetupScreen(
    uiState: MainUiState,
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit,
    onConfirmClick: () -> Unit,
    onRestartClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Bienvenido a lograre",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            val subtitle = when (uiState.pinSetupStep) {
                PinSetupStep.ENTER_NEW_PIN ->
                    "Paso 1 de 2: elige un PIN numérico (mínimo 4 dígitos). Se pedirá para desbloquear apps y modificar límites."
                PinSetupStep.CONFIRM_NEW_PIN ->
                    "Paso 2 de 2: vuelve a introducir el mismo PIN para confirmarlo."
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            PinKeypad(
                pinLength = uiState.pinInput.length,
                errorMessage = uiState.pinError,
                confirmLabel = if (uiState.pinSetupStep == PinSetupStep.ENTER_NEW_PIN) {
                    "Siguiente"
                } else {
                    "Guardar PIN"
                },
                enabled = !uiState.isProcessing,
                onDigitClick = onDigitClick,
                onBackspaceClick = onBackspaceClick,
                onClearClick = onClearClick,
                onConfirmClick = onConfirmClick
            )

            if (uiState.pinSetupStep == PinSetupStep.CONFIRM_NEW_PIN) {
                TextButton(onClick = onRestartClick) {
                    Text("Empezar de nuevo")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePinScreen(
    uiState: MainUiState,
    onBackClick: () -> Unit,
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit,
    onConfirmClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cambiar PIN") },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text("Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val (stepTitle, confirmLabel) = when (uiState.changePinStep) {
                ChangePinStep.VERIFY_CURRENT_PIN ->
                    "Paso 1 de 3: introduce tu PIN actual" to "Verificar"
                ChangePinStep.ENTER_NEW_PIN ->
                    "Paso 2 de 3: introduce el nuevo PIN (mínimo 4 dígitos)" to "Siguiente"
                ChangePinStep.CONFIRM_NEW_PIN ->
                    "Paso 3 de 3: confirma el nuevo PIN" to "Guardar nuevo PIN"
            }

            Text(
                text = stepTitle,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            PinKeypad(
                pinLength = uiState.pinInput.length,
                errorMessage = uiState.pinError,
                confirmLabel = confirmLabel,
                enabled = !uiState.isProcessing,
                onDigitClick = onDigitClick,
                onBackspaceClick = onBackspaceClick,
                onClearClick = onClearClick,
                onConfirmClick = onConfirmClick
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinConfirmationScreen(
    action: PendingProtectedAction,
    pinLength: Int,
    pinError: String?,
    isProcessing: Boolean,
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit,
    onConfirmClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(action.title) },
                navigationIcon = {
                    TextButton(onClick = onCancelClick, enabled = !isProcessing) {
                        Text("Cancelar")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = action.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            PinKeypad(
                pinLength = pinLength,
                errorMessage = pinError,
                confirmLabel = "Confirmar cambio",
                enabled = !isProcessing,
                onDigitClick = onDigitClick,
                onBackspaceClick = onBackspaceClick,
                onClearClick = onClearClick,
                onConfirmClick = onConfirmClick
            )
        }
    }
}
