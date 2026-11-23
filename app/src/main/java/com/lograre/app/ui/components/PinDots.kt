package com.lograre.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lograre.app.R
import com.lograre.app.ui.theme.AppTheme
import com.lograre.app.ui.theme.LocalZenPalette
import com.lograre.app.ui.theme.ThemeMode
import com.lograre.app.ui.theme.ZenDimens

/**
 * Water-drop PIN indicator dots supporting variable-length numeric PINs.
 *
 * - 14 dp dots with 16 dp spacing.
 * - Empty: 1.5 dp border in `accent`.
 * - Filled: solid `accent` with a 250 ms droplet pop (1.0 → 1.25 → 1.0).
 * - On wrong PIN ([errorTrigger] incremented): oscillates horizontally ±10 dp (3 cycles, 350 ms).
 */
@Composable
fun PinDots(
    enteredLength: Int,
    modifier: Modifier = Modifier,
    minVisibleDots: Int = DEFAULT_MIN_VISIBLE_DOTS,
    errorTrigger: Int = 0
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val dotCount = maxOf(minVisibleDots, enteredLength)
    val shakeOffsetPx = remember { Animatable(0f) }
    val amplitudePx = with(LocalDensity.current) { SHAKE_AMPLITUDE_DP.dp.toPx() }

    LaunchedEffect(errorTrigger) {
        if (errorTrigger <= 0 || !animationsEnabled) return@LaunchedEffect
        val stepDuration = SHAKE_TOTAL_DURATION_MS / 7
        val targets = listOf(
            amplitudePx,
            -amplitudePx,
            amplitudePx,
            -amplitudePx,
            amplitudePx * 0.6f,
            -amplitudePx * 0.6f,
            0f
        )
        for (target in targets) {
            shakeOffsetPx.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = stepDuration, easing = FastOutSlowInEasing)
            )
        }
    }

    val cdText = stringResource(R.string.pin_dots_cd, enteredLength)

    Row(
        modifier = modifier
            .graphicsLayer { translationX = shakeOffsetPx.value }
            .semantics { contentDescription = cdText },
        horizontalArrangement = Arrangement.spacedBy(ZenDimens.PinDotSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (index in 0 until dotCount) {
            PinSingleDot(
                filled = index < enteredLength,
                animationsEnabled = animationsEnabled
            )
        }
    }
}

@Composable
private fun PinSingleDot(
    filled: Boolean,
    animationsEnabled: Boolean
) {
    val palette = LocalZenPalette.current
    val scale = remember { Animatable(1f) }

    LaunchedEffect(filled) {
        if (filled && animationsEnabled) {
            scale.snapTo(1f)
            scale.animateTo(
                targetValue = DROPLET_PEAK_SCALE,
                animationSpec = tween(durationMillis = DROPLET_HALF_MS, easing = FastOutSlowInEasing)
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = DROPLET_HALF_MS, easing = FastOutSlowInEasing)
            )
        } else {
            scale.snapTo(1f)
        }
    }

    Box(
        modifier = Modifier
            .size(ZenDimens.PinDotSize)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .background(
                color = if (filled) palette.accent else Color.Transparent,
                shape = CircleShape
            )
            .border(
                width = ZenDimens.PinDotBorderWidth,
                color = palette.accent,
                shape = CircleShape
            )
    )
}

private const val DEFAULT_MIN_VISIBLE_DOTS = 4
private const val DROPLET_PEAK_SCALE = 1.25f
private const val DROPLET_HALF_MS = 125
private const val SHAKE_AMPLITUDE_DP = 10
private const val SHAKE_TOTAL_DURATION_MS = 350

@Preview(name = "PinDots — Claro", showBackground = true, widthDp = 280, heightDp = 80)
@Composable
private fun PinDotsLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ZenBackground {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(ZenDimens.Space16)
            ) {
                PinDots(enteredLength = 3)
            }
        }
    }
}

@Preview(name = "PinDots — Oscuro", showBackground = true, widthDp = 280, heightDp = 80)
@Composable
private fun PinDotsDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ZenBackground {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(ZenDimens.Space16)
            ) {
                PinDots(enteredLength = 5)
            }
        }
    }
}
