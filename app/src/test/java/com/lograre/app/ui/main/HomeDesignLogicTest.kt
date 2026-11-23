package com.lograre.app.ui.main

import com.lograre.app.R
import com.lograre.app.domain.model.AppLimitStatus
import com.lograre.app.ui.components.computeWaterVisualModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDesignLogicTest {

    @Test
    fun `greeting matches morning, afternoon and evening time ranges`() {
        assertEquals(R.string.greeting_morning, HomeGreeting.greetingResIdForHour(5))
        assertEquals(R.string.greeting_morning, HomeGreeting.greetingResIdForHour(11))

        assertEquals(R.string.greeting_afternoon, HomeGreeting.greetingResIdForHour(12))
        assertEquals(R.string.greeting_afternoon, HomeGreeting.greetingResIdForHour(19))

        assertEquals(R.string.greeting_evening, HomeGreeting.greetingResIdForHour(20))
        assertEquals(R.string.greeting_evening, HomeGreeting.greetingResIdForHour(23))
        assertEquals(R.string.greeting_evening, HomeGreeting.greetingResIdForHour(0))
        assertEquals(R.string.greeting_evening, HomeGreeting.greetingResIdForHour(4))
    }

    @Test
    fun `water visual model computes normal, low water, paused and ignored states`() {
        val normal = computeWaterVisualModel(
            status = AppLimitStatus.ACTIVE,
            remainingMillis = 20 * 60_000L,
            effectiveLimitMillis = 30 * 60_000L
        )
        assertEquals(20f / 30f, normal.fillFraction, 0.001f)
        assertFalse(normal.isLowWater)
        assertTrue(normal.shouldWave)
        assertFalse(normal.isMutedStatic)

        val low = computeWaterVisualModel(
            status = AppLimitStatus.ACTIVE,
            remainingMillis = 3 * 60_000L,
            effectiveLimitMillis = 30 * 60_000L
        )
        assertEquals(0.10f, low.fillFraction, 0.001f)
        assertTrue(low.isLowWater)
        assertTrue(low.shouldWave)

        val paused = computeWaterVisualModel(
            status = AppLimitStatus.BLOCKED,
            remainingMillis = 0L,
            effectiveLimitMillis = 30 * 60_000L
        )
        assertEquals(0f, paused.fillFraction, 0.001f)
        assertFalse(paused.shouldWave)

        val ignored = computeWaterVisualModel(
            status = AppLimitStatus.IGNORED_TODAY,
            remainingMillis = 0L,
            effectiveLimitMillis = 30 * 60_000L
        )
        assertEquals(1f, ignored.fillFraction, 0.001f)
        assertFalse(ignored.shouldWave)
        assertTrue(ignored.isMutedStatic)
    }
}
