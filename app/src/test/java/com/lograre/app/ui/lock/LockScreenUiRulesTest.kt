package com.lograre.app.ui.lock

import com.lograre.app.ui.components.BREATH_CYCLE_DURATION_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenUiRulesTest {

    @Test
    fun `wait duration and seconds derive cleanly from BREATH_CYCLES_BEFORE_KEYPAD`() {
        assertEquals(2 * BREATH_CYCLE_DURATION_MS, totalWaitDurationMillis(2))
        assertEquals(20, totalWaitSeconds(2))
        assertEquals(0L, totalWaitDurationMillis(0))
        assertEquals(0, totalWaitSeconds(0))
    }

    @Test
    fun `water fill rises linearly from 0 to 1 and reaches 1 when keypad opens`() {
        val total = totalWaitDurationMillis(2)
        assertEquals(0f, computeWaterFillFraction(0L, total), 0.0001f)
        assertEquals(0.25f, computeWaterFillFraction(total / 4, total), 0.0001f)
        assertEquals(0.5f, computeWaterFillFraction(total / 2, total), 0.0001f)
        assertEquals(1f, computeWaterFillFraction(total, total), 0.0001f)
        assertEquals(1f, computeWaterFillFraction(total + 5_000L, total), 0.0001f)
        assertEquals(1f, computeWaterFillFraction(0L, 0L), 0.0001f)
    }

    @Test
    fun `keypad opens strictly when wait duration completes or when cycles is zero`() {
        val total = totalWaitDurationMillis(2)
        assertFalse(shouldShowLockKeypad(elapsedMillis = 0L, requiredBreathCycles = 2))
        assertFalse(shouldShowLockKeypad(elapsedMillis = total - 1L, requiredBreathCycles = 2))
        assertTrue(shouldShowLockKeypad(elapsedMillis = total, requiredBreathCycles = 2))
        assertTrue(shouldShowLockKeypad(elapsedMillis = 0L, requiredBreathCycles = 0))
    }
}
