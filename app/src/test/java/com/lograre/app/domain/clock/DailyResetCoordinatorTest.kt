package com.lograre.app.domain.clock

import com.lograre.app.data.repository.AppLimitRepository
import com.lograre.app.data.repository.TrustedClockStateRepository
import com.lograre.app.domain.model.AppLimitStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyResetCoordinatorTest {

    private val mexicoZone = ZoneId.of("America/Mexico_City")

    private fun utcMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, mexicoZone).toInstant().toEpochMilli()

    @Test
    fun `tampering with system clock or timezone does not reset tracked apps`() = runTest {
        val startUtc = utcMillis(2026, 11, 22, 20, 0)
        val deviceClock = FakeDeviceClock(elapsedMillis = 100_000L, systemUtcMillis = startUtc, zoneId = mexicoZone)
        val appRepo = AppLimitRepository(InMemoryTrackedAppDao())
        val trustedClock = TrustedClock(
            deviceClock = deviceClock,
            stateRepository = TrustedClockStateRepository(InMemoryTrustedClockDao()),
            ntpTimeProvider = FakeNtpTimeProvider(null)
        )
        val coordinator = DailyResetCoordinator(trustedClock, appRepo)

        val initialSnapshot = trustedClock.now()
        appRepo.addOrUpdateApp("com.instagram.android", 15, initialSnapshot.dayId, startUtc)
        appRepo.addUsageMillis("com.instagram.android", 15 * 60_000L, initialSnapshot.dayId)
        assertTrue(appRepo.getApp("com.instagram.android")!!.isBlocked)

        // User advances system clock by 2 days and changes timezone
        deviceClock.systemUtcMillis = utcMillis(2026, 11, 24, 20, 0)
        deviceClock.zoneId = ZoneId.of("Asia/Tokyo")
        deviceClock.elapsedMillis += 5_000L

        val result = coordinator.checkAndPerformDailyReset(
            attemptNtpSync = false,
            afterSystemTimeChange = true
        )

        assertTrue(result is DailyResetResult.NoResetNeeded)
        val app = appRepo.getApp("com.instagram.android")!!
        assertEquals(15 * 60_000L, app.usedMillisToday)
        assertTrue(app.isBlocked)
    }

    @Test
    fun `real midnight resets usage, extra time, ignore flag, and warning flag once`() = runTest {
        val startUtc = utcMillis(2026, 11, 22, 23, 55)
        val deviceClock = FakeDeviceClock(elapsedMillis = 50_000L, systemUtcMillis = startUtc, zoneId = mexicoZone)
        val appRepo = AppLimitRepository(InMemoryTrackedAppDao())
        val trustedClock = TrustedClock(
            deviceClock = deviceClock,
            stateRepository = TrustedClockStateRepository(InMemoryTrustedClockDao()),
            ntpTimeProvider = FakeNtpTimeProvider(null)
        )
        val coordinator = DailyResetCoordinator(trustedClock, appRepo)

        val day1 = trustedClock.now().dayId
        appRepo.addOrUpdateApp("com.google.android.youtube", 20, day1, startUtc)
        appRepo.addUsageMillis("com.google.android.youtube", 20 * 60_000L, day1)
        appRepo.addExtraMinutes("com.google.android.youtube", 10, day1)
        appRepo.ignoreForToday("com.google.android.youtube", day1)
        appRepo.markWarningSent("com.google.android.youtube", day1)

        // 10 real minutes pass -> 00:05 on Nov 23
        deviceClock.advanceRealTime(10 * 60_000L)
        val firstCheck = coordinator.checkAndPerformDailyReset()

        assertTrue(firstCheck is DailyResetResult.ResetPerformed)
        val performed = firstCheck as DailyResetResult.ResetPerformed
        assertEquals(day1, performed.previousDayId)
        assertEquals(day1 + 1, performed.newDayId)

        val resetApp = appRepo.getApp("com.google.android.youtube")!!
        assertEquals(0L, resetApp.usedMillisToday)
        assertEquals(0L, resetApp.extraMillisToday)
        assertFalse(resetApp.ignoredToday)
        assertFalse(resetApp.warningSentToday)
        assertEquals(AppLimitStatus.ACTIVE, resetApp.status)

        // Subsequent check on the same day does not reset again
        appRepo.addUsageMillis("com.google.android.youtube", 60_000L, day1 + 1)
        val secondCheck = coordinator.checkAndPerformDailyReset()
        assertTrue(secondCheck is DailyResetResult.NoResetNeeded)
        assertEquals(60_000L, appRepo.getApp("com.google.android.youtube")!!.usedMillisToday)
    }

    @Test
    fun `offline cheat reboot does not reset until NTP confirms a genuine new day`() = runTest {
        val startUtc = utcMillis(2026, 11, 22, 22, 0)
        val nextDayUtc = utcMillis(2026, 11, 23, 9, 0)
        val deviceClock = FakeDeviceClock(elapsedMillis = 500_000L, systemUtcMillis = startUtc, zoneId = mexicoZone)
        val ntpProvider = FakeNtpTimeProvider(null)
        val appRepo = AppLimitRepository(InMemoryTrackedAppDao())
        val trustedClock = TrustedClock(
            deviceClock = deviceClock,
            stateRepository = TrustedClockStateRepository(InMemoryTrustedClockDao()),
            ntpTimeProvider = ntpProvider
        )
        val coordinator = DailyResetCoordinator(trustedClock, appRepo)

        val day1 = trustedClock.now().dayId
        appRepo.addOrUpdateApp("com.zhiliaoapp.musically", 10, day1, startUtc)
        appRepo.addUsageMillis("com.zhiliaoapp.musically", 10 * 60_000L, day1)

        // Power off, jump system clock to tomorrow, boot offline
        deviceClock.simulateReboot(newElapsedMillis = 10_000L, newSystemUtcMillis = nextDayUtc)
        val offlineResult = coordinator.checkAndPerformDailyReset(attemptNtpSync = true)
        assertTrue(offlineResult is DailyResetResult.NoResetNeeded)
        assertTrue(appRepo.getApp("com.zhiliaoapp.musically")!!.isBlocked)

        // Later, NTP actually confirms it is Nov 23 -> legitimate reset occurs immediately
        ntpProvider.nextUtcMillis = nextDayUtc
        val onlineResult = coordinator.checkAndPerformDailyReset(attemptNtpSync = true)
        assertTrue(onlineResult is DailyResetResult.ResetPerformed)
        assertFalse(appRepo.getApp("com.zhiliaoapp.musically")!!.isBlocked)
        assertEquals(0L, appRepo.getApp("com.zhiliaoapp.musically")!!.usedMillisToday)
    }
}
