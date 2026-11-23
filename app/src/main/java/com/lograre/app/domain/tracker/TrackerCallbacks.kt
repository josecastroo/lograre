package com.lograre.app.domain.tracker

/**
 * Decoupled hook invoked when a tracked app reaches its daily limit.
 * Wired to LockScreenActivity in Step 4.
 */
fun interface AppBlockLauncher {
    fun launchBlockScreen(packageName: String)
}

/**
 * Decoupled hook invoked when a tracked app has <= 5 minutes remaining
 * (only when dailyLimitMinutes >= 5). Wired to Android notifications in Step 6.
 */
fun interface FiveMinuteWarningNotifier {
    fun notifyFiveMinutesRemaining(packageName: String, remainingMillis: Long)
}
