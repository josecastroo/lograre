package com.lograre.app.data.repository

import com.lograre.app.data.local.dao.TrackedAppDao
import com.lograre.app.data.local.entity.TrackedAppEntity
import com.lograre.app.data.local.entity.toDomain
import com.lograre.app.domain.model.TrackedApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppLimitRepository(
    private val trackedAppDao: TrackedAppDao
) {
    fun observeAllApps(): Flow<List<TrackedApp>> =
        trackedAppDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun getAllApps(): List<TrackedApp> =
        trackedAppDao.getAll().map { it.toDomain() }

    suspend fun getApp(packageName: String): TrackedApp? =
        trackedAppDao.getByPackageName(packageName)?.toDomain()

    fun observeApp(packageName: String): Flow<TrackedApp?> =
        trackedAppDao.observeByPackageName(packageName).map { it?.toDomain() }

    suspend fun addOrUpdateApp(
        packageName: String,
        dailyLimitMinutes: Int,
        currentDayId: Long,
        nowMillis: Long
    ) {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(dailyLimitMinutes >= 0) { "dailyLimitMinutes must be >= 0" }

        val existing = trackedAppDao.getByPackageName(packageName)
        if (existing == null) {
            trackedAppDao.upsert(
                TrackedAppEntity(
                    packageName = packageName,
                    dailyLimitMinutes = dailyLimitMinutes,
                    usedMillisToday = 0L,
                    extraMillisToday = 0L,
                    ignoredToday = false,
                    warningSentToday = false,
                    lastUpdatedDayId = currentDayId,
                    addedAtMillis = nowMillis
                )
            )
        } else {
            trackedAppDao.updateDailyLimit(
                packageName = packageName,
                dailyLimitMinutes = dailyLimitMinutes
            )
        }
    }

    suspend fun removeApp(packageName: String) {
        trackedAppDao.deleteByPackageName(packageName)
    }

    suspend fun addUsageMillis(packageName: String, deltaMillis: Long, dayId: Long) {
        require(deltaMillis >= 0L) { "deltaMillis must be >= 0" }
        if (deltaMillis == 0L) return
        trackedAppDao.addUsageMillis(packageName, deltaMillis, dayId)
    }

    suspend fun addExtraMinutes(packageName: String, extraMinutes: Int, dayId: Long) {
        require(extraMinutes > 0) { "extraMinutes must be > 0" }
        trackedAppDao.addExtraMillis(
            packageName = packageName,
            extraMillis = extraMinutes * TrackedApp.MILLIS_PER_MINUTE,
            dayId = dayId
        )
    }

    suspend fun ignoreForToday(packageName: String, dayId: Long) {
        trackedAppDao.setIgnoredToday(
            packageName = packageName,
            ignored = true,
            dayId = dayId
        )
    }

    suspend fun markWarningSent(packageName: String, dayId: Long) {
        trackedAppDao.setWarningSentToday(
            packageName = packageName,
            sent = true,
            dayId = dayId
        )
    }

    suspend fun resetAllForNewDay(newDayId: Long) {
        trackedAppDao.resetAllForNewDay(newDayId)
    }
}
