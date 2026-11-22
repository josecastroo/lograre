package com.lograre.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackedAppTest {

    @Test
    fun `limit of zero minutes starts blocked immediately`() {
        val app = TrackedApp(
            packageName = "com.instagram.android",
            dailyLimitMinutes = 0
        )

        assertEquals(0L, app.effectiveLimitMillis)
        assertEquals(0L, app.remainingMillis)
        assertEquals(AppLimitStatus.BLOCKED, app.status)
        assertTrue(app.isBlocked)
    }

    @Test
    fun `limit of zero minutes becomes active when extra time is granted`() {
        val app = TrackedApp(
            packageName = "com.instagram.android",
            dailyLimitMinutes = 0,
            usedMillisToday = 2 * 60_000L,
            extraMillisToday = 5 * 60_000L
        )

        assertEquals(5 * 60_000L, app.effectiveLimitMillis)
        assertEquals(3 * 60_000L, app.remainingMillis)
        assertEquals(AppLimitStatus.ACTIVE, app.status)
        assertFalse(app.isBlocked)
    }

    @Test
    fun `app becomes blocked once usage reaches daily limit plus extra time`() {
        val app = TrackedApp(
            packageName = "com.google.android.youtube",
            dailyLimitMinutes = 30,
            usedMillisToday = 45 * 60_000L,
            extraMillisToday = 15 * 60_000L
        )

        assertEquals(45 * 60_000L, app.effectiveLimitMillis)
        assertEquals(0L, app.remainingMillis)
        assertEquals(AppLimitStatus.BLOCKED, app.status)
        assertTrue(app.isBlocked)
    }

    @Test
    fun `ignored today overrides blocked state`() {
        val app = TrackedApp(
            packageName = "com.zhiliaoapp.musically",
            dailyLimitMinutes = 0,
            usedMillisToday = 60_000L,
            ignoredToday = true
        )

        assertEquals(AppLimitStatus.IGNORED_TODAY, app.status)
        assertFalse(app.isBlocked)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative daily limit is rejected`() {
        TrackedApp(
            packageName = "com.example.app",
            dailyLimitMinutes = -1
        )
    }
}
