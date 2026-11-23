package com.lograre.app.ui.lock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lograre.app.LograreApplication
import com.lograre.app.ui.components.PinKeypad
import com.lograre.app.ui.theme.LograreTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Full-screen overlay activity shown over a blocked app when its daily limit is reached.
 *
 * If the user cancels, presses Back, or leaves without unlocking, they are sent directly
 * to the device launcher ([Intent.ACTION_MAIN] + [Intent.CATEGORY_HOME]).
 */
class LockScreenActivity : ComponentActivity() {

    private var blockedPackageState by mutableStateOf<String?>(null)
    private var unlockedAndReturning: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val initialPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)?.takeIf { it.isNotBlank() }
        if (initialPackage == null) {
            goToLauncherAndFinish()
            return
        }
        blockedPackageState = initialPackage

        val container = (application as LograreApplication).container

        setContent {
            LograreTheme {
                val activePackage = blockedPackageState
                if (activePackage == null) {
                    LaunchedEffect(Unit) { goToLauncherAndFinish() }
                    return@LograreTheme
                }

                val appLabel = remember(activePackage) { resolveAppLabel(activePackage) }
                val controller = remember(activePackage) {
                    LockScreenController(
                        packageName = activePackage,
                        appLabel = appLabel,
                        pinRepository = container.pinRepository,
                        appLimitRepository = container.appLimitRepository,
                        dailyResetCoordinator = container.dailyResetCoordinator
                    )
                }
                val uiState by controller.uiState.collectAsState()
                val scope = rememberCoroutineScope()

                BackHandler {
                    controller.onExitRequested()
                }

                LaunchedEffect(uiState.outcome) {
                    when (uiState.outcome) {
                        LockScreenOutcome.UnlockedReturnToApp -> {
                            unlockedAndReturning = true
                            finish()
                        }
                        LockScreenOutcome.ExitToLauncher -> {
                            goToLauncherAndFinish()
                        }
                        null -> Unit
                    }
                }

                LockScreenContent(
                    uiState = uiState,
                    onDigitClick = controller::onDigitPressed,
                    onBackspaceClick = controller::onBackspacePressed,
                    onClearClick = controller::onClearPin,
                    onSubmitPin = {
                        scope.launch(Dispatchers.Default) {
                            controller.submitPin()
                        }
                    },
                    onPresetExtraMinutes = { minutes ->
                        scope.launch(Dispatchers.IO) {
                            controller.grantExtraMinutes(minutes)
                        }
                    },
                    onCustomMinutesChanged = controller::onCustomMinutesChanged,
                    onSubmitCustomMinutes = {
                        scope.launch(Dispatchers.IO) {
                            controller.grantCustomExtraMinutes()
                        }
                    },
                    onIgnoreToday = {
                        scope.launch(Dispatchers.IO) {
                            controller.ignoreForToday()
                        }
                    },
                    onExitToLauncher = controller::onExitRequested
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)?.takeIf { it.isNotBlank() }
        if (newPackage != null) {
            unlockedAndReturning = false
            blockedPackageState = newPackage
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!unlockedAndReturning) {
            finish()
        }
    }

    private fun resolveAppLabel(packageName: String): String = try {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    } catch (_: Exception) {
        packageName
    }

    private fun goToLauncherAndFinish() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "com.lograre.app.EXTRA_BLOCKED_PACKAGE"

        fun createIntent(context: Context, packageName: String): Intent =
            Intent(context, LockScreenActivity::class.java).apply {
                putExtra(EXTRA_BLOCKED_PACKAGE, packageName)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
    }
}

@Composable
private fun LockScreenContent(
    uiState: LockScreenUiState,
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit,
    onSubmitPin: () -> Unit,
    onPresetExtraMinutes: (Int) -> Unit,
    onCustomMinutesChanged: (String) -> Unit,
    onSubmitCustomMinutes: () -> Unit,
    onIgnoreToday: () -> Unit,
    onExitToLauncher: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Tiempo agotado",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = uiState.appLabel,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            when (uiState.stage) {
                LockScreenStage.PIN_ENTRY -> {
                    Text(
                        text = "Esta aplicación alcanzó su límite diario. Introduce el PIN para continuar o sal al inicio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    PinKeypad(
                        pinLength = uiState.pinInput.length,
                        errorMessage = uiState.pinError,
                        confirmLabel = "Desbloquear",
                        enabled = !uiState.isProcessing,
                        onDigitClick = onDigitClick,
                        onBackspaceClick = onBackspaceClick,
                        onClearClick = onClearClick,
                        onConfirmClick = onSubmitPin
                    )

                    TextButton(onClick = onExitToLauncher) {
                        Text("Salir al inicio")
                    }
                }

                LockScreenStage.UNLOCKED_OPTIONS -> {
                    Text(
                        text = "PIN verificado. Elige cómo continuar con ${uiState.appLabel}:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Añadir más tiempo",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LockScreenController.PRESET_EXTRA_MINUTES.forEach { minutes ->
                            FilledTonalButton(
                                onClick = { onPresetExtraMinutes(minutes) },
                                enabled = !uiState.isProcessing,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+$minutes min")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.customMinutesInput,
                            onValueChange = onCustomMinutesChanged,
                            label = { Text("Minutos personalizados") },
                            singleLine = true,
                            enabled = !uiState.isProcessing,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = uiState.customMinutesError != null,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = onSubmitCustomMinutes,
                            enabled = !uiState.isProcessing && uiState.customMinutesInput.isNotEmpty()
                        ) {
                            Text("Añadir")
                        }
                    }

                    if (uiState.customMinutesError != null) {
                        Text(
                            text = uiState.customMinutesError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    OutlinedButton(
                        onClick = onIgnoreToday,
                        enabled = !uiState.isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Ignorar el bloqueo todo el día")
                    }

                    TextButton(
                        onClick = onExitToLauncher,
                        enabled = !uiState.isProcessing
                    ) {
                        Text("Cancelar y salir al inicio")
                    }
                }

                LockScreenStage.PIN_NOT_CONFIGURED -> {
                    Text(
                        text = "Aún no hay un PIN configurado en lograre. Abre la app para configurarlo.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = onExitToLauncher) {
                        Text("Volver al inicio")
                    }
                }
            }
        }
    }
}
