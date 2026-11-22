package com.lograre.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lograre.app.domain.model.TrackedApp

@Entity(tableName = "tracked_apps")
data class TrackedAppEntity(
    @PrimaryKey
    val packageName: String,
    val dailyLimitMinutes: Int,
    val usedMillisToday: Long = 0L,
    val extraMillisToday: Long = 0L,
    val ignoredToday: Boolean = false,
    val warningSentToday: Boolean = false,
    val lastUpdatedDayId: Long = 0L,
    val addedAtMillis: Long = 0L
)

fun TrackedAppEntity.toDomain(): TrackedApp = TrackedApp(
    packageName = packageName,
    dailyLimitMinutes = dailyLimitMinutes,
    usedMillisToday = usedMillisToday,
    extraMillisToday = extraMillisToday,
    ignoredToday = ignoredToday,
    warningSentToday = warningSentToday,
    lastUpdatedDayId = lastUpdatedDayId,
    addedAtMillis = addedAtMillis
)

fun TrackedApp.toEntity(): TrackedAppEntity = TrackedAppEntity(
    packageName = packageName,
    dailyLimitMinutes = dailyLimitMinutes,
    usedMillisToday = usedMillisToday,
    extraMillisToday = extraMillisToday,
    ignoredToday = ignoredToday,
    warningSentToday = warningSentToday,
    lastUpdatedDayId = lastUpdatedDayId,
    addedAtMillis = addedAtMillis
)
