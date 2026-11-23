package com.lograre.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lograre.app.domain.model.AppLimitStatus
import com.lograre.app.ui.theme.AppTheme
import com.lograre.app.ui.theme.LocalZenPalette
import com.lograre.app.ui.theme.LocalZenTypography
import com.lograre.app.ui.theme.ThemeMode
import com.lograre.app.ui.theme.ZenDimens
import com.lograre.app.ui.theme.ZenShapes
import kotlin.math.PI
import kotlin.math.sin

internal data class WaterVisualModel(
    val fillFraction: Float,
    val isLowWater: Boolean,
    val shouldWave: Boolean,
    val isMutedStatic: Boolean
)

internal fun computeWaterVisualModel(
    status: AppLimitStatus,
    remainingMillis: Long,
    effectiveLimitMillis: Long
): WaterVisualModel = when (status) {
    AppLimitStatus.IGNORED_TODAY -> WaterVisualModel(
        fillFraction = 1f,
        isLowWater = false,
        shouldWave = false,
        isMutedStatic = true
    )

    AppLimitStatus.BLOCKED -> WaterVisualModel(
        fillFraction = 0f,
        isLowWater = false,
        shouldWave = false,
        isMutedStatic = false
    )

    AppLimitStatus.ACTIVE -> {
        val fraction = if (effectiveLimitMillis <= 0L) {
            0f
        } else {
            (remainingMillis.toFloat() / effectiveLimitMillis.toFloat()).coerceIn(0f, 1f)
        }
        WaterVisualModel(
            fillFraction = fraction,
            isLowWater = fraction > 0f && fraction < 0.20f,
            shouldWave = fraction > 0.01f,
            isMutedStatic = false
        )
    }
}

/**
 * Animated water-level capsule replacing traditional linear progress bars.
 *
 * - 40 × 64 dp capsule with 20 dp corner radius and 1 dp outline (`text` @ 25%).
 * - Proportional to **remaining** time (full = all time left, empty = paused).
 * - Continuous sine wave surface (amplitude 3 dp, wavelength ~60 dp, 6 s cycle; slows to 9 s
 *   and shifts to `accent2` below 20% remaining).
 * - Smoothly animates level changes over 600 ms.
 * - Automatically freezes flat when animations are disabled or screen is not RESUMED.
 */
