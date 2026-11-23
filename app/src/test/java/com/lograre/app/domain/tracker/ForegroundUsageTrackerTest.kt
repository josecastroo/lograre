package com.lograre.app.domain.tracker

import com.lograre.app.data.repository.AppLimitRepository
import com.lograre.app.data.repository.TrustedClockStateRepository
import com.lograre.app.domain.clock.DailyResetCoordinator
import com.lograre.app.domain.clock.FakeDeviceClock
import com.lograre.app.domain.clock.FakeNtpTimeProvider
import com.lograre.app.domain.clock.InMemoryTrackedAppDao
import com.lograre.app.domain.clock.InMemoryTrustedClockDao
import com.lograre.app.domain.clock.TrustedClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ForegroundUsageTrackerTest {

    private val mexicoZone = ZoneId.of("America/Mexico_City")

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, mexicoZone).toInstant().toEpochMilli()

    private class TestHarness(
        startUtcMillis: Long,
        protectedPackages: Set<String> = ProtectedPackages.STATIC_CRITICAL_PACKAGES + "com.example.launcher"
    ) {
        val deviceClock = FakeDeviceClock(
            elapsedMillis = 100_000L,
            systemUtcMillis = startUtcMillis,
            zoneId = ZoneId.of("America/Mexico_City")
        )
        val appRepo = AppLimitRepository(InMemoryTrackedAppDao())
        val trustedClock = TrustedClock(
            deviceClock = deviceClock,
            stateRepository = TrustedClockStateRepository(InMemoryTrustedClockDao()),
            ntpTimeProvider = FakeNtpTimeProvider(null)
        )
        val dailyResetCoordinator = DailyResetCoordinator(trustedClock, appRepo)
        val tracker = ForegroundUsageTracker(
            deviceClock = deviceClock,
            appLimitRepository = appRepo,
            dailyResetCoordinator = dailyResetCoordinator,
            protectedPackagesProvider = { protectedPackages }
        )
    }

    @Test
    fun `counts usage only while tracked app is in foreground and pauses on switch`() = runTest {
        val start = utcMillis(2026, 11, 22, 15, 0)
        val h = TestHarness(start)
        val dayId = h.trustedClock.now().dayId
        val instagram = "com.instagram.android"

        h.appRepo.addOrUpdateApp(instagram, dailyLimitMinutes = 20, currentDayId = dayId, nowMillis = start)

        assertEquals(TrackerAction.None, h.tracker.onForegroundPackageChanged(instagram))
        assertEquals(instagram, h.tracker.currentActivePackage())

        h.deviceClock.advanceRealTime(90_000L)
        assertEquals(TrackerAction.None, h.tracker.tick())
        assertEquals(90_000L, h.appRepo.getApp(instagram)!!.usedMillisToday)

        // Switch to launcher: flushes remaining 30s and stops session
        h.deviceClock.advanceRealTime(30_000L)
        assertEquals(TrackerAction.None, h.tracker.onForegroundPackageChanged("com.example.launcher"))
        assertNull(h.tracker.currentActivePackage())
        assertEquals(120_000L, h.appRepo.getApp(instagram)!!.usedMillisToday)

        // Time passes while on launcher -> Instagram usage does NOT advance
        h.deviceClock.advanceRealTime(5 * 60_000L)
        assertEquals(TrackerAction.None, h.tracker.tick())
        assertEquals(120_000L, h.appRepo.getApp(instagram)!!.usedMillisToday)
    }

    @Test
    fun `pauses counting immediately on screen off`() = runTest {
        val start = utcMillis(2026, 11, 22, 15, 0)
        val h = TestHarness(start)
        val dayId = h.trustedClock.now().dayId
        val youtube = "com.google.android.youtube"

        h.appRepo.addOrUpdateApp(youtube, dailyLimitMinutes = 30, currentDayId = dayId, nowMillis = start)
        h.tracker.onForegroundPackageChanged(youtube)

        h.deviceClock.advanceRealTime(45_000L)
        assertEquals(TrackerAction.None, h.tracker.onScreenOff())
        assertFalse(h.tracker.hasActiveSession())
        assertEquals(45_000L, h.appRepo.getApp(youtube)!!.usedMillisToday)

        // 10 minutes pass with screen off -> no usage credited
        h.deviceClock.advanceRealTime(10 * 60_000L)
        assertEquals(TrackerAction.None, h.tracker.tick())
        assertEquals(45_000L, h.appRepo.getApp(youtube)!!.usedMillisToday)
    }

    @Test
    fun `limit of zero minutes triggers block immediately on first open`() = runTest {
        val start = utcMillis(2026, 11, 22, 15, 0)
        val h = TestHarness(start)
        val dayId = h.trustedClock.now().dayId
        val tiktok = "com.zhiliaoapp.musically"

        h.appRepo.addOrUpdateApp(tiktok, dailyLimitMinutes = 0, currentDayId = dayId, nowMillis = start)

        val action = h.tracker.onForegroundPackageChanged(tiktok)
        assertEquals(TrackerAction.BlockRequired(tiktok), action)
        assertFalse(h.tracker.hasActiveSession())
    }

    @Test
    fun `exhausting limit triggers block and extra time resumes counting until exhausted again`() = runTest {
        val start = utcMillis(2026, 11, 22, 15, 0)
        val h = TestHarness(start)
        val dayId = h.trustedClock.now().dayId
        val instagram = "com.instagram.android"

        h.appRepo.addOrUpdateApp(instagram, dailyLimitMinutes = 2, currentDayId = dayId, nowMillis = start)
        assertEquals(TrackerAction.None, h.tracker.onForegroundPackageChanged(instagram))

        // Exhaust initial 2 minutes
        h.deviceClock.advanceRealTime(2 * 60_000L)
        assertEquals(TrackerAction.BlockRequired(instagram), h.tracker.tick())
        assertFalse(h.tracker.hasActiveSession())

        // PIN entered -> grant +5 extra minutes
        h.appRepo.addExtraMinutes(instagram, extraMinutes = 5, dayId = dayId)
        assertEquals(TrackerAction.None, h.tracker.onForegroundPackageChanged(instagram))
        assertTrue(h.tracker.hasActiveSession())

        // Use 4 minutes of extra time -> still active
        h.deviceClock.advanceRealTime(4 * 60_000L)
        assertEquals(TrackerAction.None, h.tracker.tick())
        assertEquals(6 * 60_000L, h.appRepo.getApp(instagram)!!.usedMillisToday)

        // Use final 1 minute -> blocks again
        h.deviceClock.advanceRealTime(60_000L)
        assertEquals(TrackerAction.BlockRequired(instagram), h.tracker.tick())
        assertFalse(h.tracker.hasActiveSession())
    }

    @Test
    fun `ignored today app does not count and never triggers block or warning`() = runTest {
        val start = utcMillis(2026, 11, 22, 15, 0)
        val h = TestHarness(start)
        val dayId = h.trustedClock.now().dayId
        val youtube = "com.google.android.youtube"

        h.appRepo.addOrUpdateApp(youtube, dailyLimitMinutes = 10, currentDayId = dayId, nowMillis = start)
        h.appRepo.ignoreForToday(youtube, dayId)

        assertEquals(TrackerAction.None, h.tracker.onForegroundPackageChanged(youtube))
        assertFalse(h.tracker.hasActiveSession())

        h.deviceClock.advanceRealTime(20 * 60_000L)
        assertEquals(TrackerAction.None, h.tracker.tick())
        assertEquals(0L, h.appRepo.getApp(youtube)!!.usedMillisToday)
    }

    @Test
    fun `five minute warning fires exactly once when daily limit is at least 5 minutes`() = runTest {
        val start = utcMillis(2026, 11, 22, 15, 0)
        val h = TestHarness(start)
        val dayId = h.trustedClock.now().dayId
        val instagram = "com.instagram.android"

        h.appRepo.addOrUpdateApp(instagram, dailyLimitMinutes = 10, currentDayId = dayId, nowMillis = start)
        assertEquals(TrackerAction.None, h.tracker.onForegroundPackageChanged(instagram))

        // 4 minutes used -> 6 minutes remaining -> no warning yet
        h.deviceClock.advanceRealTime(4 * 60_000L)
        assertEquals(TrackerAction.None, h.tracker.tick())
        assertFalse(h.appRepo.getApp(instagram)!!.warningSentToday)

        // 1 more minute used -> 5 minutes remaining -> warning fires!
        h.deviceClock.advanceRealTime(60_000L)
        val warningAction = h.tracker.tick()
        assertTrue(warningAction is TrackerAction.FiveMinuteWarning)
        assertEquals(instagram, (warningAction as TrackerAction.FiveMinuteWarning).packageName)
        assertEquals(5 * 60_000L, warningAction.remainingMillis)
        assertTrue(h.appRepo.getApp(instagram)!!.warningSentToday)

        // 1 more minute used -> 4 minutes remaining -> warning must NOT fire again
        h.deviceClock.advanceRealTime(60_000L)
        assertEquals(TrackerAction.None, h.tracker.tick())
    }

    @Test
    fun `five minute warning never fires when daily limit is less than 5 minutes`() = runTest {
        val start = utcMillis(2026, 11, 22, 15, 0)
        val h = TestHarness(start)
        val dayId = h.trustedClock.now().dayId
        val reddit = "com.reddit.frontpage"

        h.appRepo.addOrUpdateApp(reddit, dailyLimitMinutes = 4, currentDayId = dayId, nowMillis = start)
        assertEquals(TrackerAction.None, h.tracker.onForegroundPackageChanged(reddit))

        h.deviceClock.advanceRealTime(2 * 60_000L)
        assertEquals(TrackerAction.None, h.tracker.tick())
        assertFalse(h.appRepo.getApp(reddit)!!.warningSentToday)

        h.deviceClock.advanceRealTime(2 * 60_000L)
        assertEquals(TrackerAction.BlockRequired(reddit), h.tracker.tick())
        assertFalse(h.appRepo.getApp(reddit)!!.warningSentToday)
    }

    @Test
    fun `protected system packages are never tracked or blocked`() = runTest {
        val start = utcMillis(2026, 11, 22, 15, 0)
        val h = TestHarness(start)
        val dayId = h.trustedClock.now().dayId

        // Even if somehow inserted in DB, protected packages are ignored
        h.appRepo.addOrUpdateApp("com.android.settings", dailyLimitMinutes = 0, currentDayId = dayId, nowMillis = start)
        assertEquals(TrackerAction.None, h.tracker.onForegroundPackageChanged("com.android.settings"))
        assertFalse(h.tracker.hasActiveSession())
    }

    @Test
    fun `crossing real midnight while app is active resets usage and continues cleanly`() = runTest {
        val start = utcMillis(2026, 11, 22, 23, 58)
        val h = TestHarness(start)
        val day1 = h.trustedClock.now().dayId
        val youtube = "com.google.android.youtube"

        h.appRepo.addOrUpdateApp(youtube, dailyLimitMinutes = 10, currentDayId = day1, nowMillis = start)
        h.appRepo.addUsageMillis(youtube, 8 * 60_000L, day1)
        h.tracker.onForegroundPackageChanged(youtube)

        // Advance 1 minute -> 23:59 (9 min used)
        h.deviceClock.advanceRealTime(60_000L)
        h.tracker.tick()
        assertEquals(9 * 60_000L, h.appRepo.getApp(youtube)!!.usedMillisToday)

        // Advance 2 minutes -> 00:01 next day -> daily reset triggers instead of blocking!
        h.deviceClock.advanceRealTime(2 * 60_000L)
        assertEquals(TrackerAction.None, h.tracker.tick())
        val afterMidnight = h.appRepo.getApp(youtube)!!
        assertEquals(0L, afterMidnight.usedMillisToday)
        assertEquals(day1 + 1, afterMidnight.lastUpdatedDayId)
        assertTrue(h.tracker.hasActiveSession())

        // Subsequent usage counts on the new day
        h.deviceClock.advanceRealTime(60_000L)
        assertEquals(TrackerAction.None, h.tracker.tick())
        assertEquals(60_000L, h.appRepo.getApp(youtube)!!.usedMillisToday)
    }

    @Test
    fun `switching between two tracked apps flushes first app and tracks second`() = runTest {
        val start = utcMillis(2026, 11, 22, 16, 0)
        val h = TestHarness(start)
        val dayId = h.trustedClock.now().dayId
        val appA = "com.instagram.android"
        val appB = "com.google.android.youtube"

        h.appRepo.addOrUpdateApp(appA, dailyLimitMinutes = 15, currentDayId = dayId, nowMillis = start)
        h.appRepo.addOrUpdateApp(appB, dailyLimitMinutes = 15, currentDayId = dayId, nowMillis = start)

        h.tracker.onForegroundPackageChanged(appA)
        h.deviceClock.advanceRealTime(60_000L)

        // Switch directly from appA to appB
        h.tracker.onForegroundPackageChanged(appB)
        assertEquals(60_000L, h.appRepo.getApp(appA)!!.usedMillisToday)
        assertEquals(0L, h.appRepo.getApp(appB)!!.usedMillisToday)
        assertEquals(appB, h.tracker.currentActivePackage())

        h.deviceClock.advanceRealTime(120_000L)
        h.tracker.tick()
        assertEquals(60_000L, h.appRepo.getApp(appA)!!.usedMillisToday)
        assertEquals(120_000L, h.appRepo.getApp(appB)!!.usedMillisToday)
    }

    @Test
    fun `raising daily limit from exhausted 1 min to 4 min resumes counting cleanly`() = runTest {
        val start = utcMillis(2026, 11, 22, 16, 0)
        val h = TestHarness(start)
        val dayId = h.trustedClock.now().dayId
        val instagram = "com.instagram.android"

        h.appRepo.addOrUpdateApp(instagram, dailyLimitMinutes = 1, currentDayId = dayId, nowMillis = start)
        assertEquals(TrackerAction.None, h.tracker.onForegroundPackageChanged(instagram))

        // Exhaust 1 minute -> blocked
        h.deviceClock.advanceRealTime(60_000L)
        assertEquals(TrackerAction.BlockRequired(instagram), h.tracker.tick())
        assertFalse(h.tracker.hasActiveSession())

        // User opens lograre and increases daily limit to 4 minutes
        h.tracker.onForegroundPackageChanged("com.lograre.app")
        h.appRepo.addOrUpdateApp(instagram, dailyLimitMinutes = 4, currentDayId = dayId, nowMillis = start + 65_000L)

        // User returns to Instagram -> counting resumes from 1 min up to 4 min
        assertEquals(TrackerAction.None, h.tracker.onForegroundPackageChanged(instagram))
        assertTrue(h.tracker.hasActiveSession())

        h.deviceClock.advanceRealTime(35_000L)
        assertEquals(TrackerAction.None, h.tracker.tick())
        assertEquals(95_000L, h.appRepo.getApp(instagram)!!.usedMillisToday)

        h.deviceClock.advanceRealTime(145_000L)
        assertEquals(TrackerAction.BlockRequired(instagram), h.tracker.tick())
        assertEquals(240_000L, h.appRepo.getApp(instagram)!!.usedMillisToday)
    }
}
