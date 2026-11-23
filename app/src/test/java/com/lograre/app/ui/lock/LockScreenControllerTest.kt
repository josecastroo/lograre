package com.lograre.app.ui.lock

import com.lograre.app.data.repository.AppLimitRepository
import com.lograre.app.data.repository.TrustedClockStateRepository
import com.lograre.app.data.security.PinHasher
import com.lograre.app.data.security.PinRepository
import com.lograre.app.data.security.PinStorage
import com.lograre.app.domain.clock.DailyResetCoordinator
import com.lograre.app.domain.clock.FakeDeviceClock
import com.lograre.app.domain.clock.FakeNtpTimeProvider
import com.lograre.app.domain.clock.InMemoryTrackedAppDao
import com.lograre.app.domain.clock.InMemoryTrustedClockDao
import com.lograre.app.domain.clock.TrustedClock
import com.lograre.app.domain.model.AppLimitStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LockScreenControllerTest {

    private class InMemoryPinStorage : PinStorage {
        private var record: String? = null
        override fun readRecord(): String? = record
        override fun writeRecord(serializedRecord: String) {
            record = serializedRecord
        }
        override fun clear() {
            record = null
        }
    }

    private class Harness(configurePin: String? = "2580") {
        val startUtc: Long = ZonedDateTime.of(
            2026, 11, 22, 18, 0, 0, 0, ZoneId.of("America/Mexico_City")
        ).toInstant().toEpochMilli()

        val deviceClock = FakeDeviceClock(
            elapsedMillis = 100_000L,
            systemUtcMillis = startUtc,
            zoneId = ZoneId.of("America/Mexico_City")
        )
        val appRepo = AppLimitRepository(InMemoryTrackedAppDao())
        val trustedClock = TrustedClock(
            deviceClock = deviceClock,
            stateRepository = TrustedClockStateRepository(InMemoryTrustedClockDao()),
            ntpTimeProvider = FakeNtpTimeProvider(null)
        )
        val dailyResetCoordinator = DailyResetCoordinator(trustedClock, appRepo)
        val pinRepo = PinRepository(
            storage = InMemoryPinStorage(),
            hasher = PinHasher(iterations = 2_000)
        ).also { repo ->
            if (configurePin != null) {
                repo.setupPin(configurePin, configurePin)
            }
        }

        fun createController(
            packageName: String = "com.instagram.android",
            appLabel: String = "Instagram"
        ): LockScreenController = LockScreenController(
            packageName = packageName,
            appLabel = appLabel,
            pinRepository = pinRepo,
            appLimitRepository = appRepo,
            dailyResetCoordinator = dailyResetCoordinator
        )
    }

    @Test
    fun `starts in PIN_NOT_CONFIGURED stage when no pin is set`() {
        val h = Harness(configurePin = null)
        val controller = h.createController()

        assertEquals(LockScreenStage.PIN_NOT_CONFIGURED, controller.uiState.value.stage)
    }

    @Test
    fun `wrong pin clears input, reports error, and remains locked`() {
        val h = Harness(configurePin = "2580")
        val controller = h.createController()

        "1234".forEach { controller.onDigitPressed(it) }
        assertEquals("1234", controller.uiState.value.pinInput)

        assertFalse(controller.submitPin())
        val state = controller.uiState.value
        assertEquals(LockScreenStage.PIN_ENTRY, state.stage)
        assertEquals("", state.pinInput)
        assertNotNull(state.pinError)
        assertNull(state.outcome)
    }

    @Test
    fun `correct pin unlocks options and preset extra minutes unblocks app`() = runTest {
        val h = Harness(configurePin = "2580")
        val dayId = h.trustedClock.now().dayId
        val pkg = "com.instagram.android"
        h.appRepo.addOrUpdateApp(pkg, dailyLimitMinutes = 10, currentDayId = dayId, nowMillis = h.startUtc)
        h.appRepo.addUsageMillis(pkg, 10 * 60_000L, dayId)
        assertTrue(h.appRepo.getApp(pkg)!!.isBlocked)

        val controller = h.createController(pkg, "Instagram")
        "2589".forEach { controller.onDigitPressed(it) }
        controller.onBackspacePressed()
        controller.onDigitPressed('0')

        assertTrue(controller.submitPin())
        assertEquals(LockScreenStage.UNLOCKED_OPTIONS, controller.uiState.value.stage)

        assertTrue(controller.grantExtraMinutes(15))
        assertEquals(LockScreenOutcome.UnlockedReturnToApp, controller.uiState.value.outcome)

        val updatedApp = h.appRepo.getApp(pkg)!!
        assertEquals(15 * 60_000L, updatedApp.extraMillisToday)
        assertFalse(updatedApp.isBlocked)
        assertEquals(AppLimitStatus.ACTIVE, updatedApp.status)
    }

    @Test
    fun `custom extra minutes rejects zero or empty and applies positive value`() = runTest {
        val h = Harness(configurePin = "43210")
        val dayId = h.trustedClock.now().dayId
        val pkg = "com.google.android.youtube"
        h.appRepo.addOrUpdateApp(pkg, dailyLimitMinutes = 0, currentDayId = dayId, nowMillis = h.startUtc)

        val controller = h.createController(pkg, "YouTube")
        "43210".forEach { controller.onDigitPressed(it) }
        assertTrue(controller.submitPin())

        controller.onCustomMinutesChanged("0")
        assertFalse(controller.grantCustomExtraMinutes())
        assertNotNull(controller.uiState.value.customMinutesError)
        assertNull(controller.uiState.value.outcome)

        controller.onCustomMinutesChanged("12abc")
        assertEquals("12", controller.uiState.value.customMinutesInput)
        assertTrue(controller.grantCustomExtraMinutes())
        assertEquals(12 * 60_000L, h.appRepo.getApp(pkg)!!.extraMillisToday)
        assertEquals(LockScreenOutcome.UnlockedReturnToApp, controller.uiState.value.outcome)
    }

    @Test
    fun `ignore for today marks app ignored and returns to app`() = runTest {
        val h = Harness(configurePin = "9999")
        val dayId = h.trustedClock.now().dayId
        val pkg = "com.zhiliaoapp.musically"
        h.appRepo.addOrUpdateApp(pkg, dailyLimitMinutes = 5, currentDayId = dayId, nowMillis = h.startUtc)
        h.appRepo.addUsageMillis(pkg, 5 * 60_000L, dayId)

        val controller = h.createController(pkg, "TikTok")
        "9999".forEach { controller.onDigitPressed(it) }
        assertTrue(controller.submitPin())

        assertTrue(controller.ignoreForToday())
        assertEquals(LockScreenOutcome.UnlockedReturnToApp, controller.uiState.value.outcome)
        assertEquals(AppLimitStatus.IGNORED_TODAY, h.appRepo.getApp(pkg)!!.status)
    }

    @Test
    fun `cannot grant extra time or ignore before pin verification and exit goes to launcher`() = runTest {
        val h = Harness(configurePin = "2580")
        val dayId = h.trustedClock.now().dayId
        val pkg = "com.instagram.android"
        h.appRepo.addOrUpdateApp(pkg, dailyLimitMinutes = 0, currentDayId = dayId, nowMillis = h.startUtc)

        val controller = h.createController(pkg, "Instagram")
        assertFalse(controller.grantExtraMinutes(5))
        assertFalse(controller.ignoreForToday())
        assertTrue(h.appRepo.getApp(pkg)!!.isBlocked)

        controller.onExitRequested()
        assertEquals(LockScreenOutcome.ExitToLauncher, controller.uiState.value.outcome)
    }
}
