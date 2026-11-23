package com.lograre.app.domain.boot

import com.lograre.app.domain.clock.DailyResetCoordinator
import com.lograre.app.domain.clock.DailyResetResult
import com.lograre.app.domain.tracker.ForegroundUsageTracker

/**
 * Restores persisted clock and daily limit state after device reboot or app update.
 */
class BootRestoreHandler(
    private val dailyResetCoordinator: DailyResetCoordinator,
    private val foregroundUsageTracker: ForegroundUsageTracker
) {
    suspend fun onDeviceBootOrRestore(): DailyResetResult {
        foregroundUsageTracker.onServiceStopped()
        return dailyResetCoordinator.checkAndPerformDailyReset(attemptNtpSync = true)
    }
}
