package com.lograre.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lograre.app.MainActivity
import com.lograre.app.R
import com.lograre.app.data.apps.AppIconLoader
import com.lograre.app.domain.tracker.FiveMinuteWarningNotifier

/**
 * Posts a high-priority local notification when a tracked app has <= 5 minutes left
 * (only triggered by [com.lograre.app.domain.tracker.ForegroundUsageTracker] when dailyLimitMinutes >= 5).
 */
class AndroidFiveMinuteWarningNotifier(
    private val context: Context
) : FiveMinuteWarningNotifier {

    fun ensureNotificationChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        manager.createNotificationChannel(channel)
    }

    override fun notifyFiveMinutesRemaining(packageName: String, remainingMillis: Long) {
        if (!canPostNotifications()) return
        ensureNotificationChannel()

        val appLabel = AppIconLoader.resolveAppLabel(context, packageName)
        val content = WarningMessageFormatter.format(appLabel, remainingMillis)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            WarningMessageFormatter.notificationIdFor(packageName),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(WarningMessageFormatter.notificationIdFor(packageName), notification)
        } catch (_: SecurityException) {
            // Permission revoked between check and notify call.
        }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    companion object {
        const val CHANNEL_ID = "lograre_time_warnings"
        private const val CHANNEL_NAME = "Avisos de tiempo restante"
        private const val CHANNEL_DESCRIPTION =
            "Avisa cuando faltan 5 minutos para agotar el límite diario de una aplicación."
    }
}
