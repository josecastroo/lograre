package com.lograre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lograre.app.R
import com.lograre.app.ui.theme.AppTheme
import com.lograre.app.ui.theme.LocalZenPalette
import com.lograre.app.ui.theme.LocalZenTypography
import com.lograre.app.ui.theme.ThemeMode
import com.lograre.app.ui.theme.ZenDimens

/**
 * Calm 3 × 4 circular numeric PIN keypad for "Lago y bosque".
 *
 * - 72 dp circular translucent glass keys spaced by 16 dp, using Keypad typography.
 * - Bottom-left empty; bottom-right backspace icon.
 * - Every press emits [waterRipple] from exact touch point + gentle [ZenHaptics.keyPress].
 * - Includes [PinDots] (optional via [showDots]) and a primary pill confirmation button
 *   to support variable-length PINs cleanly.
 */
@Composable
fun PinKeypad(
    pinLength: Int,
    errorMessage: String?,
    confirmLabel: String,
    enabled: Boolean = true,
    errorTrigger: Int = 0,
    showDots: Boolean = true,
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit = {},
    onConfirmClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current
    val view = LocalView.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZenDimens.Space16)
    ) {
        if (showDots) {
            PinDots(
                enteredLength = pinLength,
                errorTrigger = errorTrigger
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = typo.body,
                color = palette.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Spacer(modifier = Modifier.height(ZenDimens.Space4))
        }

        val digitRows = listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9')
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZenDimens.KeypadSpacing)
        ) {
            digitRows.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ZenDimens.KeypadSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { digit ->
                        KeypadDigitCircle(
                            digit = digit,
                            enabled = enabled,
                            onClick = {
                                ZenHaptics.keyPress(view)
                                onDigitClick(digit)
                            }
                        )
                    }
                }
            }

            // Bottom row: empty left, 0 center, backspace icon right
            Row(
                horizontalArrangement = Arrangement.spacedBy(ZenDimens.KeypadSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(ZenDimens.KeypadButtonSize))

                KeypadDigitCircle(
                    digit = '0',
                    enabled = enabled,
                    onClick = {
                        ZenHaptics.keyPress(view)
                        onDigitClick('0')
                    }
                )

                val backspaceCd = stringResource(R.string.pin_backspace_cd)
                val backspaceEnabled = enabled && pinLength > 0
                Box(
                    modifier = Modifier
                        .size(ZenDimens.KeypadButtonSize)
                        .alpha(if (backspaceEnabled) 1f else 0.35f)
                        .clip(CircleShape)
                        .background(palette.surface, CircleShape)
                        .border(ZenDimens.BorderWidth, palette.border, CircleShape)
                        .semantics { contentDescription = backspaceCd }
                        .waterRipple(
                            enabled = backspaceEnabled,
                            onClickLabel = backspaceCd,
                            role = Role.Button,
                            onClick = {
                                ZenHaptics.keyPress(view)
                                onBackspaceClick()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_zen_backspace),
                        contentDescription = null,
                        tint = palette.text,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(ZenDimens.Space4))

        ZenButton(
            text = confirmLabel,
            onClick = {
                ZenHaptics.keyPress(view)
                onConfirmClick()
            },
            style = ZenButtonStyle.PRIMARY,
            enabled = enabled && pinLength > 0
        )
    }
}

@Composable
private fun KeypadDigitCircle(
    digit: Char,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current
    val digitCd = stringResource(R.string.pin_digit_cd, digit.toString())

    Box(
        modifier = Modifier
            .size(ZenDimens.KeypadButtonSize)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .background(palette.surface, CircleShape)
            .border(ZenDimens.BorderWidth, palette.border, CircleShape)
            .semantics { contentDescription = digitCd }
            .waterRipple(
                enabled = enabled,
                onClickLabel = digitCd,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit.toString(),
            style = typo.keypad,
            color = palette.text
        )
    }
}

@Preview(name = "PinKeypad — Claro", showBackground = true, widthDp = 360, heightDp = 520)
@Composable
private fun PinKeypadLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ZenBackground {
            PinKeypad(
                pinLength = 2,
                errorMessage = null,
                confirmLabel = "Continuar",
                onDigitClick = {},
                onBackspaceClick = {},
                onConfirmClick = {},
                modifier = Modifier.padding(ZenDimens.ScreenHorizontalPadding)
            )
        }
    }
}

@Preview(name = "PinKeypad — Oscuro", showBackground = true, widthDp = 360, heightDp = 520)
@Composable
private fun PinKeypadDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ZenBackground {
            PinKeypad(
                pinLength = 4,
                errorMessage = "Ese no es el código. Respira e inténtalo de nuevo.",
                confirmLabel = "Continuar",
                onDigitClick = {},
                onBackspaceClick = {},
                onConfirmClick = {},
                modifier = Modifier.padding(ZenDimens.ScreenHorizontalPadding)
            )
        }
    }
}
