package com.lograre.app.domain.clock

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.time.ZoneId

interface DeviceClock {
    fun elapsedRealtimeMillis(): Long
    fun systemCurrentTimeMillis(): Long
    fun systemZoneId(): ZoneId
    fun bootCount(): Int?
}

class AndroidDeviceClock(
    private val context: Context
) : DeviceClock {

    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()

    override fun systemCurrentTimeMillis(): Long = System.currentTimeMillis()

    override fun systemZoneId(): ZoneId = ZoneId.systemDefault()

    override fun bootCount(): Int? = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
    } catch (_: Exception) {
        null
    }
}
