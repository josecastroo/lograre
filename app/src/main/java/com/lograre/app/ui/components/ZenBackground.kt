package com.lograre.app.ui.components

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lograre.app.ui.theme.AppTheme
import com.lograre.app.ui.theme.LocalZenPalette
import com.lograre.app.ui.theme.ThemeMode

/**
 * Ambient living background for "Lago y bosque".
 *
 * - Vertical gradient from `bgTop` to `bgBottom` (slightly deeper when [dimmedForFocus] is true).
 * - Three large soft radial-gradient orbs (340–410 dp) drifting ±40 dp on desynchronized
 *   21s / 25s / 29s reversible sine cycles.
 * - Reads animated values strictly inside [Canvas] DrawScope (zero recomposition per frame)
 *   and pauses automatically when animations are disabled or the lifecycle is not RESUMED.
 */
@Composable
fun ZenBackground(
    modifier: Modifier = Modifier,
    dimmedForFocus: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val palette = LocalZenPalette.current
    val runContinuous = rememberShouldRunContinuousAnimations()

    val drift1: State<Float>
    val drift2: State<Float>
    val drift3: State<Float>

    if (runContinuous) {
        val transition = rememberInfiniteTransition(label = "zen_background_drift")
        drift1 = transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = BLOB_1_CYCLE_MS, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "blob1"
        )
        drift2 = transition.animateFloat(
            initialValue = 1f,
            targetValue = -1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = BLOB_2_CYCLE_MS, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "blob2"
        )
        drift3 = transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = BLOB_3_CYCLE_MS, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "blob3"
        )
    } else {
        drift1 = rememberUpdatedState(0f)
        drift2 = rememberUpdatedState(0f)
        drift3 = rememberUpdatedState(0f)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(palette.bgTop, palette.bgBottom)
                )
            )

            val maxDriftPx = MAX_DRIFT_DP.dp.toPx()

            val radius1 = BLOB_1_DIAMETER_DP.dp.toPx() / 2f
            val center1 = Offset(
                x = size.width * 0.18f + drift1.value * maxDriftPx,
                y = size.height * 0.16f + drift1.value * (maxDriftPx * 0.65f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.accent.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = center1,
                    radius = radius1
                ),
                radius = radius1,
                center = center1
            )

            val radius2 = BLOB_2_DIAMETER_DP.dp.toPx() / 2f
            val center2 = Offset(
                x = size.width * 0.85f + drift2.value * maxDriftPx,
                y = size.height * 0.78f - drift2.value * (maxDriftPx * 0.75f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.accent2.copy(alpha = 0.30f),
                        Color.Transparent
                    ),
                    center = center2,
                    radius = radius2
                ),
                radius = radius2,
                center = center2
            )

            val radius3 = BLOB_3_DIAMETER_DP.dp.toPx() / 2f
            val center3 = Offset(
                x = size.width * 0.72f - drift3.value * (maxDriftPx * 0.7f),
                y = size.height * 0.36f + drift3.value * maxDriftPx
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = palette.backgroundBlobWhiteAlpha),
                        Color.Transparent
                    ),
                    center = center3,
                    radius = radius3
                ),
                radius = radius3,
                center = center3
            )

            if (dimmedForFocus) {
                val scrimAlpha = if (palette.isDark) 0.24f else 0.14f
                drawRect(color = Color(0xFF0A1916).copy(alpha = scrimAlpha))
            }
        }

        content()
    }
}

private const val MAX_DRIFT_DP = 40
private const val BLOB_1_DIAMETER_DP = 380
private const val BLOB_2_DIAMETER_DP = 410
private const val BLOB_3_DIAMETER_DP = 340
private const val BLOB_1_CYCLE_MS = 21_000
private const val BLOB_2_CYCLE_MS = 25_000
private const val BLOB_3_CYCLE_MS = 29_000

@Preview(name = "ZenBackground — Claro", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ZenBackgroundLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ZenBackground()
    }
}

@Preview(name = "ZenBackground — Oscuro", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ZenBackgroundDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ZenBackground()
    }
}
