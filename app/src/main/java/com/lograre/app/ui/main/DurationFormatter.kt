package com.lograre.app.ui.main

object DurationFormatter {

    fun formatMinutes(minutes: Int): String {
        if (minutes <= 0) return "0 min"
        val hours = minutes / 60
        val remMinutes = minutes % 60
        return when {
            hours == 0 -> "$remMinutes min"
            remMinutes == 0 -> "${hours}h"
            else -> "${hours}h ${remMinutes}m"
        }
    }

    fun formatMillis(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L) / 1_000L)
        if (totalSeconds == 0L) return "0 min"
        if (totalSeconds < 60L) return "${totalSeconds}s"

        val totalMinutes = (totalSeconds / 60L).toInt()
        val remSeconds = (totalSeconds % 60L).toInt()
        if (totalMinutes < 60) {
            return if (remSeconds == 0) {
                "$totalMinutes min"
            } else {
                "${totalMinutes}m ${remSeconds}s"
            }
        }
        return formatMinutes(totalMinutes)
    }
}
