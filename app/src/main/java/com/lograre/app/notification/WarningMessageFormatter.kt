package com.lograre.app.notification

import kotlin.math.ceil

data class WarningNotificationContent(
    val title: String,
    val body: String
)

object WarningMessageFormatter {

    fun format(appLabel: String, remainingMillis: Long): WarningNotificationContent {
        val safeMillis = remainingMillis.coerceAtLeast(1_000L)
        val title = if (safeMillis < 60_000L) {
            val seconds = ceil(safeMillis / 1_000.0).toInt().coerceAtLeast(1)
            if (seconds == 1) {
                "Te queda 1 segundo en $appLabel."
            } else {
                "Te quedan $seconds segundos en $appLabel."
            }
        } else {
            val minutes = ceil(safeMillis / 60_000.0).toInt().coerceAtLeast(1)
            if (minutes == 1) {
                "Te queda 1 minuto en $appLabel."
            } else {
                "Te quedan $minutes minutos en $appLabel."
            }
        }

        return WarningNotificationContent(
            title = title,
            body = "Cuando termine tu tiempo, $appLabel hará una pausa."
        )
    }

    fun notificationIdFor(packageName: String): Int =
        NOTIFICATION_ID_OFFSET + (packageName.hashCode() and 0x0FFFFFFF)

    private const val NOTIFICATION_ID_OFFSET = 10_000
}
