package com.lograre.app.domain.clock

import com.lograre.app.data.repository.TrustedClockStateRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TrustedClockTest {

    private val mexicoZone = ZoneId.of("America/Mexico_City")

    private fun utcMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0,
        zone: ZoneId = mexicoZone
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, second, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `manual forward and backward system time changes are ignored while running`() = runTest {
        val startUtc = utcMillis(2026, 11, 22, 18, 0)
        val deviceClock = FakeDeviceClock(elapsedMillis = 100_000L, systemUtcMillis = startUtc, zoneId = mexicoZone)
        val trustedClock = TrustedClock(
            deviceClock = deviceClock,
            stateRepository = TrustedClockStateRepository(InMemoryTrustedClockDao()),
            ntpTimeProvider = FakeNtpTimeProvider(null)
        )

        val initial = trustedClock.now()
        assertEquals(startUtc, initial.utcMillis)
        assertFalse(initial.isNewDayPending)

        // User sets system clock 3 days into the future
        deviceClock.systemUtcMillis = utcMillis(2026, 11, 25, 18, 0)
        deviceClock.elapsedMillis += 10_000L
        val afterForward = trustedClock.recalculateAfterSystemTimeChange()
        assertEquals(startUtc + 10_000L, afterForward.utcMillis)
        assertFalse(afterForward.isNewDayPending)

        // User sets system clock 1 year into the past
        deviceClock.systemUtcMillis = utcMillis(2025, 11, 22, 18, 0)
        deviceClock.elapsedMillis += 5_000L
        val afterBackward = trustedClock.recalculateAfterSystemTimeChange()
        assertEquals(startUtc + 15_000L, afterBackward.utcMillis)
        assertFalse(afterBackward.isNewDayPending)
    }

    @Test
    fun `changing device timezone cannot trigger fake midnight`() = runTest {
        val startUtc = utcMillis(2026, 11, 22, 22, 0) // 22:00 in UTC-6
        val deviceClock = FakeDeviceClock(elapsedMillis = 50_000L, systemUtcMillis = startUtc, zoneId = mexicoZone)
        val trustedClock = TrustedClock(
            deviceClock = deviceClock,
            stateRepository = TrustedClockStateRepository(InMemoryTrustedClockDao()),
            ntpTimeProvider = FakeNtpTimeProvider(null)
        )

        val initial = trustedClock.now()
        assertFalse(initial.isNewDayPending)

        // User switches timezone to UTC+14 (would already be next day there)
        deviceClock.zoneId = ZoneId.of("Pacific/Kiritimati")
        deviceClock.elapsedMillis += 30_000L
        val afterTimezoneChange = trustedClock.recalculateAfterSystemTimeChange()

        assertEquals(mexicoZone, afterTimezoneChange.zoneId)
        assertEquals(initial.dayId, afterTimezoneChange.dayId)
        assertFalse(afterTimezoneChange.isNewDayPending)
    }

    @Test
    fun `monotonic elapsed time crossing midnight marks new day pending even offline`() = runTest {
        val startUtc = utcMillis(2026, 11, 22, 23, 50)
        val deviceClock = FakeDeviceClock(elapsedMillis = 10_000L, systemUtcMillis = startUtc, zoneId = mexicoZone)
        val trustedClock = TrustedClock(
            deviceClock = deviceClock,
            stateRepository = TrustedClockStateRepository(InMemoryTrustedClockDao()),
            ntpTimeProvider = FakeNtpTimeProvider(null)
        )

        val initial = trustedClock.now()
        assertFalse(initial.isNewDayPending)

        // Advance 15 real minutes -> 00:05 next day
        deviceClock.advanceRealTime(15 * 60_000L)
        val afterMidnight = trustedClock.now()

        assertTrue(afterMidnight.isNewDayPending)
        assertEquals(initial.dayId + 1, afterMidnight.dayId)
    }

    @Test
    fun `reboot with system clock rolled backwards never moves trusted time backwards`() = runTest {
        val startUtc = utcMillis(2026, 11, 22, 16, 0)
        val deviceClock = FakeDeviceClock(elapsedMillis = 500_000L, systemUtcMillis = startUtc, zoneId = mexicoZone)
        val trustedClock = TrustedClock(
            deviceClock = deviceClock,
            stateRepository = TrustedClockStateRepository(InMemoryTrustedClockDao()),
            ntpTimeProvider = FakeNtpTimeProvider(null)
        )

        trustedClock.now()

        // Device reboots with system clock rolled back 5 hours
        deviceClock.simulateReboot(
            newElapsedMillis = 20_000L,
            newSystemUtcMillis = utcMillis(2026, 11, 22, 11, 0)
        )
        val afterReboot = trustedClock.now()

        assertTrue(afterReboot.rebootDetected)
        assertEquals(startUtc, afterReboot.utcMillis)
        assertFalse(afterReboot.isNewDayPending)

        // Monotonic clock continues advancing cleanly from lastSavedUtc
        deviceClock.advanceRealTime(60_000L)
        val later = trustedClock.now()
        assertEquals(startUtc + 60_000L, later.utcMillis)
    }

    @Test
    fun `offline reboot jumping across midnight is rejected as suspicious`() = runTest {
        val startUtc = utcMillis(2026, 11, 22, 23, 0)
        val deviceClock = FakeDeviceClock(elapsedMillis = 400_000L, systemUtcMillis = startUtc, zoneId = mexicoZone)
        val trustedClock = TrustedClock(
            deviceClock = deviceClock,
            stateRepository = TrustedClockStateRepository(InMemoryTrustedClockDao()),
            ntpTimeProvider = FakeNtpTimeProvider(null)
        )

        val before = trustedClock.now()

        // User powers off phone at 23:00, jumps clock to 00:30 next day, boots offline
        deviceClock.simulateReboot(
            newElapsedMillis = 15_000L,
            newSystemUtcMillis = utcMillis(2026, 11, 23, 0, 30)
        )
        val afterCheatReboot = trustedClock.now()

        assertTrue(afterCheatReboot.rebootDetected)
        assertTrue(afterCheatReboot.suspiciousJumpRejected)
        assertEquals(startUtc, afterCheatReboot.utcMillis)
        assertEquals(before.dayId, afterCheatReboot.dayId)
        assertFalse(afterCheatReboot.isNewDayPending)
    }

    @Test
    fun `offline reboot jumping more than 2 hours same day is rejected as suspicious`() = runTest {
        val startUtc = utcMillis(2026, 11, 22, 10, 0)
        val deviceClock = FakeDeviceClock(elapsedMillis = 300_000L, systemUtcMillis = startUtc, zoneId = mexicoZone)
        val trustedClock = TrustedClock(
            deviceClock = deviceClock,
            stateRepository = TrustedClockStateRepository(InMemoryTrustedClockDao()),
            ntpTimeProvider = FakeNtpTimeProvider(null)
        )

        trustedClock.now()

        // Reboot jumping 4 hours forward offline on the same day
        deviceClock.simulateReboot(
            newElapsedMillis = 10_000L,
            newSystemUtcMillis = utcMillis(2026, 11, 22, 14, 0)
        )
        val afterJump = trustedClock.now()

        assertTrue(afterJump.rebootDetected)
        assertTrue(afterJump.suspiciousJumpRejected)
        assertEquals(startUtc, afterJump.utcMillis)
    }

    @Test
    fun `short same-day offline reboot is accepted smoothly`() = runTest {
        val startUtc = utcMillis(2026, 11, 22, 10, 0)
        val rebootedUtc = utcMillis(2026, 11, 22, 10, 15)
        val deviceClock = FakeDeviceClock(elapsedMillis = 300_000L, systemUtcMillis = startUtc, zoneId = mexicoZone)
        val trustedClock = TrustedClock(
            deviceClock = deviceClock,
            stateRepository = TrustedClockStateRepository(InMemoryTrustedClockDao()),
            ntpTimeProvider = FakeNtpTimeProvider(null)
        )

        trustedClock.now()

        deviceClock.simulateReboot(newElapsedMillis = 15_000L, newSystemUtcMillis = rebootedUtc)
        val afterShortReboot = trustedClock.now()

        assertTrue(afterShortReboot.rebootDetected)
        assertFalse(afterShortReboot.suspiciousJumpRejected)
        assertEquals(rebootedUtc, afterShortReboot.utcMillis)
    }

    @Test
    fun `ntp sync after legitimate overnight power-off re-anchors and reveals new day`() = runTest {
        val nightUtc = utcMillis(2026, 11, 22, 23, 0)
        val morningUtc = utcMillis(2026, 11, 23, 8, 0)
        val deviceClock = FakeDeviceClock(elapsedMillis = 500_000L, systemUtcMillis = nightUtc, zoneId = mexicoZone)
        val ntpProvider = FakeNtpTimeProvider(null)
        val trustedClock = TrustedClock(
            deviceClock = deviceClock,
            stateRepository = TrustedClockStateRepository(InMemoryTrustedClockDao()),
            ntpTimeProvider = ntpProvider
        )

        val nightSnapshot = trustedClock.now()

        // Phone turns back on next morning initially offline -> rejected until NTP confirms
        deviceClock.simulateReboot(newElapsedMillis = 20_000L, newSystemUtcMillis = morningUtc)
        val offlineMorning = trustedClock.now()
        assertTrue(offlineMorning.suspiciousJumpRejected)
        assertFalse(offlineMorning.isNewDayPending)

        // Internet becomes available -> NTP confirms it really is morning of Nov 23
        ntpProvider.nextUtcMillis = morningUtc
        val syncResult = trustedClock.syncWithNtp()
        assertTrue(syncResult is NtpSyncResult.Synced)
        val synced = (syncResult as NtpSyncResult.Synced).snapshot
        assertEquals(morningUtc, synced.utcMillis)
        assertEquals(nightSnapshot.dayId + 1, synced.dayId)
        assertTrue(synced.isNewDayPending)
    }

    @Test
    fun `ntp sync never moves time backwards and rejects implausible timestamps`() = runTest {
        val startUtc = utcMillis(2026, 11, 22, 12, 0)
        val deviceClock = FakeDeviceClock(elapsedMillis = 100_000L, systemUtcMillis = startUtc, zoneId = mexicoZone)
        val ntpProvider = FakeNtpTimeProvider(startUtc - 60_000L) // 1 min behind
        val trustedClock = TrustedClock(
            deviceClock = deviceClock,
            stateRepository = TrustedClockStateRepository(InMemoryTrustedClockDao()),
            ntpTimeProvider = ntpProvider
        )

        trustedClock.now()

        val result = trustedClock.syncWithNtp()
        assertTrue(result is NtpSyncResult.Synced)
        assertEquals(startUtc, (result as NtpSyncResult.Synced).snapshot.utcMillis)

        // Implausible pre-2025 NTP response is rejected
        ntpProvider.nextUtcMillis = 1_000_000_000_000L
        assertTrue(trustedClock.syncWithNtp() is NtpSyncResult.RejectedSuspiciousNtp)

        // Implausible >1 year jump after an established NTP anchor is rejected
        ntpProvider.nextUtcMillis = startUtc + 400L * 24 * 60 * 60 * 1000L
        assertTrue(trustedClock.syncWithNtp() is NtpSyncResult.RejectedSuspiciousNtp)
    }
}
