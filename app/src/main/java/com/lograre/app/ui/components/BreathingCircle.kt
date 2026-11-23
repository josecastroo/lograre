package com.lograre.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lograre.app.ui.theme.AppTheme
import com.lograre.app.ui.theme.LocalZenPalette
import com.lograre.app.ui.theme.ThemeMode
import com.lograre.app.ui.theme.ZenDimens
import kotlin.math.PI
import kotlin.math.sin

const val BREATH_INHALE_DURATION_MS: Int = 4_000
const val BREATH_EXHALE_DURATION_MS: Int = 6_000
const val BREATH_CYCLE_DURATION_MS: Long = (BREATH_INHALE_DURATION_MS + BREATH_EXHALE_DURATION_MS).toLong()

/**
 * Organic breathing circle with a clipped translucent rising water fill inside.
 *
 * - Base diameter 200 dp (shrinks smoothly to 120 dp in [compact] mode).
 * - Inhale 4 s (scale 1.0 → 1.12; 1.0 → 1.06 when compact), exhale 6 s back to 1.0.
 * - Outer radial halo (1.6x diameter, accent @ 35% → transparent) scales 1.0 → 1.25 in sync.
 * - Clipped inside the circle: translucent water (`accent → accent2`, medium opacity) rising
 *   from bottom to top according to [waterLevel] (`0f..1f`), with a gentle sine wave surface
 *   (amplitude 3 dp, 6 s cycle).
 * - When [completionRippleTrigger] increments (and animations are enabled), emits a soft
 *   expanding ring ripple from the circle edge.
 * - Inner [centerContent] stays stationary on top and never scales with the breathing pulse.
 * - When system animations are disabled: circle stays static, water still rises cleanly with a
 *   flat surface (no sine wave), and no completion ripple plays.
 */
