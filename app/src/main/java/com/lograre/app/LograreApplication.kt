package com.lograre.app

import android.app.Application
import com.lograre.app.data.local.LograreDatabase
import com.lograre.app.data.repository.AppLimitRepository
import com.lograre.app.data.repository.TrustedClockStateRepository
import com.lograre.app.data.security.KeystorePinStorage
import com.lograre.app.data.security.PinRepository

class LograreContainer(application: Application) {
    val database: LograreDatabase = LograreDatabase.create(application)
    val appLimitRepository: AppLimitRepository = AppLimitRepository(database.trackedAppDao())
    val trustedClockStateRepository: TrustedClockStateRepository =
        TrustedClockStateRepository(database.trustedClockDao())
    val pinRepository: PinRepository = PinRepository(KeystorePinStorage(application))
}

class LograreApplication : Application() {
    lateinit var container: LograreContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = LograreContainer(this)
    }
}
