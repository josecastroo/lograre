package com.lograre.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class AppPermissionsStatus(
    val accessibilityEnabled: Boolean = false,
    val notificationsGranted: Boolean = true,
    val batteryOptimizationIgnored: Boolean = false
) {
    val requiresNotificationRuntimePermission: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

object PermissionStatusChecker {
    fun check(context: Context): AppPermissionsStatus {
        val accessibility = AccessibilityServiceState.isRunning.value ||
            AccessibilityServiceState.isEnabledInSettings(context)

        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        return AppPermissionsStatus(
            accessibilityEnabled = accessibility,
            notificationsGranted = notifications,
            batteryOptimizationIgnored = batteryIgnored
        )
    }
}
