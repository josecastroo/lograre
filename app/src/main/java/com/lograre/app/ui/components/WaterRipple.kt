package com.lograre.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.semantics.Role
import com.lograre.app.ui.theme.LocalZenPalette
import com.lograre.app.ui.theme.ZenDimens
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Custom water-ripple [IndicationNodeFactory]:
 * expands a circular wave from the exact touch coordinates up to 2x the element's size
 * over 900 ms while fading opacity from 25% to 0%.
 *
 * When system animations are disabled, falls back to a calm static 12% highlight while pressed.
 */
@Composable
fun rememberWaterRippleIndication(
    color: Color = LocalZenPalette.current.rippleColor,
    animationsEnabled: Boolean = rememberAnimationsEnabled()
): IndicationNodeFactory = remember(color, animationsEnabled) {
    WaterRippleIndication(
        color = color,
        animationsEnabled = animationsEnabled
    )
}

/**
 * Convenience modifier applying [rememberWaterRippleIndication] to a clickable surface
 * in place of Material's default ripple.
 */
fun Modifier.waterRipple(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    color: Color? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
): Modifier = composed {
    val resolvedColor = color ?: LocalZenPalette.current.rippleColor
    val animationsEnabled = rememberAnimationsEnabled()
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val indication = rememberWaterRippleIndication(
        color = resolvedColor,
        animationsEnabled = animationsEnabled
    )
    this.clickable(
        interactionSource = resolvedInteractionSource,
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick
    )
}

internal data class WaterRippleIndication(
    private val color: Color,
    private val animationsEnabled: Boolean
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        WaterRippleNode(
            interactionSource = interactionSource,
            color = color,
            animationsEnabled = animationsEnabled
        )
}

private class RippleWave(
    val origin: Offset,
    val progress: Animatable<Float, *> = Animatable(0f)
)

private class WaterRippleNode(
    private val interactionSource: InteractionSource,
    private val color: Color,
    private val animationsEnabled: Boolean
) : Modifier.Node(), DrawModifierNode {

    private val activeWaves = mutableStateListOf<RippleWave>()
    private var isPressedStatic by mutableStateOf(false)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        if (animationsEnabled) {
                            spawnWave(interaction.pressPosition)
                        } else {
                            isPressedStatic = true
                        }
                    }

                    is PressInteraction.Release,
                    is PressInteraction.Cancel -> {
                        isPressedStatic = false
                    }
                }
            }
        }
    }

    private fun spawnWave(origin: Offset) {
        val wave = RippleWave(origin = origin)
        activeWaves.add(wave)
        coroutineScope.launch {
            try {
                wave.progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = ZenDimens.WaterRippleDurationMs,
                        easing = FastOutSlowInEasing
                    )
                )
            } finally {
                activeWaves.remove(wave)
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()

        if (!animationsEnabled) {
            if (isPressedStatic) {
                drawRect(color = color.copy(alpha = STATIC_PRESSED_ALPHA))
            }
            return
        }

        val maxDiameter = max(size.width, size.height) * 2f
        val maxRadius = maxDiameter / 2f
        for (i in activeWaves.indices) {
            val wave = activeWaves[i]
            val p = wave.progress.value
            val alpha = MAX_RIPPLE_ALPHA * (1f - p).coerceIn(0f, 1f)
            val radius = maxRadius * p
            if (alpha > 0.001f && radius > 0f) {
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = radius,
                    center = wave.origin
                )
            }
        }
    }

    companion object {
        private const val MAX_RIPPLE_ALPHA = 0.25f
        private const val STATIC_PRESSED_ALPHA = 0.12f
    }
}
