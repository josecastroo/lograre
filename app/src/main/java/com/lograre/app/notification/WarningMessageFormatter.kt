package com.lograre.app.notification

import kotlin.math.ceil

data class WarningNotificationContent(
    val title: String,
    val body: String
)

object WarningMessageFormatter {

    fun format(appLabel: String, remainingMillis: Long): WarningNotificationContent {
        val safeMillis = remainingMillis.coerceAtLeast(1_000L)
        val remainingText = if (safeMillis < 60_000L) {
            val seconds = ceil(safeMillis / 1_000.0).toInt().coerceAtLeast(1)
            "$seconds s"
        } else {
            val minutes = ceil(safeMillis / 60_000.0).toInt().coerceAtLeast(1)
            if (minutes == 1) "1 min" else "$minutes min"
        }

        return WarningNotificationContent(
            title = "Quedan $remainingText en $appLabel",
            body = "Cuando se agote el tiempo diario, $appLabel se bloqueará con PIN."
        )
    }

    fun notificationIdFor(packageName: String): Int =
        NOTIFICATION_ID_OFFSET + (packageName.hashCode() and 0x0FFFFFFF)

    private const val NOTIFICATION_ID_OFFSET = 10_000
}
