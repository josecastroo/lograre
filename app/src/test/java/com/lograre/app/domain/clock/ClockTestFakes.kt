package com.lograre.app.domain.clock

import com.lograre.app.data.local.dao.TrackedAppDao
import com.lograre.app.data.local.dao.TrustedClockDao
import com.lograre.app.data.local.entity.TrackedAppEntity
import com.lograre.app.data.local.entity.TrustedClockStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.ZoneId

internal class FakeDeviceClock(
    var elapsedMillis: Long,
    var systemUtcMillis: Long,
    var zoneId: ZoneId = ZoneId.of("America/Mexico_City"),
    var bootCountValue: Int? = 1
) : DeviceClock {
    override fun elapsedRealtimeMillis(): Long = elapsedMillis
    override fun systemCurrentTimeMillis(): Long = systemUtcMillis
    override fun systemZoneId(): ZoneId = zoneId
    override fun bootCount(): Int? = bootCountValue

    fun advanceRealTime(millis: Long) {
        elapsedMillis += millis
        systemUtcMillis += millis
    }

    fun simulateReboot(newElapsedMillis: Long, newSystemUtcMillis: Long, incrementBootCount: Boolean = true) {
        elapsedMillis = newElapsedMillis
        systemUtcMillis = newSystemUtcMillis
        if (incrementBootCount) {
            bootCountValue = (bootCountValue ?: 0) + 1
        }
    }
}

internal class FakeNtpTimeProvider(
    var nextUtcMillis: Long? = null
) : NtpTimeProvider {
    override suspend fun fetchCurrentUtcMillis(): Long? = nextUtcMillis
}

internal class InMemoryTrustedClockDao : TrustedClockDao {
    private val stateFlow = MutableStateFlow<TrustedClockStateEntity?>(null)

    override suspend fun getState(): TrustedClockStateEntity? = stateFlow.value

    override fun observeState(): Flow<TrustedClockStateEntity?> = stateFlow

    override suspend fun saveState(state: TrustedClockStateEntity) {
        stateFlow.value = state
    }
}

internal class InMemoryTrackedAppDao : TrackedAppDao {
    private val items = MutableStateFlow<Map<String, TrackedAppEntity>>(emptyMap())

    override fun observeAll(): Flow<List<TrackedAppEntity>> =
        items.map { map -> map.values.sortedWith(compareBy({ it.addedAtMillis }, { it.packageName })) }

    override suspend fun getAll(): List<TrackedAppEntity> =
        items.value.values.sortedWith(compareBy({ it.addedAtMillis }, { it.packageName }))

    override suspend fun getByPackageName(packageName: String): TrackedAppEntity? =
        items.value[packageName]

    override fun observeByPackageName(packageName: String): Flow<TrackedAppEntity?> =
        items.map { it[packageName] }

    override suspend fun upsert(entity: TrackedAppEntity) {
        items.value = items.value + (entity.packageName to entity)
    }

    override suspend fun deleteByPackageName(packageName: String) {
        items.value = items.value - packageName
    }

    override suspend fun updateDailyLimit(packageName: String, dailyLimitMinutes: Int) {
        val current = items.value[packageName] ?: return
        items.value = items.value + (packageName to current.copy(dailyLimitMinutes = dailyLimitMinutes))
    }

    override suspend fun addUsageMillis(packageName: String, deltaMillis: Long, dayId: Long) {
        val current = items.value[packageName] ?: return
        items.value = items.value + (
            packageName to current.copy(
                usedMillisToday = current.usedMillisToday + deltaMillis,
                lastUpdatedDayId = dayId
            )
        )
    }

    override suspend fun addExtraMillis(packageName: String, extraMillis: Long, dayId: Long) {
        val current = items.value[packageName] ?: return
        items.value = items.value + (
            packageName to current.copy(
                extraMillisToday = current.extraMillisToday + extraMillis,
                lastUpdatedDayId = dayId
            )
        )
    }

    override suspend fun setIgnoredToday(packageName: String, ignored: Boolean, dayId: Long) {
        val current = items.value[packageName] ?: return
        items.value = items.value + (
            packageName to current.copy(
                ignoredToday = ignored,
                lastUpdatedDayId = dayId
            )
        )
    }

    override suspend fun setWarningSentToday(packageName: String, sent: Boolean, dayId: Long) {
        val current = items.value[packageName] ?: return
        items.value = items.value + (
            packageName to current.copy(
                warningSentToday = sent,
                lastUpdatedDayId = dayId
            )
        )
    }

    override suspend fun resetAllForNewDay(newDayId: Long) {
        items.value = items.value.mapValues { (_, entity) ->
            entity.copy(
                usedMillisToday = 0L,
                extraMillisToday = 0L,
                ignoredToday = false,
                warningSentToday = false,
                lastUpdatedDayId = newDayId
            )
        }
    }
}
