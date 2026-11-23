package com.lograre.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Calm, system-respecting haptic feedback helpers.
 * Uses [View.performHapticFeedback] so Android's system vibration/haptic preference
 * is always honored and no extra VIBRATE permission is required.
 */
object ZenHaptics {

    fun keyPress(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(constant)
    }

    fun reject(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }
}
