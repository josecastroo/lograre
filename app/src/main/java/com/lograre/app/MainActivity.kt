package com.lograre.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lograre.app.domain.clock.DailyResetResult
import com.lograre.app.domain.clock.TrustedTimeSnapshot
import com.lograre.app.service.AccessibilityServiceState
import com.lograre.app.ui.theme.LograreTheme
import java.time.Instant
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LograreApplication).container
        setContent {
            LograreTheme {
                val trackedApps by container.appLimitRepository
                    .observeAllApps()
                    .collectAsState(initial = emptyList())
                val isAccessibilityRunning by AccessibilityServiceState.isRunning.collectAsState()
                val isPinConfigured = container.pinRepository.isPinConfigured()
                var clockSnapshot by remember { mutableStateOf<TrustedTimeSnapshot?>(null) }

                LaunchedEffect(Unit) {
                    val result = container.dailyResetCoordinator.checkAndPerformDailyReset(
                        attemptNtpSync = true
                    )
                    clockSnapshot = when (result) {
                        is DailyResetResult.ResetPerformed -> result.snapshot
                        is DailyResetResult.NoResetNeeded -> result.snapshot
                    }
                }

                BootstrapStatusScreen(
                    isPinConfigured = isPinConfigured,
                    trackedAppsCount = trackedApps.size,
                    isAccessibilityActive = isAccessibilityRunning ||
                        AccessibilityServiceState.isEnabledInSettings(this),
                    clockSnapshot = clockSnapshot,
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }
        }
    }
}

@Composable
private fun BootstrapStatusScreen(
    isPinConfigured: Boolean,
    trackedAppsCount: Int,
    isAccessibilityActive: Boolean,
    clockSnapshot: TrustedTimeSnapshot?,
    onOpenAccessibilitySettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "lograre",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = if (isPinConfigured) {
                    "PIN configurado correctamente."
                } else {
                    "PIN pendiente de configuración (Paso 5)."
                },
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Apps supervisadas: $trackedAppsCount",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = if (isAccessibilityActive) {
                    "Servicio de accesibilidad: Activo"
                } else {
                    "Servicio de accesibilidad: Inactivo"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (!isAccessibilityActive) {
                Button(onClick = onOpenAccessibilitySettings) {
                    Text("Activar servicio de accesibilidad")
                }
            }
            val trustedTimeText = clockSnapshot?.let { snapshot ->
                val localDateTime = Instant.ofEpochMilli(snapshot.utcMillis).atZone(snapshot.zoneId)
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").format(localDateTime)
            } ?: "Inicializando reloj de confianza..."
            Text(
                text = "Reloj de confianza: $trustedTimeText",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
