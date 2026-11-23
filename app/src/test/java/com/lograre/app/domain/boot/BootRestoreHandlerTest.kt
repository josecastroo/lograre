package com.lograre.app.domain.boot

import com.lograre.app.data.repository.AppLimitRepository
import com.lograre.app.data.repository.TrustedClockStateRepository
import com.lograre.app.domain.clock.DailyResetCoordinator
import com.lograre.app.domain.clock.DailyResetResult
import com.lograre.app.domain.clock.FakeDeviceClock
import com.lograre.app.domain.clock.FakeNtpTimeProvider
import com.lograre.app.domain.clock.InMemoryTrackedAppDao
import com.lograre.app.domain.clock.InMemoryTrustedClockDao
import com.lograre.app.domain.clock.TrustedClock
import com.lograre.app.domain.tracker.ForegroundUsageTracker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class BootRestoreHandlerTest {

    private val mexicoZone = ZoneId.of("America/Mexico_City")

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, mexicoZone).toInstant().toEpochMilli()

    @Test
    fun `restores same-day usage after reboot without losing accumulated time`() = runTest {
        val start = utcMillis(2026, 11, 22, 14, 0)
        val deviceClock = FakeDeviceClock(elapsedMillis = 300_000L, systemUtcMillis = start, zoneId = mexicoZone)
        val appRepo = AppLimitRepository(InMemoryTrackedAppDao())
        val ntpProvider = FakeNtpTimeProvider(null)
        val trustedClock = TrustedClock(
            deviceClock = deviceClock,
            stateRepository = TrustedClockStateRepository(InMemoryTrustedClockDao()),
            ntpTimeProvider = ntpProvider
        )
        val coordinator = DailyResetCoordinator(trustedClock, appRepo)
        val tracker = ForegroundUsageTracker(deviceClock, appRepo, coordinator)
        val bootHandler = BootRestoreHandler(coordinator, tracker)

        val day1 = trustedClock.now().dayId
        val pkg = "com.instagram.android"
        appRepo.addOrUpdateApp(pkg, dailyLimitMinutes = 20, currentDayId = day1, nowMillis = start)
        appRepo.addUsageMillis(pkg, 12 * 60_000L, day1)

        // Device reboots 10 minutes later on the same day
        deviceClock.simulateReboot(
            newElapsedMillis = 15_000L,
            newSystemUtcMillis = utcMillis(2026, 11, 22, 14, 10)
        )

        val result = bootHandler.onDeviceBootOrRestore()
        assertTrue(result is DailyResetResult.NoResetNeeded)
        assertFalse(tracker.hasActiveSession())
        assertEquals(12 * 60_000L, appRepo.getApp(pkg)!!.usedMillisToday)
    }

    @Test
    fun `resets counters on boot when NTP confirms next real day`() = runTest {
        val night = utcMillis(2026, 11, 22, 23, 0)
        val nextMorning = utcMillis(2026, 11, 23, 8, 30)
        val deviceClock = FakeDeviceClock(elapsedMillis = 500_000L, systemUtcMillis = night, zoneId = mexicoZone)
        val appRepo = AppLimitRepository(InMemoryTrackedAppDao())
        val ntpProvider = FakeNtpTimeProvider(nextMorning)
        val trustedClock = TrustedClock(
            deviceClock = deviceClock,
            stateRepository = TrustedClockStateRepository(InMemoryTrustedClockDao()),
            ntpTimeProvider = ntpProvider
        )
        val coordinator = DailyResetCoordinator(trustedClock, appRepo)
        val tracker = ForegroundUsageTracker(deviceClock, appRepo, coordinator)
        val bootHandler = BootRestoreHandler(coordinator, tracker)

        val day1 = trustedClock.now().dayId
        val pkg = "com.google.android.youtube"
        appRepo.addOrUpdateApp(pkg, dailyLimitMinutes = 15, currentDayId = day1, nowMillis = night)
        appRepo.addUsageMillis(pkg, 15 * 60_000L, day1)
        assertTrue(appRepo.getApp(pkg)!!.isBlocked)

        // Reboot next morning with internet -> NTP confirms new day -> counters reset cleanly
        deviceClock.simulateReboot(newElapsedMillis = 20_000L, newSystemUtcMillis = nextMorning)
        val result = bootHandler.onDeviceBootOrRestore()

        assertTrue(result is DailyResetResult.ResetPerformed)
        assertEquals(0L, appRepo.getApp(pkg)!!.usedMillisToday)
        assertFalse(appRepo.getApp(pkg)!!.isBlocked)
    }

    @Test
    fun `rejects fake offline overnight jump on boot and keeps app blocked`() = runTest {
        val evening = utcMillis(2026, 11, 22, 20, 0)
        val fakeTomorrow = utcMillis(2026, 11, 23, 9, 0)
        val deviceClock = FakeDeviceClock(elapsedMillis = 500_000L, systemUtcMillis = evening, zoneId = mexicoZone)
        val appRepo = AppLimitRepository(InMemoryTrackedAppDao())
        val ntpProvider = FakeNtpTimeProvider(null) // offline
        val trustedClock = TrustedClock(
            deviceClock = deviceClock,
            stateRepository = TrustedClockStateRepository(InMemoryTrustedClockDao()),
            ntpTimeProvider = ntpProvider
        )
        val coordinator = DailyResetCoordinator(trustedClock, appRepo)
        val tracker = ForegroundUsageTracker(deviceClock, appRepo, coordinator)
        val bootHandler = BootRestoreHandler(coordinator, tracker)

        val day1 = trustedClock.now().dayId
        val pkg = "com.zhiliaoapp.musically"
        appRepo.addOrUpdateApp(pkg, dailyLimitMinutes = 10, currentDayId = day1, nowMillis = evening)
        appRepo.addUsageMillis(pkg, 10 * 60_000L, day1)

        // Power off, advance clock to tomorrow, boot offline
        deviceClock.simulateReboot(newElapsedMillis = 15_000L, newSystemUtcMillis = fakeTomorrow)
        val result = bootHandler.onDeviceBootOrRestore()

        assertTrue(result is DailyResetResult.NoResetNeeded)
        assertTrue(appRepo.getApp(pkg)!!.isBlocked)
    }
}
