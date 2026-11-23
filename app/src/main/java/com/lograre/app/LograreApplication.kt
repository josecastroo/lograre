package com.lograre.app

import android.app.Application
import com.lograre.app.data.apps.AndroidInstalledAppsProvider
import com.lograre.app.data.apps.InstalledAppsProvider
import com.lograre.app.data.local.LograreDatabase
import com.lograre.app.data.network.SntpClient
import com.lograre.app.data.repository.AppLimitRepository
import com.lograre.app.data.repository.TrustedClockStateRepository
import com.lograre.app.data.security.KeystorePinStorage
import com.lograre.app.data.security.PinRepository
import com.lograre.app.domain.boot.BootRestoreHandler
import com.lograre.app.domain.clock.AndroidDeviceClock
import com.lograre.app.domain.clock.DailyResetCoordinator
import com.lograre.app.domain.clock.DeviceClock
import com.lograre.app.domain.clock.NtpTimeProvider
import com.lograre.app.domain.clock.TrustedClock
import com.lograre.app.domain.tracker.AppBlockLauncher
import com.lograre.app.domain.tracker.FiveMinuteWarningNotifier
import com.lograre.app.domain.tracker.ForegroundUsageTracker
import com.lograre.app.domain.tracker.SystemPackageProvider
import com.lograre.app.notification.AndroidFiveMinuteWarningNotifier
import com.lograre.app.service.AndroidSystemPackageProvider
import com.lograre.app.ui.lock.ActivityAppBlockLauncher
import com.lograre.app.ui.theme.SharedPrefsThemeStorage
import com.lograre.app.ui.theme.ThemePreferenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LograreContainer(application: Application) {
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: LograreDatabase = LograreDatabase.create(application)
    val appLimitRepository: AppLimitRepository = AppLimitRepository(database.trackedAppDao())
    val trustedClockStateRepository: TrustedClockStateRepository =
        TrustedClockStateRepository(database.trustedClockDao())
    val pinRepository: PinRepository = PinRepository(KeystorePinStorage(application))

    val deviceClock: DeviceClock = AndroidDeviceClock(application)
    val ntpTimeProvider: NtpTimeProvider = SntpClient()
    val trustedClock: TrustedClock = TrustedClock(
        deviceClock = deviceClock,
        stateRepository = trustedClockStateRepository,
        ntpTimeProvider = ntpTimeProvider
    )
    val dailyResetCoordinator: DailyResetCoordinator = DailyResetCoordinator(
        trustedClock = trustedClock,
        appLimitRepository = appLimitRepository
    )

    val systemPackageProvider: SystemPackageProvider = AndroidSystemPackageProvider(application)
    val installedAppsProvider: InstalledAppsProvider = AndroidInstalledAppsProvider(
        context = application,
        systemPackageProvider = systemPackageProvider
    )
    val foregroundUsageTracker: ForegroundUsageTracker = ForegroundUsageTracker(
        deviceClock = deviceClock,
        appLimitRepository = appLimitRepository,
        dailyResetCoordinator = dailyResetCoordinator,
        protectedPackagesProvider = { systemPackageProvider.getProtectedPackages() }
    )

    val bootRestoreHandler: BootRestoreHandler = BootRestoreHandler(
        dailyResetCoordinator = dailyResetCoordinator,
        foregroundUsageTracker = foregroundUsageTracker
    )

    val appBlockLauncher: AppBlockLauncher = ActivityAppBlockLauncher(application)

    val themePreferenceRepository: ThemePreferenceRepository =
        ThemePreferenceRepository(SharedPrefsThemeStorage(application))

    private val androidWarningNotifier = AndroidFiveMinuteWarningNotifier(application)
    val warningNotifier: FiveMinuteWarningNotifier = androidWarningNotifier

    fun initialize() {
        androidWarningNotifier.ensureNotificationChannel()
    }
}

class LograreApplication : Application() {
    lateinit var container: LograreContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = LograreContainer(this)
        container.initialize()
        container.applicationScope.launch {
            container.dailyResetCoordinator.checkAndPerformDailyReset(attemptNtpSync = true)
        }
    }
}
