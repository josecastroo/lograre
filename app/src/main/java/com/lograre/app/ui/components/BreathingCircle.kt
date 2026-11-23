package com.lograre.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lograre.app.R
import com.lograre.app.ui.theme.AppTheme
import com.lograre.app.ui.theme.LocalZenPalette
import com.lograre.app.ui.theme.LocalZenTypography
import com.lograre.app.ui.theme.ThemeMode
import com.lograre.app.ui.theme.ZenDimens

enum class BreathPhase {
    INHALE,
    EXHALE
}

/**
 * Organic breathing circle for the lock screen pause experience.
 *
 * - Base diameter 200 dp (shrinks smoothly to 120 dp in [compact] mode).
 * - Inhale 4 s (scale 1.0 → 1.12; 1.0 → 1.06 when compact), exhale 6 s back to 1.0.
 * - Outer radial halo (1.6x diameter, accent @ 35% → transparent) scales 1.0 → 1.25 in sync.
 * - Fill gradient `accent → accent2` at 90% opacity with a 1 dp white @ 30% border.
 * - Inner [centerContent] stays steady and never pulses with the circle scale.
 * - Static circle + "Respira un momento" when system animations are disabled; pauses when not RESUMED.
 */
@Composable
fun BreathingCircle(
    compact: Boolean,
    modifier: Modifier = Modifier,
    onCycleCompleted: (Int) -> Unit = {},
    centerContent: @Composable BoxScope.() -> Unit = {}
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current
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
    var currentPhase by remember { mutableStateOf(BreathPhase.INHALE) }

    LaunchedEffect(runContinuous) {
        if (!runContinuous) {
            breathProgress.snapTo(0f)
            currentPhase = BreathPhase.INHALE
            return@LaunchedEffect
        }
        var cycles = 0
        while (true) {
            currentPhase = BreathPhase.INHALE
            breathProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = INHALE_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
            currentPhase = BreathPhase.EXHALE
            breathProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = EXHALE_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
            cycles += 1
            onCycleCompleted(cycles)
        }
    }

    val haloContainerSize = baseDiameter * HALO_MULTIPLIER

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZenDimens.Space8)
    ) {
        Box(
            modifier = Modifier.size(haloContainerSize),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val progress = breathProgress.value
                val circleScale = 1f + progress * (if (compact) COMPACT_SCALE_DELTA else FULL_SCALE_DELTA)
                val haloScale = 1f + progress * (if (compact) COMPACT_HALO_DELTA else FULL_HALO_DELTA)

                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = (baseDiameter.toPx() / 2f)
                val circleRadius = baseRadius * circleScale
                val haloRadius = (baseRadius * HALO_MULTIPLIER) * haloScale

                // Soft outer halo
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

                // Breathing gradient body
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = 0.90f),
                            palette.accent2.copy(alpha = 0.90f)
                        ),
                        start = Offset(center.x - circleRadius, center.y - circleRadius),
                        end = Offset(center.x + circleRadius, center.y + circleRadius)
                    ),
                    radius = circleRadius,
                    center = center
                )

                // Subtle translucent ring border
                drawCircle(
                    color = Color.White.copy(alpha = 0.30f),
                    radius = circleRadius,
                    center = center,
                    style = Stroke(width = ZenDimens.BorderWidth.toPx())
                )
            }

            // Inner app icon stays stationary (never pulses with circleScale)
            centerContent()
        }

        if (!animationsEnabled) {
            Text(
                text = stringResource(R.string.lock_subtitle),
                style = typo.label,
                color = palette.textSecondary
            )
        } else {
            Crossfade(
                targetState = currentPhase,
                animationSpec = tween(durationMillis = PHASE_TEXT_CROSSFADE_MS),
                label = "breath_phase_text"
            ) { phase ->
                Text(
                    text = when (phase) {
                        BreathPhase.INHALE -> stringResource(R.string.breath_inhale)
                        BreathPhase.EXHALE -> stringResource(R.string.breath_exhale)
                    },
                    style = typo.label,
                    color = palette.textSecondary
                )
            }
        }
    }
}

private const val INHALE_DURATION_MS = 4_000
private const val EXHALE_DURATION_MS = 6_000
private const val PHASE_TEXT_CROSSFADE_MS = 400
private const val HALO_MULTIPLIER = 1.6f
private const val FULL_SCALE_DELTA = 0.12f
private const val COMPACT_SCALE_DELTA = 0.06f
private const val FULL_HALO_DELTA = 0.25f
private const val COMPACT_HALO_DELTA = 0.12f

@Preview(name = "BreathingCircle — Claro", showBackground = true, widthDp = 360, heightDp = 380)
@Composable
private fun BreathingCircleLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ZenBackground(dimmedForFocus = true) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(ZenDimens.Space16)
            ) {
                BreathingCircle(compact = false)
            }
        }
    }
}

@Preview(name = "BreathingCircle — Oscuro", showBackground = true, widthDp = 360, heightDp = 380)
@Composable
private fun BreathingCircleDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ZenBackground(dimmedForFocus = true) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(ZenDimens.Space16)
            ) {
                BreathingCircle(compact = false)
            }
        }
    }
}
