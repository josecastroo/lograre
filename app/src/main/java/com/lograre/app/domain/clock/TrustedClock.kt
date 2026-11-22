package com.lograre.app.domain.clock

import com.lograre.app.data.repository.TrustedClockStateRepository
import com.lograre.app.domain.model.TrustedClockState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

data class TrustedTimeSnapshot(
    val utcMillis: Long,
    val dayId: Long,
    val persistedDayId: Long,
    val zoneId: ZoneId,
    val rebootDetected: Boolean,
    val suspiciousJumpRejected: Boolean
) {
    val isNewDayPending: Boolean
        get() = dayId > persistedDayId
}

sealed interface NtpSyncResult {
    data class Synced(
        val snapshot: TrustedTimeSnapshot,
        val ntpUtcMillis: Long
    ) : NtpSyncResult

    data object Unavailable : NtpSyncResult

    data class RejectedSuspiciousNtp(
        val ntpUtcMillis: Long
    ) : NtpSyncResult
}

/**
 * Tamper-resistant clock backed by [DeviceClock.elapsedRealtimeMillis] and optional NTP re-anchoring.
 *
 * Guarantees:
 * - Never affected by manual system time or timezone changes while running.
 * - Never moves backwards, even after a device reboot with the system clock rolled back.
 * - Rejects suspicious unverified forward jumps after an offline reboot (crossing midnight or
 *   jumping more than [maxUnverifiedRebootJumpMillis]) until NTP confirms real time or monotonic
 *   uptime advances naturally.
 */
