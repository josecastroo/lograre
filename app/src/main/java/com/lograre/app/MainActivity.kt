package com.lograre.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lograre.app.ui.theme.LograreTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LograreApplication).container
        setContent {
            LograreTheme {
                val trackedApps by container.appLimitRepository
                    .observeAllApps()
                    .collectAsState(initial = emptyList())
                val isPinConfigured = container.pinRepository.isPinConfigured()

                BootstrapStatusScreen(
                    isPinConfigured = isPinConfigured,
                    trackedAppsCount = trackedApps.size
                )
            }
        }
    }
}

@Composable
private fun BootstrapStatusScreen(
    isPinConfigured: Boolean,
    trackedAppsCount: Int
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
                    "PIN pendiente de configuración (se implementará en la pantalla inicial)."
                },
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Apps supervisadas: $trackedAppsCount",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
