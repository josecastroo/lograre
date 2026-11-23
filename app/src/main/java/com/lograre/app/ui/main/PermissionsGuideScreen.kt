package com.lograre.app.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lograre.app.service.AppPermissionsStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsGuideScreen(
    status: AppPermissionsStatus,
    onBackClick: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestNotificationsPermission: () -> Unit,
    onRequestBatteryExemption: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guía de permisos") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Configura estos permisos para que lograre funcione de forma fiable en segundo plano.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PermissionCard(
                title = "1. Servicio de accesibilidad (Obligatorio)",
                description = "Permite detectar qué aplicación está en primer plano para contar el tiempo diario y mostrar la pantalla de PIN al agotarse. Busca \"lograre - Control de tiempo\" y actívalo.",
                isGranted = status.accessibilityEnabled,
                actionLabel = if (status.accessibilityEnabled) "Revisar en Ajustes" else "Activar accesibilidad",
                isPrimaryAction = !status.accessibilityEnabled,
                onActionClick = onOpenAccessibilitySettings
            )

            PermissionCard(
                title = "2. Notificaciones (Android 13+)",
                description = "Necesario para mostrarte el aviso cuando falten 5 minutos para agotar el límite diario de una aplicación.",
                isGranted = status.notificationsGranted,
                actionLabel = if (status.notificationsGranted) "Ajustes de notificaciones" else "Permitir notificaciones",
                isPrimaryAction = !status.notificationsGranted,
                onActionClick = onRequestNotificationsPermission
            )

            PermissionCard(
                title = "3. Exclusión de optimización de batería (Recomendada)",
                description = "Evita que Android cierre o suspenda el servicio de conteo en segundo plano cuando ahorra batería.",
                isGranted = status.batteryOptimizationIgnored,
                actionLabel = if (status.batteryOptimizationIgnored) "Revisar batería" else "Excluir de optimización",
                isPrimaryAction = !status.batteryOptimizationIgnored,
                onActionClick = onRequestBatteryExemption
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    isPrimaryAction: Boolean,
    onActionClick: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = if (isGranted) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (isGranted) "Activo" else "Pendiente",
                        color = if (isGranted) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isPrimaryAction) {
                Button(onClick = onActionClick) {
                    Text(actionLabel)
                }
            } else {
                OutlinedButton(onClick = onActionClick) {
                    Text(actionLabel)
                }
            }
        }
    }
}
