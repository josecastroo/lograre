package com.lograre.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Reusable numeric PIN entry pad with hidden indicator dots and variable-length support.
 */
@Composable
fun PinKeypad(
    pinLength: Int,
    errorMessage: String?,
    confirmLabel: String,
    enabled: Boolean = true,
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit,
    onConfirmClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val masked = if (pinLength == 0) {
            "Introduce tu PIN"
        } else {
            "• ".repeat(pinLength).trim()
        }

        Text(
            text = masked,
            style = if (pinLength == 0) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.headlineMedium
            },
            color = if (pinLength == 0) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        } else {
            Spacer(modifier = Modifier.height(20.dp))
        }

        val rows = listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9')
        )

        rows.forEach { rowDigits ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowDigits.forEach { digit ->
                    FilledTonalButton(
                        onClick = { onDigitClick(digit) },
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Text(
                            text = digit.toString(),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onClearClick,
                enabled = enabled && pinLength > 0,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text("Limpiar")
            }

            FilledTonalButton(
                onClick = { onDigitClick('0') },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            OutlinedButton(
                onClick = onBackspaceClick,
                enabled = enabled && pinLength > 0,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text("⌫", style = MaterialTheme.typography.titleLarge)
            }
        }

        Button(
            onClick = onConfirmClick,
            enabled = enabled && pinLength > 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(confirmLabel)
        }
    }
}
