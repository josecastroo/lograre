package com.lograre.app.ui.main

import androidx.annotation.StringRes
import com.lograre.app.R

object HomeGreeting {
    @StringRes
    fun greetingResIdForHour(hourOfDay: Int): Int = when (hourOfDay) {
        in 5..11 -> R.string.greeting_morning
        in 12..19 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    }
}
