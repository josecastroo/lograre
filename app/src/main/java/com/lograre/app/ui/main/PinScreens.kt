package com.lograre.app.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.lograre.app.R
import com.lograre.app.ui.components.PinKeypad
import com.lograre.app.ui.components.ZenBackground
import com.lograre.app.ui.components.ZenTextButton
import com.lograre.app.ui.theme.AppTheme
import com.lograre.app.ui.theme.LocalZenPalette
import com.lograre.app.ui.theme.LocalZenTypography
import com.lograre.app.ui.theme.ThemeMode
import com.lograre.app.ui.theme.ZenDimens

@Composable
fun InitialPinSetupScreen(
    uiState: MainUiState,
    pinErrorTrigger: Int = 0,
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit = {},
    onConfirmClick: () -> Unit,
    onRestartClick: () -> Unit
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current

    val title = when (uiState.pinSetupStep) {
        PinSetupStep.ENTER_NEW_PIN -> stringResource(R.string.pin_setup_title)
        PinSetupStep.CONFIRM_NEW_PIN -> stringResource(R.string.pin_setup_confirm)
    }
    val subtitle = when (uiState.pinSetupStep) {
        PinSetupStep.ENTER_NEW_PIN -> stringResource(R.string.pin_setup_subtitle)
        PinSetupStep.CONFIRM_NEW_PIN -> stringResource(R.string.pin_setup_subtitle)
    }
    val confirmLabel = when (uiState.pinSetupStep) {
        PinSetupStep.ENTER_NEW_PIN -> stringResource(R.string.pin_setup_next)
        PinSetupStep.CONFIRM_NEW_PIN -> stringResource(R.string.pin_setup_save)
    }

    ZenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = ZenDimens.ScreenHorizontalPadding,
                    vertical = ZenDimens.Space32
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZenDimens.Space16)
        ) {
            Spacer(modifier = Modifier.height(ZenDimens.Space16))

            Text(
                text = title,
                style = typo.title,
                color = palette.text,
                textAlign = TextAlign.Center
            )

            Text(
                text = subtitle,
                style = typo.body,
                color = palette.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(ZenDimens.Space8))

            PinKeypad(
                pinLength = uiState.pinInput.length,
                errorMessage = uiState.pinError,
                confirmLabel = confirmLabel,
                enabled = !uiState.isProcessing,
                errorTrigger = pinErrorTrigger,
                onDigitClick = onDigitClick,
                onBackspaceClick = onBackspaceClick,
                onClearClick = onClearClick,
                onConfirmClick = onConfirmClick
            )

            if (uiState.pinSetupStep == PinSetupStep.CONFIRM_NEW_PIN) {
                ZenTextButton(
                    text = stringResource(R.string.pin_setup_restart),
                    onClick = onRestartClick,
                    color = palette.textSecondary
                )
            }
        }
    }
}

@Composable
fun ChangePinScreen(
    uiState: MainUiState,
    pinErrorTrigger: Int = 0,
    onBackClick: () -> Unit,
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit = {},
    onConfirmClick: () -> Unit
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current

    val (stepSubtitle, confirmLabel) = when (uiState.changePinStep) {
        ChangePinStep.VERIFY_CURRENT_PIN ->
            stringResource(R.string.change_pin_step_current) to stringResource(R.string.change_pin_verify)
        ChangePinStep.ENTER_NEW_PIN ->
            stringResource(R.string.change_pin_step_new) to stringResource(R.string.pin_setup_next)
        ChangePinStep.CONFIRM_NEW_PIN ->
            stringResource(R.string.change_pin_step_confirm) to stringResource(R.string.change_pin_save)
    }

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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZenDimens.Space16)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                ZenTextButton(
                    text = stringResource(R.string.action_back),
                    onClick = onBackClick,
                    enabled = !uiState.isProcessing,
                    color = palette.textSecondary,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            Text(
                text = stringResource(R.string.change_pin_title),
                style = typo.title,
                color = palette.text,
                textAlign = TextAlign.Center
            )

            Text(
                text = stepSubtitle,
                style = typo.body,
                color = palette.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(ZenDimens.Space8))

            PinKeypad(
                pinLength = uiState.pinInput.length,
                errorMessage = uiState.pinError,
                confirmLabel = confirmLabel,
                enabled = !uiState.isProcessing,
                errorTrigger = pinErrorTrigger,
                onDigitClick = onDigitClick,
                onBackspaceClick = onBackspaceClick,
                onClearClick = onClearClick,
                onConfirmClick = onConfirmClick
            )
        }
    }
}

@Composable
fun PinConfirmationScreen(
    action: PendingProtectedAction,
    pinLength: Int,
    pinError: String?,
    pinErrorTrigger: Int = 0,
    isProcessing: Boolean,
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit = {},
    onConfirmClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current
    val calmError = if (pinError != null) stringResource(R.string.pin_incorrect) else null

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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZenDimens.Space16)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                ZenTextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onCancelClick,
                    enabled = !isProcessing,
                    color = palette.textSecondary,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            Text(
                text = action.title,
                style = typo.title,
                color = palette.text,
                textAlign = TextAlign.Center
            )

            Text(
                text = action.description,
                style = typo.body,
                color = palette.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(ZenDimens.Space8))

            PinKeypad(
                pinLength = pinLength,
                errorMessage = calmError,
                confirmLabel = stringResource(R.string.pin_confirm_change),
                enabled = !isProcessing,
                errorTrigger = pinErrorTrigger,
                onDigitClick = onDigitClick,
                onBackspaceClick = onBackspaceClick,
                onClearClick = onClearClick,
                onConfirmClick = onConfirmClick
            )
        }
    }
}

@Preview(name = "PIN Inicial — Claro", showBackground = true, widthDp = 380, heightDp = 760)
@Composable
private fun InitialPinSetupLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        InitialPinSetupScreen(
            uiState = MainUiState(
                destination = MainDestination.INITIAL_PIN_SETUP,
                pinInput = "12"
            ),
            onDigitClick = {},
            onBackspaceClick = {},
            onConfirmClick = {},
            onRestartClick = {}
        )
    }
}

@Preview(name = "PIN Inicial — Oscuro", showBackground = true, widthDp = 380, heightDp = 760)
@Composable
private fun InitialPinSetupDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        InitialPinSetupScreen(
            uiState = MainUiState(
                destination = MainDestination.INITIAL_PIN_SETUP,
                pinSetupStep = PinSetupStep.CONFIRM_NEW_PIN,
                pinInput = "123"
            ),
            onDigitClick = {},
            onBackspaceClick = {},
            onConfirmClick = {},
            onRestartClick = {}
        )
    }
}
