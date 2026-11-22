package com.lograre.app

import android.app.Application
import com.lograre.app.data.local.LograreDatabase
import com.lograre.app.data.network.SntpClient
import com.lograre.app.data.repository.AppLimitRepository
import com.lograre.app.data.repository.TrustedClockStateRepository
import com.lograre.app.data.security.KeystorePinStorage
import com.lograre.app.data.security.PinRepository
import com.lograre.app.domain.clock.AndroidDeviceClock
import com.lograre.app.domain.clock.DailyResetCoordinator
import com.lograre.app.domain.clock.DeviceClock
import com.lograre.app.domain.clock.NtpTimeProvider
import com.lograre.app.domain.clock.TrustedClock
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
}

class LograreApplication : Application() {
    lateinit var container: LograreContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = LograreContainer(this)
        container.applicationScope.launch {
            container.dailyResetCoordinator.checkAndPerformDailyReset(attemptNtpSync = true)
        }
    }
}
