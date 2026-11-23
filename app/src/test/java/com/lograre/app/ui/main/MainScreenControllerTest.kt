package com.lograre.app.ui.main

import com.lograre.app.data.apps.InstalledAppInfo
import com.lograre.app.data.apps.InstalledAppsProvider
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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MainScreenControllerTest {

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

    private class FakeInstalledAppsProvider(
        private val apps: List<InstalledAppInfo>
    ) : InstalledAppsProvider {
        override suspend fun getLaunchableApps(): List<InstalledAppInfo> = apps
    }

    private class Harness(
        initialPin: String? = null,
        installedApps: List<InstalledAppInfo> = listOf(
            InstalledAppInfo("com.instagram.android", "Instagram"),
            InstalledAppInfo("com.zhiliaoapp.musically", "TikTok"),
            InstalledAppInfo("com.google.android.youtube", "YouTube")
        )
    ) {
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
            if (initialPin != null) {
                repo.setupPin(initialPin, initialPin)
            }
        }
        val controller = MainScreenController(
            pinRepository = pinRepo,
            appLimitRepository = appRepo,
            dailyResetCoordinator = dailyResetCoordinator,
            installedAppsProvider = FakeInstalledAppsProvider(installedApps)
        )

        fun typePin(digits: String) {
            digits.forEach { controller.onPinDigitPressed(it) }
        }
    }

    @Test
    fun `first run starts at INITIAL_PIN_SETUP and completes two-step variable PIN setup`() {
        val h = Harness(initialPin = null)
        assertEquals(MainDestination.INITIAL_PIN_SETUP, h.controller.uiState.value.destination)
        assertEquals(PinSetupStep.ENTER_NEW_PIN, h.controller.uiState.value.pinSetupStep)

        // Variable 6-digit PIN
        h.typePin("847291")
        assertTrue(h.controller.submitInitialPinStep())
        assertEquals(PinSetupStep.CONFIRM_NEW_PIN, h.controller.uiState.value.pinSetupStep)
        assertEquals("", h.controller.uiState.value.pinInput)

        h.typePin("847291")
        assertTrue(h.controller.submitInitialPinStep())
        assertEquals(MainDestination.HOME, h.controller.uiState.value.destination)
        assertTrue(h.pinRepo.verifyPin("847291"))
    }

    @Test
    fun `initial PIN setup rejects short PIN and mismatched confirmation`() {
        val h = Harness(initialPin = null)

        h.typePin("123")
        assertFalse(h.controller.submitInitialPinStep())
        assertNotNull(h.controller.uiState.value.pinError)
        assertEquals(PinSetupStep.ENTER_NEW_PIN, h.controller.uiState.value.pinSetupStep)

        h.controller.onPinClearPressed()
        h.typePin("1234")
        assertTrue(h.controller.submitInitialPinStep())

        h.typePin("1235")
        assertFalse(h.controller.submitInitialPinStep())
        assertNotNull(h.controller.uiState.value.pinError)
        assertFalse(h.pinRepo.isPinConfigured())
    }

    @Test
    fun `adding new app saves directly without PIN and allows 0 minute limit`() = runTest {
        val h = Harness(initialPin = "2468")
        assertEquals(MainDestination.HOME, h.controller.uiState.value.destination)

        h.controller.openAppPicker()
        assertEquals(MainDestination.APP_PICKER, h.controller.uiState.value.destination)
        assertEquals(3, h.controller.uiState.value.availableAppsForPicker.size)

        val instagram = InstalledAppInfo("com.instagram.android", "Instagram")
        h.controller.openAddAppLimitDialog(instagram)
        assertEquals("", h.controller.uiState.value.limitEditor!!.minutesInput)
        h.controller.onLimitMinutesInputChanged("0")
        assertTrue(h.controller.confirmLimitEditor())

        assertNull(h.controller.uiState.value.pendingProtectedAction)
        val saved = h.appRepo.getApp(instagram.packageName)!!
        assertEquals(0, saved.dailyLimitMinutes)
        assertTrue(saved.isBlocked)
        assertEquals(MainDestination.HOME, h.controller.uiState.value.destination)
    }

    @Test
    fun `editing limit and removing app both require valid PIN`() = runTest {
        val h = Harness(initialPin = "5555")
        val dayId = h.trustedClock.now().dayId
        val youtube = "com.google.android.youtube"
        h.appRepo.addOrUpdateApp(youtube, dailyLimitMinutes = 20, currentDayId = dayId, nowMillis = h.startUtc)
        h.appRepo.addUsageMillis(youtube, 5 * 60_000L, dayId)

        val currentApp = h.appRepo.getApp(youtube)!!
        h.controller.openEditAppLimitDialog(currentApp)
        h.controller.onLimitMinutesInputChanged("45")
        assertTrue(h.controller.confirmLimitEditor())
        assertTrue(h.controller.uiState.value.pendingProtectedAction is PendingProtectedAction.EditAppLimit)

        h.typePin("5555")
        assertTrue(h.controller.submitProtectedActionPin())
        val edited = h.appRepo.getApp(youtube)!!
        assertEquals(45, edited.dailyLimitMinutes)
        assertEquals(5 * 60_000L, edited.usedMillisToday)

        // Request removal
        h.controller.requestRemoveApp(edited)
        h.typePin("1111")
        assertFalse(h.controller.submitProtectedActionPin())
        assertNotNull(h.appRepo.getApp(youtube))

        h.typePin("5555")
        assertTrue(h.controller.submitProtectedActionPin())
        assertNull(h.appRepo.getApp(youtube))
    }

    @Test
    fun `change PIN flow verifies current PIN and updates to new PIN`() {
        val h = Harness(initialPin = "1234")
        h.controller.openChangePin()
        assertEquals(MainDestination.CHANGE_PIN, h.controller.uiState.value.destination)
        assertEquals(ChangePinStep.VERIFY_CURRENT_PIN, h.controller.uiState.value.changePinStep)

        // Wrong current PIN rejected
        h.typePin("9999")
        assertFalse(h.controller.submitChangePinStep())
        assertEquals(ChangePinStep.VERIFY_CURRENT_PIN, h.controller.uiState.value.changePinStep)

        // Correct current PIN advances to new PIN
        h.typePin("1234")
        assertTrue(h.controller.submitChangePinStep())
        assertEquals(ChangePinStep.ENTER_NEW_PIN, h.controller.uiState.value.changePinStep)

        // Enter & confirm new 8-digit PIN
        h.typePin("87654321")
        assertTrue(h.controller.submitChangePinStep())
        assertEquals(ChangePinStep.CONFIRM_NEW_PIN, h.controller.uiState.value.changePinStep)

        h.typePin("87654321")
        assertTrue(h.controller.submitChangePinStep())
        assertEquals(MainDestination.HOME, h.controller.uiState.value.destination)
        assertFalse(h.pinRepo.verifyPin("1234"))
        assertTrue(h.pinRepo.verifyPin("87654321"))
    }

    @Test
    fun `app picker excludes already tracked apps and filters by search query`() = runTest {
        val h = Harness(initialPin = "1234")
        val dayId = h.trustedClock.now().dayId
        h.appRepo.addOrUpdateApp("com.instagram.android", 10, dayId, h.startUtc)
        h.controller.updateTrackedApps(h.appRepo.getAllApps())

        h.controller.openAppPicker()
        assertEquals(2, h.controller.uiState.value.availableAppsForPicker.size)

        h.controller.onAppSearchQueryChanged("you")
        val filtered = h.controller.uiState.value.availableAppsForPicker
        assertEquals(1, filtered.size)
        assertEquals("com.google.android.youtube", filtered.first().packageName)
    }

    @Test
    fun `DurationFormatter formats zero, seconds, minutes and hours cleanly`() {
        assertEquals("0 min", DurationFormatter.formatMinutes(0))
        assertEquals("15 min", DurationFormatter.formatMinutes(15))
        assertEquals("1h", DurationFormatter.formatMinutes(60))
        assertEquals("1h 30m", DurationFormatter.formatMinutes(90))
        assertEquals("45s", DurationFormatter.formatMillis(45_000L))
        assertEquals("2 min", DurationFormatter.formatMillis(120_000L))
        assertEquals("2m 5s", DurationFormatter.formatMillis(125_000L))
    }
}
