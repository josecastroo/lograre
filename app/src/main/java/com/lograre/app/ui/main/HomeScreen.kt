package com.lograre.app.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lograre.app.domain.model.AppLimitStatus
import com.lograre.app.domain.model.TrackedApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: MainUiState,
    onAddAppClick: () -> Unit,
    onEditAppClick: (TrackedApp) -> Unit,
    onRemoveAppClick: (TrackedApp) -> Unit,
    onOpenPermissionsClick: () -> Unit,
    onOpenChangePinClick: () -> Unit,
    onDismissStatusMessage: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onDismissStatusMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("lograre") },
                actions = {
                    TextButton(onClick = onOpenPermissionsClick) {
                        Text(
                            if (uiState.permissionsStatus.accessibilityEnabled) {
                                "Permisos"
                            } else {
                                "Permisos (!)"
                            }
                        )
                    }
                    TextButton(onClick = onOpenChangePinClick) {
                        Text("PIN")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddAppClick
            ) {
                Text("+ Añadir app")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!uiState.permissionsStatus.accessibilityEnabled) {
                item(key = "accessibility_warning_banner") {
                    AccessibilityWarningBanner(onOpenPermissionsClick = onOpenPermissionsClick)
                }
            }

            if (uiState.trackedApps.isEmpty()) {
                item(key = "empty_state") {
                    EmptyTrackedAppsCard(onAddAppClick = onAddAppClick)
                }
            } else {
                items(
                    items = uiState.trackedApps,
                    key = { it.packageName }
                ) { app ->
                    val label = uiState.appLabelsByPackage[app.packageName] ?: app.packageName
                    TrackedAppCard(
                        app = app,
                        appLabel = label,
                        onEditClick = { onEditAppClick(app) },
                        onRemoveClick = { onRemoveAppClick(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessibilityWarningBanner(
    onOpenPermissionsClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Servicio de accesibilidad desactivado",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "Activa el servicio para que lograre pueda contar el tiempo de uso y bloquear apps al agotarse el límite.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(onClick = onOpenPermissionsClick) {
                Text("Abrir guía de permisos")
            }
        }
    }
}

@Composable
private fun EmptyTrackedAppsCard(
    onAddAppClick: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Aún no hay apps supervisadas",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Elige las aplicaciones que quieres limitar (Instagram, TikTok, YouTube...) y define su tiempo diario en minutos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onAddAppClick) {
                Text("Elegir primera app")
            }
        }
    }
}

@Composable
private fun TrackedAppCard(
    app: TrackedApp,
    appLabel: String,
    onEditClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconBadge(
                    packageName = app.packageName,
                    appLabel = appLabel
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appLabel,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val extraMinutes = (app.extraMillisToday / TrackedApp.MILLIS_PER_MINUTE).toInt()
                    val limitSummary = if (extraMinutes > 0) {
                        "Límite: ${DurationFormatter.formatMinutes(app.dailyLimitMinutes)} (+$extraMinutes min hoy)"
                    } else {
                        "Límite diario: ${DurationFormatter.formatMinutes(app.dailyLimitMinutes)}"
                    }
                    Text(
                        text = limitSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(status = app.status)
            }

            val progress = when {
                app.ignoredToday -> 0f
                app.effectiveLimitMillis <= 0L -> 1f
                else -> (app.usedMillisToday.toFloat() / app.effectiveLimitMillis.toFloat())
                    .coerceIn(0f, 1f)
            }

            val usageLine = when (app.status) {
                AppLimitStatus.IGNORED_TODAY ->
                    "Libre todo el día (se reinicia a las 00:00)"
                AppLimitStatus.BLOCKED ->
                    "Usado hoy: ${DurationFormatter.formatMillis(app.usedMillisToday)} · Límite agotado"
                AppLimitStatus.ACTIVE ->
                    "Usado hoy: ${DurationFormatter.formatMillis(app.usedMillisToday)} · Quedan ${DurationFormatter.formatMillis(app.remainingMillis)}"
            }

            Text(
                text = usageLine,
                style = MaterialTheme.typography.bodyMedium
            )

            if (!app.ignoredToday) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onEditClick) {
                    Text("Editar límite")
                }
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                TextButton(onClick = onRemoveClick) {
                    Text(
                        text = "Quitar",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: AppLimitStatus) {
    val (label, containerColor, contentColor) = when (status) {
        AppLimitStatus.ACTIVE -> Triple(
            "Activa",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        AppLimitStatus.BLOCKED -> Triple(
            "Bloqueada",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        AppLimitStatus.IGNORED_TODAY -> Triple(
            "Ignorada hoy",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun LimitEditorDialog(
    state: LimitEditorDialogState,
    onMinutesChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (state.isNewApp) {
                    "Añadir ${state.appLabel}"
                } else {
                    "Editar límite de ${state.appLabel}"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Define el límite diario en minutos. Puedes poner 0 para pedir PIN desde el primer intento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = state.minutesInput,
                    onValueChange = onMinutesChanged,
                    label = { Text("Minutos diarios (>= 0)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.error != null) {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Continuar con PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
