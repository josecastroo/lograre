package com.lograre.app.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationPreferencesTest {

    @Test
    fun `isAnimatorScaleEnabled returns false for zero or negative scales and true for positive`() {
        assertFalse(isAnimatorScaleEnabled(0f))
        assertFalse(isAnimatorScaleEnabled(-1f))
        assertTrue(isAnimatorScaleEnabled(0.5f))
        assertTrue(isAnimatorScaleEnabled(1f))
        assertTrue(isAnimatorScaleEnabled(2f))
    }

    @Test
    fun `WaterRippleIndication implements structural equality required by IndicationNodeFactory`() {
        val a = WaterRippleIndication(color = Color.White, animationsEnabled = true)
        val b = WaterRippleIndication(color = Color.White, animationsEnabled = true)
        val c = WaterRippleIndication(color = Color.White, animationsEnabled = false)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }
}
