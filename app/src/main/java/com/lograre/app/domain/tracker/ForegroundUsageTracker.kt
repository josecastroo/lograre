package com.lograre.app.domain.tracker

import com.lograre.app.data.repository.AppLimitRepository
import com.lograre.app.domain.clock.DailyResetCoordinator
import com.lograre.app.domain.clock.DailyResetResult
import com.lograre.app.domain.clock.DeviceClock
import com.lograre.app.domain.model.TrackedApp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface TrackerAction {
    data object None : TrackerAction
    data class BlockRequired(val packageName: String) : TrackerAction
    data class FiveMinuteWarning(
        val packageName: String,
        val remainingMillis: Long
    ) : TrackerAction
}

data class ActiveForegroundSession(
    val packageName: String,
    val lastTickElapsedMillis: Long
)

/**
 * Tracks active foreground usage strictly using monotonic [DeviceClock.elapsedRealtimeMillis].
 *
 * Rules:
 * - Only advances a tracked app's counter while that exact app is in the foreground and screen is on.
 * - Immediately triggers [TrackerAction.BlockRequired] for apps with limit == 0 (unless extra time
 *   was granted or ignored today) or once `usedMillisToday >= effectiveLimitMillis`.
 * - Stops counting and never blocks apps marked as `ignoredToday`.
 * - Emits [TrackerAction.FiveMinuteWarning] once per day when `remainingMillis <= 5 min`
 *   and `dailyLimitMinutes >= 5`.
 * - Checks [DailyResetCoordinator] before crediting time so crossing real midnight resets cleanly.
 */
class ForegroundUsageTracker(
    private val deviceClock: DeviceClock,
    private val appLimitRepository: AppLimitRepository,
    private val dailyResetCoordinator: DailyResetCoordinator,
    private val protectedPackagesProvider: () -> Set<String> = { ProtectedPackages.STATIC_CRITICAL_PACKAGES }
) {
    private val mutex = Mutex()
    private var activeSession: ActiveForegroundSession? = null

    suspend fun currentActivePackage(): String? = mutex.withLock {
        activeSession?.packageName
    }

    suspend fun hasActiveSession(): Boolean = mutex.withLock {
        activeSession != null
    }

    suspend fun onForegroundPackageChanged(packageName: String): TrackerAction = mutex.withLock {
        val previous = activeSession
        if (previous != null && previous.packageName == packageName) {
            return@withLock tickLocked(endSession = false)
        }

        // Flush previous session before transitioning.
        if (previous != null) {
            tickLocked(endSession = true)
        }

        if (packageName.isBlank() || packageName in protectedPackagesProvider()) {
            activeSession = null
            return@withLock TrackerAction.None
        }

        val resetResult = dailyResetCoordinator.checkAndPerformDailyReset()
        val currentDayId = currentDayIdFrom(resetResult)

        val app = appLimitRepository.getApp(packageName)
            ?: return@withLock TrackerAction.None

        if (app.ignoredToday) {
            activeSession = null
            return@withLock TrackerAction.None
        }

        if (app.isBlocked) {
            activeSession = null
            return@withLock TrackerAction.BlockRequired(packageName)
        }

        val nowElapsed = deviceClock.elapsedRealtimeMillis()
        activeSession = ActiveForegroundSession(
            packageName = packageName,
            lastTickElapsedMillis = nowElapsed
        )

        if (shouldEmitFiveMinuteWarning(app)) {
            appLimitRepository.markWarningSent(packageName, currentDayId)
            return@withLock TrackerAction.FiveMinuteWarning(packageName, app.remainingMillis)
        }

        TrackerAction.None
    }

    suspend fun tick(): TrackerAction = mutex.withLock {
        tickLocked(endSession = false)
    }

    suspend fun onScreenOff(): TrackerAction = mutex.withLock {
        tickLocked(endSession = true)
    }

    suspend fun onServiceStopped(): TrackerAction = mutex.withLock {
        tickLocked(endSession = true)
    }

    private suspend fun tickLocked(endSession: Boolean): TrackerAction {
        val session = activeSession ?: return TrackerAction.None
        val nowElapsed = deviceClock.elapsedRealtimeMillis()
        val deltaMillis = (nowElapsed - session.lastTickElapsedMillis).coerceAtLeast(0L)

        val resetResult = dailyResetCoordinator.checkAndPerformDailyReset()
        val currentDayId = currentDayIdFrom(resetResult)

        if (resetResult is DailyResetResult.ResetPerformed) {
            // Day rolled over at 00:00 while app was open: do not carry previous day's delta
            // into the new day; re-evaluate fresh limit state.
            val freshApp = appLimitRepository.getApp(session.packageName)
            if (freshApp == null || freshApp.ignoredToday) {
                activeSession = null
                return TrackerAction.None
            }
            if (freshApp.isBlocked) {
                activeSession = null
                return TrackerAction.BlockRequired(freshApp.packageName)
            }
            activeSession = if (endSession) null else session.copy(lastTickElapsedMillis = nowElapsed)
            return TrackerAction.None
        }

        val app = appLimitRepository.getApp(session.packageName)
        if (app == null || app.ignoredToday) {
            activeSession = null
            return TrackerAction.None
        }

        if (app.isBlocked) {
            activeSession = null
            return TrackerAction.BlockRequired(app.packageName)
        }

        val updatedApp = if (deltaMillis > 0L) {
            appLimitRepository.addUsageMillis(session.packageName, deltaMillis, currentDayId)
            app.copy(
                usedMillisToday = app.usedMillisToday + deltaMillis,
                lastUpdatedDayId = currentDayId
            )
        } else {
            app
        }

        if (updatedApp.isBlocked) {
            activeSession = null
            return TrackerAction.BlockRequired(updatedApp.packageName)
        }

        activeSession = if (endSession) null else session.copy(lastTickElapsedMillis = nowElapsed)

        if (shouldEmitFiveMinuteWarning(updatedApp)) {
            appLimitRepository.markWarningSent(updatedApp.packageName, currentDayId)
            return TrackerAction.FiveMinuteWarning(
                packageName = updatedApp.packageName,
                remainingMillis = updatedApp.remainingMillis
            )
        }

        return TrackerAction.None
    }

    private fun shouldEmitFiveMinuteWarning(app: TrackedApp): Boolean {
        if (app.ignoredToday || app.warningSentToday) return false
        if (app.dailyLimitMinutes < WARNING_THRESHOLD_MINUTES) return false
        return app.remainingMillis in 1L..WARNING_THRESHOLD_MILLIS
    }

    private fun currentDayIdFrom(result: DailyResetResult): Long = when (result) {
        is DailyResetResult.ResetPerformed -> result.newDayId
        is DailyResetResult.NoResetNeeded -> result.currentDayId
    }

    companion object {
        const val WARNING_THRESHOLD_MINUTES: Int = 5
        const val WARNING_THRESHOLD_MILLIS: Long = WARNING_THRESHOLD_MINUTES * TrackedApp.MILLIS_PER_MINUTE
    }
}
