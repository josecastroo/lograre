package com.lograre.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lograre.app.domain.model.TrustedClockState

@Entity(tableName = "trusted_clock_state")
data class TrustedClockStateEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    val anchorUtcMillis: Long,
    val anchorElapsedRealtimeMillis: Long,
    val anchorZoneId: String,
    val lastNtpSyncUtcMillis: Long? = null,
    val currentDayId: Long,
    val lastBootCount: Int? = null
) {
    companion object {
        const val SINGLETON_ID: Int = 1
    }
}

fun TrustedClockStateEntity.toDomain(): TrustedClockState = TrustedClockState(
    anchorUtcMillis = anchorUtcMillis,
    anchorElapsedRealtimeMillis = anchorElapsedRealtimeMillis,
    anchorZoneId = anchorZoneId,
    lastNtpSyncUtcMillis = lastNtpSyncUtcMillis,
    currentDayId = currentDayId,
    lastBootCount = lastBootCount
)

fun TrustedClockState.toEntity(): TrustedClockStateEntity = TrustedClockStateEntity(
    id = TrustedClockStateEntity.SINGLETON_ID,
    anchorUtcMillis = anchorUtcMillis,
    anchorElapsedRealtimeMillis = anchorElapsedRealtimeMillis,
    anchorZoneId = anchorZoneId,
    lastNtpSyncUtcMillis = lastNtpSyncUtcMillis,
    currentDayId = currentDayId,
    lastBootCount = lastBootCount
)