@Composable
fun BreathingCircle(
    compact: Boolean,
    waterLevel: Float = 1f,
    completionRippleTrigger: Int = 0,
    modifier: Modifier = Modifier,
    centerContent: @Composable BoxScope.() -> Unit = {}
) {
    val palette = LocalZenPalette.current
    val animationsEnabled = rememberAnimationsEnabled()
    val runContinuous = rememberShouldRunContinuousAnimations()

    val baseDiameter by animateDpAsState(
        targetValue = if (compact) ZenDimens.BreathingCircleCompactSize else ZenDimens.BreathingCircleBaseSize,
        animationSpec = tween(
            durationMillis = if (animationsEnabled) 400 else 0,
            easing = FastOutSlowInEasing
        ),
        label = "breathing_diameter"
    )

    val breathProgress = remember { Animatable(0f) }

    LaunchedEffect(runContinuous) {
        if (!runContinuous) {
            breathProgress.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            breathProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = BREATH_INHALE_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
            breathProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = BREATH_EXHALE_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    val wavePhase: State<Float> = if (runContinuous) {
        val transition = rememberInfiniteTransition(label = "breathing_water_wave")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = WATER_WAVE_CYCLE_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "breathing_wave_phase"
        )
    } else {
        rememberUpdatedState(0f)
    }

    val completionWave = remember { Animatable(0f) }
    LaunchedEffect(completionRippleTrigger, animationsEnabled) {
        if (completionRippleTrigger <= 0 || !animationsEnabled) {
            completionWave.snapTo(0f)
            return@LaunchedEffect
        }
        completionWave.snapTo(0f)
        completionWave.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = COMPLETION_RIPPLE_DURATION_MS,
                easing = FastOutSlowInEasing
            )
        )
        completionWave.snapTo(0f)
    }

    val haloContainerSize = baseDiameter * HALO_MULTIPLIER

    Box(
        modifier = modifier.size(haloContainerSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val progress = breathProgress.value
            val circleScale = 1f + progress * (if (compact) COMPACT_SCALE_DELTA else FULL_SCALE_DELTA)
            val haloScale = 1f + progress * (if (compact) COMPACT_HALO_DELTA else FULL_HALO_DELTA)

            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = baseDiameter.toPx() / 2f
            val circleRadius = baseRadius * circleScale
            val haloRadius = (baseRadius * HALO_MULTIPLIER) * haloScale

            // 1. Soft outer halo synchronized with breathing
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.accent.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = center
            )

            // 2. Soft expanding completion wave when water reaches 100%
            val rippleProgress = completionWave.value
            if (rippleProgress > 0.001f && rippleProgress < 0.999f) {
                val rippleRadius = circleRadius * (1f + rippleProgress * COMPLETION_RIPPLE_EXTRA_SCALE)
                val rippleAlpha = (1f - rippleProgress) * 0.45f
                val rippleStrokeWidth = (3.dp.toPx() * (1f - rippleProgress * 0.6f)).coerceAtLeast(1.dp.toPx())
                drawCircle(
                    color = palette.rippleColor.copy(alpha = rippleAlpha),
                    radius = rippleRadius,
                    center = center,
                    style = Stroke(width = rippleStrokeWidth)
                )
            }

            // 3. Translucent base orb body
            val circleBounds = Rect(
                left = center.x - circleRadius,
                top = center.y - circleRadius,
                right = center.x + circleRadius,
                bottom = center.y + circleRadius
            )
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        palette.accent.copy(alpha = 0.22f),
                        palette.accent2.copy(alpha = 0.16f)
                    ),
                    start = circleBounds.topLeft,
                    end = circleBounds.bottomRight
                ),
                radius = circleRadius,
                center = center
            )

            // 4. Rising translucent water clipped strictly to the breathing circle shape
            val clampedFill = waterLevel.coerceIn(0f, 1f)
            if (clampedFill > 0.001f) {
                val clipCirclePath = Path().apply {
                    addOval(circleBounds)
                }
                clipPath(clipCirclePath) {
                    val diameterPx = circleRadius * 2f
                    val waterTopY = circleBounds.bottom - (diameterPx * clampedFill)
                    val waterBrush = Brush.verticalGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = 0.62f),
                            palette.accent2.copy(alpha = 0.56f)
                        ),
                        startY = circleBounds.top,
                        endY = circleBounds.bottom
                    )

                    val drawWaveSurface = runContinuous && clampedFill < 0.995f
                    if (!drawWaveSurface) {
                        drawRect(
                            brush = waterBrush,
                            topLeft = Offset(circleBounds.left, waterTopY),
                            size = Size(diameterPx, circleBounds.bottom - waterTopY)
                        )
                    } else {
                        val amplitudePx = WATER_WAVE_AMPLITUDE_DP.dp.toPx()
                        val wavelengthPx = WATER_WAVE_LENGTH_DP.dp.toPx()
                        val phase: Float = wavePhase.value

                        val waterPath = Path().apply {
                            moveTo(circleBounds.left, circleBounds.bottom)
                            val startY = (waterTopY + amplitudePx * sin(phase.toDouble()).toFloat())
                                .coerceIn(circleBounds.top, circleBounds.bottom)
                            lineTo(circleBounds.left, startY)

                            val stepPx = 4f
                            var x = circleBounds.left + stepPx
                            while (x <= circleBounds.right) {
                                val relativeX = x - circleBounds.left
                                val angle = (relativeX / wavelengthPx) * (2.0 * PI) + phase.toDouble()
                                val y = (waterTopY + amplitudePx * sin(angle).toFloat())
                                    .coerceIn(circleBounds.top, circleBounds.bottom)
                                lineTo(x, y)
                                x += stepPx
                            }
                            val endAngle = (diameterPx / wavelengthPx) * (2.0 * PI) + phase.toDouble()
                            val endY = (waterTopY + amplitudePx * sin(endAngle).toFloat())
                                .coerceIn(circleBounds.top, circleBounds.bottom)
                            lineTo(circleBounds.right, endY)
                            lineTo(circleBounds.right, circleBounds.bottom)
                            close()
                        }
                        drawPath(path = waterPath, brush = waterBrush)
                    }
                }
            }

            // 5. Crisp translucent ring border on top of water
            drawCircle(
                color = Color.White.copy(alpha = 0.30f),
                radius = circleRadius,
                center = center,
                style = Stroke(width = ZenDimens.BorderWidth.toPx())
            )
        }

        // Inner app icon stays stationary on top (never scales with breathing)
        centerContent()
    }
}

private const val HALO_MULTIPLIER = 1.6f
private const val FULL_SCALE_DELTA = 0.12f
private const val COMPACT_SCALE_DELTA = 0.06f
private const val FULL_HALO_DELTA = 0.25f
private const val COMPACT_HALO_DELTA = 0.12f
private const val WATER_WAVE_AMPLITUDE_DP = 3
private const val WATER_WAVE_LENGTH_DP = 60
private const val WATER_WAVE_CYCLE_MS = 6_000
private const val COMPLETION_RIPPLE_DURATION_MS = 850
private const val COMPLETION_RIPPLE_EXTRA_SCALE = 0.65f

@Preview(name = "BreathingCircle Agua 55% — Claro", showBackground = true, widthDp = 360, heightDp = 360)
@Composable
private fun BreathingCircleLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ZenBackground(dimmedForFocus = true) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(ZenDimens.Space16)
            ) {
                BreathingCircle(compact = false, waterLevel = 0.55f)
            }
        }
    }
}

@Preview(name = "BreathingCircle Agua 55% — Oscuro", showBackground = true, widthDp = 360, heightDp = 360)
@Composable
private fun BreathingCircleDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ZenBackground(dimmedForFocus = true) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(ZenDimens.Space16)
            ) {
                BreathingCircle(compact = false, waterLevel = 0.55f)
            }
        }
    }
}