class TrustedClock(
    private val deviceClock: DeviceClock,
    private val stateRepository: TrustedClockStateRepository,
    private val ntpTimeProvider: NtpTimeProvider,
    private val maxUnverifiedRebootJumpMillis: Long = DEFAULT_MAX_UNVERIFIED_REBOOT_JUMP_MILLIS,
    private val checkpointIntervalMillis: Long = DEFAULT_CHECKPOINT_INTERVAL_MILLIS,
    private val maxPlausibleNtpJumpMillis: Long = DEFAULT_MAX_PLAUSIBLE_NTP_JUMP_MILLIS
) {
    private val mutex = Mutex()

    suspend fun now(forcePersistCheckpoint: Boolean = false): TrustedTimeSnapshot =
        mutex.withLock {
            evaluateLocked(forcePersistCheckpoint)
        }

    suspend fun recalculateAfterSystemTimeChange(): TrustedTimeSnapshot =
        mutex.withLock {
            evaluateLocked(forcePersistCheckpoint = true)
        }

    suspend fun syncWithNtp(): NtpSyncResult = mutex.withLock {
        val current = evaluateLocked(forcePersistCheckpoint = false)
        val saved = stateRepository.getState() ?: return@withLock NtpSyncResult.Unavailable

        val ntpUtc = ntpTimeProvider.fetchCurrentUtcMillis()
            ?: return@withLock NtpSyncResult.Unavailable

        if (ntpUtc < MIN_PLAUSIBLE_EPOCH_MILLIS) {
            return@withLock NtpSyncResult.RejectedSuspiciousNtp(ntpUtc)
        }

        if (saved.lastNtpSyncUtcMillis != null &&
            ntpUtc - current.utcMillis > maxPlausibleNtpJumpMillis
        ) {
            return@withLock NtpSyncResult.RejectedSuspiciousNtp(ntpUtc)
        }

        // Trusted time never moves backwards.
        val anchoredUtc = maxOf(ntpUtc, current.utcMillis)
        val elapsedNow = deviceClock.elapsedRealtimeMillis()
        val bootCountNow = deviceClock.bootCount()
        val zoneId = safeZoneId(saved.anchorZoneId)
        val realDayId = epochDayFor(anchoredUtc, zoneId)

        val updatedState = saved.copy(
            anchorUtcMillis = anchoredUtc,
            anchorElapsedRealtimeMillis = elapsedNow,
            lastNtpSyncUtcMillis = ntpUtc,
            lastBootCount = bootCountNow
        )
        stateRepository.saveState(updatedState)

        NtpSyncResult.Synced(
            snapshot = TrustedTimeSnapshot(
                utcMillis = anchoredUtc,
                dayId = realDayId,
                persistedDayId = updatedState.currentDayId,
                zoneId = zoneId,
                rebootDetected = current.rebootDetected,
                suspiciousJumpRejected = false
            ),
            ntpUtcMillis = ntpUtc
        )
    }

    suspend fun markDayResetCompleted(newDayId: Long) = mutex.withLock {
        val saved = stateRepository.getState() ?: return@withLock
        if (newDayId > saved.currentDayId) {
            stateRepository.saveState(saved.copy(currentDayId = newDayId))
        }
    }

    private suspend fun evaluateLocked(forcePersistCheckpoint: Boolean): TrustedTimeSnapshot {
        val elapsedNow = deviceClock.elapsedRealtimeMillis()
        val sysUtcNow = deviceClock.systemCurrentTimeMillis()
        val bootCountNow = deviceClock.bootCount()
        val saved = stateRepository.getState()

        if (saved == null) {
            val zoneId = deviceClock.systemZoneId()
            val initialDayId = epochDayFor(sysUtcNow, zoneId)
            val initialState = TrustedClockState(
                anchorUtcMillis = sysUtcNow,
                anchorElapsedRealtimeMillis = elapsedNow,
                anchorZoneId = zoneId.id,
                lastNtpSyncUtcMillis = null,
                currentDayId = initialDayId,
                lastBootCount = bootCountNow
            )
            stateRepository.saveState(initialState)
            return TrustedTimeSnapshot(
                utcMillis = sysUtcNow,
                dayId = initialDayId,
                persistedDayId = initialDayId,
                zoneId = zoneId,
                rebootDetected = false,
                suspiciousJumpRejected = false
            )
        }

        val zoneId = safeZoneId(saved.anchorZoneId)
        val bootCountChanged = saved.lastBootCount != null &&
            bootCountNow != null &&
            bootCountNow != saved.lastBootCount
        val elapsedWentBackwards = elapsedNow < saved.anchorElapsedRealtimeMillis
        val rebootDetected = bootCountChanged || elapsedWentBackwards

        if (!rebootDetected) {
            val monotonicDelta = (elapsedNow - saved.anchorElapsedRealtimeMillis).coerceAtLeast(0L)
            val trustedUtc = saved.anchorUtcMillis + monotonicDelta
            val realDayId = epochDayFor(trustedUtc, zoneId)

            val shouldPersist = forcePersistCheckpoint ||
                monotonicDelta >= checkpointIntervalMillis ||
                (saved.lastBootCount == null && bootCountNow != null)

            if (shouldPersist) {
                stateRepository.saveState(
                    saved.copy(
                        anchorUtcMillis = trustedUtc,
                        anchorElapsedRealtimeMillis = elapsedNow,
                        lastBootCount = bootCountNow ?: saved.lastBootCount
                    )
                )
            }

            return TrustedTimeSnapshot(
                utcMillis = trustedUtc,
                dayId = realDayId,
                persistedDayId = saved.currentDayId,
                zoneId = zoneId,
                rebootDetected = false,
                suspiciousJumpRejected = false
            )
        }

        // Reboot detected: elapsedRealtime was reset by the OS.
        val lastSavedUtc = saved.anchorUtcMillis
        val lastSavedDayId = epochDayFor(lastSavedUtc, zoneId)
        val sysDelta = sysUtcNow - lastSavedUtc
        val sysDayId = epochDayFor(sysUtcNow, zoneId)

        val trustedUtc: Long
        val suspiciousJumpRejected: Boolean

        when {
            // System clock went backwards (or did not advance): never go backwards.
            sysDelta <= 0L -> {
                trustedUtc = lastSavedUtc
                suspiciousJumpRejected = false
            }

            // Unverified offline reboot trying to cross midnight or jump forward suspiciously:
            // reject the jump and resume monotonically from lastSavedUtc until NTP confirms.
            sysDayId > lastSavedDayId || sysDelta > maxUnverifiedRebootJumpMillis -> {
                trustedUtc = lastSavedUtc
                suspiciousJumpRejected = true
            }

            // Plausible short same-day reboot.
            else -> {
                trustedUtc = sysUtcNow
                suspiciousJumpRejected = false
            }
        }

        val updatedState = saved.copy(
            anchorUtcMillis = trustedUtc,
            anchorElapsedRealtimeMillis = elapsedNow,
            lastBootCount = bootCountNow ?: saved.lastBootCount
        )
        stateRepository.saveState(updatedState)

        val realDayId = epochDayFor(trustedUtc, zoneId)
        return TrustedTimeSnapshot(
            utcMillis = trustedUtc,
            dayId = realDayId,
            persistedDayId = updatedState.currentDayId,
            zoneId = zoneId,
            rebootDetected = true,
            suspiciousJumpRejected = suspiciousJumpRejected
        )
    }

    private fun safeZoneId(zoneIdString: String): ZoneId = try {
        ZoneId.of(zoneIdString)
    } catch (_: Exception) {
        deviceClock.systemZoneId()
    }

    companion object {
        const val DEFAULT_MAX_UNVERIFIED_REBOOT_JUMP_MILLIS: Long = 2 * 60 * 60 * 1000L // 2 hours
        const val DEFAULT_CHECKPOINT_INTERVAL_MILLIS: Long = 60_000L // 1 minute
        const val DEFAULT_MAX_PLAUSIBLE_NTP_JUMP_MILLIS: Long = 365L * 24 * 60 * 60 * 1000L // 1 year
        const val MIN_PLAUSIBLE_EPOCH_MILLIS: Long = 1_735_689_600_000L // 2025-01-01T00:00:00Z

        fun epochDayFor(utcMillis: Long, zoneId: ZoneId): Long =
            Instant.ofEpochMilli(utcMillis).atZone(zoneId).toLocalDate().toEpochDay()
    }
}
