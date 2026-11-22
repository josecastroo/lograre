package com.lograre.app.domain.model

data class TrustedClockState(
    val anchorUtcMillis: Long,
    val anchorElapsedRealtimeMillis: Long,
    val anchorZoneId: String,
    val lastNtpSyncUtcMillis: Long? = null,
    val currentDayId: Long,
    val lastBootCount: Int? = null
)
