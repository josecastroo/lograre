package com.lograre.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lograre.app.LograreApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Listens for manual system clock or timezone changes and forces recalculation
 * via [com.lograre.app.domain.clock.TrustedClock] instead of trusting the altered system time.
 */
class TimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_TIME_CHANGED && action != Intent.ACTION_TIMEZONE_CHANGED) {
            return
        }

        val app = context.applicationContext as? LograreApplication ?: return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.container.dailyResetCoordinator.checkAndPerformDailyReset(
                    attemptNtpSync = true,
                    afterSystemTimeChange = true
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
