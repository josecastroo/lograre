package com.lograre.app.domain.clock

import com.lograre.app.data.repository.AppLimitRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface DailyResetResult {
    data class ResetPerformed(
        val previousDayId: Long,
        val newDayId: Long,
        val snapshot: TrustedTimeSnapshot
    ) : DailyResetResult

    data class NoResetNeeded(
        val currentDayId: Long,
        val snapshot: TrustedTimeSnapshot
    ) : DailyResetResult
}

/**
 * Coordinates the 00:00 daily reset using [TrustedClock] instead of the device system clock.
 */
class DailyResetCoordinator(
    private val trustedClock: TrustedClock,
    private val appLimitRepository: AppLimitRepository
) {
    private val mutex = Mutex()

    suspend fun checkAndPerformDailyReset(
        attemptNtpSync: Boolean = false,
        afterSystemTimeChange: Boolean = false
    ): DailyResetResult = mutex.withLock {
        if (afterSystemTimeChange) {
            trustedClock.recalculateAfterSystemTimeChange()
        }
        if (attemptNtpSync) {
            trustedClock.syncWithNtp()
        }

        val snapshot = trustedClock.now()
        if (snapshot.isNewDayPending) {
            val previousDayId = snapshot.persistedDayId
            appLimitRepository.resetAllForNewDay(snapshot.dayId)
            trustedClock.markDayResetCompleted(snapshot.dayId)
            DailyResetResult.ResetPerformed(
                previousDayId = previousDayId,
                newDayId = snapshot.dayId,
                snapshot = snapshot.copy(persistedDayId = snapshot.dayId)
            )
        } else {
            DailyResetResult.NoResetNeeded(
                currentDayId = snapshot.persistedDayId,
                snapshot = snapshot
            )
        }
    }
}
