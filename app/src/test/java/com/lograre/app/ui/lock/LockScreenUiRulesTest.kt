package com.lograre.app.ui.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenUiRulesTest {

    @Test
    fun `keypad waits for required breathing cycles when animations are enabled`() {
        assertFalse(shouldShowLockKeypad(completedBreathCycles = 0, requiredBreathCycles = 2, animationsEnabled = true))
        assertFalse(shouldShowLockKeypad(completedBreathCycles = 1, requiredBreathCycles = 2, animationsEnabled = true))
        assertTrue(shouldShowLockKeypad(completedBreathCycles = 2, requiredBreathCycles = 2, animationsEnabled = true))
        assertTrue(shouldShowLockKeypad(completedBreathCycles = 3, requiredBreathCycles = 2, animationsEnabled = true))
    }

    @Test
    fun `keypad appears immediately when required cycles is zero or animations disabled`() {
        assertTrue(shouldShowLockKeypad(completedBreathCycles = 0, requiredBreathCycles = 0, animationsEnabled = true))
        assertTrue(shouldShowLockKeypad(completedBreathCycles = 0, requiredBreathCycles = 2, animationsEnabled = false))
    }
}