@Composable
fun WaterLevelIndicator(
    status: AppLimitStatus,
    remainingMillis: Long,
    effectiveLimitMillis: Long,
    accessibilityDescription: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalZenPalette.current
    val animationsEnabled = rememberAnimationsEnabled()
    val runContinuous = rememberShouldRunContinuousAnimations()

    val model = computeWaterVisualModel(
        status = status,
        remainingMillis = remainingMillis,
        effectiveLimitMillis = effectiveLimitMillis
    )

    val animatedFill by animateFloatAsState(
        targetValue = model.fillFraction,
        animationSpec = tween(
            durationMillis = if (animationsEnabled) ZenDimens.WaterLevelChangeDurationMs else 0,
            easing = FastOutSlowInEasing
        ),
        label = "water_fill_level"
    )

    val targetBaseColor = when {
        model.isMutedStatic -> palette.accent.copy(alpha = 0.32f)
        model.isLowWater -> palette.accent2
        else -> palette.accent
    }
    val baseWaterColor by animateColorAsState(
        targetValue = targetBaseColor,
        animationSpec = tween(durationMillis = if (animationsEnabled) 400 else 0),
        label = "water_color"
    )

    val wavePhase: State<Float> = if (runContinuous && model.shouldWave) {
        val cycleDuration = if (model.isLowWater) LOW_WATER_WAVE_CYCLE_MS else NORMAL_WAVE_CYCLE_MS
        val transition = rememberInfiniteTransition(label = "water_wave_$cycleDuration")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = cycleDuration, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_phase"
        )
    } else {
        rememberUpdatedState(0f)
    }

    val topAlpha = if (model.isMutedStatic) 0.45f else 0.85f
    val bottomAlpha = if (model.isMutedStatic) 0.25f else 0.60f

    Canvas(
        modifier = modifier
            .size(width = ZenDimens.WaterCapsuleWidth, height = ZenDimens.WaterCapsuleHeight)
            .clip(ZenShapes.WaterCapsule)
            .border(
                width = ZenDimens.BorderWidth,
                color = palette.text.copy(alpha = 0.25f),
                shape = ZenShapes.WaterCapsule
            )
            .semantics {
                contentDescription = accessibilityDescription
            }
    ) {
        val fill = animatedFill.coerceIn(0f, 1f)
        if (fill <= 0.001f) return@Canvas

        val width = size.width
        val height = size.height
        val waterTopY = height * (1f - fill)

        val waterBrush = Brush.verticalGradient(
            colors = listOf(
                baseWaterColor.copy(alpha = topAlpha),
                baseWaterColor.copy(alpha = bottomAlpha)
            ),
            startY = waterTopY.coerceIn(0f, height),
            endY = height
        )

        val drawWavySurface = runContinuous && model.shouldWave && fill < 0.995f
        if (!drawWavySurface) {
            drawRect(
                brush = waterBrush,
                topLeft = androidx.compose.ui.geometry.Offset(0f, waterTopY),
                size = androidx.compose.ui.geometry.Size(width, height - waterTopY)
            )
            return@Canvas
        }

        val amplitudePx = WAVE_AMPLITUDE_DP.dp.toPx()
        val wavelengthPx = WAVE_LENGTH_DP.dp.toPx()
        val phase = wavePhase.value

        val path = Path().apply {
            moveTo(0f, height)
            val startY = waterTopY + amplitudePx * sin(phase)
            lineTo(0f, startY)

            val stepPx = 4f
            var x = stepPx
            while (x <= width) {
                val angle = (x / wavelengthPx) * (2.0 * PI).toFloat() + phase
                val y = (waterTopY + amplitudePx * sin(angle)).coerceIn(0f, height)
                lineTo(x, y)
                x += stepPx
            }
            val endAngle = (width / wavelengthPx) * (2.0 * PI).toFloat() + phase
            lineTo(width, (waterTopY + amplitudePx * sin(endAngle)).coerceIn(0f, height))
            lineTo(width, height)
            close()
        }

        drawPath(path = path, brush = waterBrush)
    }
}

private const val WAVE_AMPLITUDE_DP = 3
private const val WAVE_LENGTH_DP = 60
private const val NORMAL_WAVE_CYCLE_MS = 6_000
private const val LOW_WATER_WAVE_CYCLE_MS = 9_000

@Composable
private fun WaterIndicatorShowcase() {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current
    ZenBackground {
        GlassCard(modifier = Modifier.padding(ZenDimens.ScreenHorizontalPadding)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ZenDimens.Space16),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WaterLevelIndicator(
                        status = AppLimitStatus.ACTIVE,
                        remainingMillis = 20 * 60_000L,
                        effectiveLimitMillis = 30 * 60_000L,
                        accessibilityDescription = "Instagram, quedan 20 de 30 minutos"
                    )
                    Text("66%", style = typo.label, color = palette.textSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WaterLevelIndicator(
                        status = AppLimitStatus.ACTIVE,
                        remainingMillis = 4 * 60_000L,
                        effectiveLimitMillis = 30 * 60_000L,
                        accessibilityDescription = "YouTube, quedan 4 de 30 minutos"
                    )
                    Text("<20%", style = typo.label, color = palette.textSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WaterLevelIndicator(
                        status = AppLimitStatus.BLOCKED,
                        remainingMillis = 0L,
                        effectiveLimitMillis = 30 * 60_000L,
                        accessibilityDescription = "TikTok, en pausa"
                    )
                    Text("En pausa", style = typo.label, color = palette.textSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WaterLevelIndicator(
                        status = AppLimitStatus.IGNORED_TODAY,
                        remainingMillis = 0L,
                        effectiveLimitMillis = 30 * 60_000L,
                        accessibilityDescription = "Reddit, libre hoy"
                    )
                    Text("Libre hoy", style = typo.label, color = palette.textSecondary)
                }
            }
        }
    }
}

@Preview(name = "WaterLevelIndicator — Claro", showBackground = true, widthDp = 360, heightDp = 180)
@Composable
private fun WaterLevelLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        WaterIndicatorShowcase()
    }
}

@Preview(name = "WaterLevelIndicator — Oscuro", showBackground = true, widthDp = 360, heightDp = 180)
@Composable
private fun WaterLevelDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        WaterIndicatorShowcase()
    }
}
