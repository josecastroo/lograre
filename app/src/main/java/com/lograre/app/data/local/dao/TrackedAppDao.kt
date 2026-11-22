package com.lograre.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lograre.app.data.local.entity.TrackedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedAppDao {

    @Query("SELECT * FROM tracked_apps ORDER BY addedAtMillis ASC, packageName ASC")
    fun observeAll(): Flow<List<TrackedAppEntity>>

    @Query("SELECT * FROM tracked_apps ORDER BY addedAtMillis ASC, packageName ASC")
    suspend fun getAll(): List<TrackedAppEntity>

    @Query("SELECT * FROM tracked_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): TrackedAppEntity?

    @Query("SELECT * FROM tracked_apps WHERE packageName = :packageName LIMIT 1")
    fun observeByPackageName(packageName: String): Flow<TrackedAppEntity?>

    @Upsert
    suspend fun upsert(entity: TrackedAppEntity)

    @Query("DELETE FROM tracked_apps WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query(
        "UPDATE tracked_apps " +
            "SET dailyLimitMinutes = :dailyLimitMinutes " +
            "WHERE packageName = :packageName"
    )
    suspend fun updateDailyLimit(packageName: String, dailyLimitMinutes: Int)

    @Query(
        "UPDATE tracked_apps " +
            "SET usedMillisToday = usedMillisToday + :deltaMillis, " +
            "lastUpdatedDayId = :dayId " +
            "WHERE packageName = :packageName"
    )
    suspend fun addUsageMillis(packageName: String, deltaMillis: Long, dayId: Long)

    @Query(
        "UPDATE tracked_apps " +
            "SET extraMillisToday = extraMillisToday + :extraMillis, " +
            "lastUpdatedDayId = :dayId " +
            "WHERE packageName = :packageName"
    )
    suspend fun addExtraMillis(packageName: String, extraMillis: Long, dayId: Long)

    @Query(
        "UPDATE tracked_apps " +
            "SET ignoredToday = :ignored, " +
            "lastUpdatedDayId = :dayId " +
            "WHERE packageName = :packageName"
    )
    suspend fun setIgnoredToday(packageName: String, ignored: Boolean, dayId: Long)

    @Query(
        "UPDATE tracked_apps " +
            "SET warningSentToday = :sent, " +
            "lastUpdatedDayId = :dayId " +
            "WHERE packageName = :packageName"
    )
    suspend fun setWarningSentToday(packageName: String, sent: Boolean, dayId: Long)

    @Query(
        "UPDATE tracked_apps " +
            "SET usedMillisToday = 0, " +
            "extraMillisToday = 0, " +
            "ignoredToday = 0, " +
            "warningSentToday = 0, " +
            "lastUpdatedDayId = :newDayId"
    )
    suspend fun resetAllForNewDay(newDayId: Long)
}
