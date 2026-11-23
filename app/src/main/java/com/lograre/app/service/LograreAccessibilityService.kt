package com.lograre.app.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.lograre.app.LograreApplication
import com.lograre.app.LograreContainer
import com.lograre.app.domain.tracker.TrackerAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Monitors foreground application changes via [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED]
 * and drives [com.lograre.app.domain.tracker.ForegroundUsageTracker].
 */
class LograreAccessibilityService : AccessibilityService() {

    private var serviceScope: CoroutineScope? = null
    private var tickerJob: Job? = null
    @Volatile
    private var lastNonTransientPackage: String? = null
    private var screenReceiverRegistered: Boolean = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> handleScreenOff()
                Intent.ACTION_USER_PRESENT -> handleUserPresent()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityServiceState.setRunning(true)
        serviceScope?.cancel()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenReceiverRegistered = true

        val container = getContainer()
        if (container != null) {
            serviceScope?.launch {
                container.bootRestoreHandler.onDeviceBootOrRestore()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        val container = getContainer() ?: return

        if (packageName in container.systemPackageProvider.getInputMethodPackages()) {
            // Soft keyboard overlay: keep counting the app underneath.
            return
        }

        if (packageName == SYSTEM_UI_PACKAGE) {
            if (!isScreenInteractiveAndUnlocked()) {
                handleScreenOff()
            }
            // Ignore transient SystemUI overlays (volume panel, heads-up notifications).
            return
        }

        if (!isScreenInteractiveAndUnlocked()) {
            handleScreenOff()
            return
        }

        lastNonTransientPackage = packageName
        serviceScope?.launch {
            val action = container.foregroundUsageTracker.onForegroundPackageChanged(packageName)
            dispatchAction(container, action)
            updateTickerState(container)
        }
    }

    override fun onInterrupt() {
        val container = getContainer() ?: return
        stopTicker()
        serviceScope?.launch {
            container.foregroundUsageTracker.onServiceStopped()
        }
    }

    override fun onDestroy() {
        AccessibilityServiceState.setRunning(false)
        stopTicker()
        if (screenReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered.
            }
            screenReceiverRegistered = false
        }
        val container = getContainer()
        if (container != null) {
            container.applicationScope.launch {
                container.foregroundUsageTracker.onServiceStopped()
            }
        }
        serviceScope?.cancel()
        serviceScope = null
        super.onDestroy()
    }

    private fun handleScreenOff() {
        val container = getContainer() ?: return
        stopTicker()
        serviceScope?.launch {
            val action = container.foregroundUsageTracker.onScreenOff()
            dispatchAction(container, action)
        }
    }

    private fun handleUserPresent() {
        val container = getContainer() ?: return
        val resumedPackage = lastNonTransientPackage ?: return
        if (!isScreenInteractiveAndUnlocked()) return

        serviceScope?.launch {
            val action = container.foregroundUsageTracker.onForegroundPackageChanged(resumedPackage)
            dispatchAction(container, action)
            updateTickerState(container)
        }
    }

    private suspend fun updateTickerState(container: LograreContainer) {
        if (container.foregroundUsageTracker.hasActiveSession()) {
            startTickerIfNeeded(container)
        } else {
            stopTicker()
        }
    }

    private fun startTickerIfNeeded(container: LograreContainer) {
        if (tickerJob?.isActive == true) return
        val scope = serviceScope ?: return
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MILLIS)
                if (!isScreenInteractiveAndUnlocked()) {
                    val action = container.foregroundUsageTracker.onScreenOff()
                    dispatchAction(container, action)
                    break
                }
                val action = container.foregroundUsageTracker.tick()
                dispatchAction(container, action)
                if (!container.foregroundUsageTracker.hasActiveSession()) {
                    break
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun dispatchAction(container: LograreContainer, action: TrackerAction) {
        when (action) {
            TrackerAction.None -> Unit
            is TrackerAction.BlockRequired ->
                container.appBlockLauncher.launchBlockScreen(action.packageName)
            is TrackerAction.FiveMinuteWarning ->
                container.warningNotifier.notifyFiveMinutesRemaining(
                    action.packageName,
                    action.remainingMillis
                )
        }
    }

    private fun isScreenInteractiveAndUnlocked(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isInteractive = powerManager?.isInteractive ?: true
        val isLocked = keyguardManager?.isKeyguardLocked ?: false
        return isInteractive && !isLocked
    }

    private fun getContainer(): LograreContainer? =
        (application as? LograreApplication)?.container

    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val TICK_INTERVAL_MILLIS = 1_000L
    }
}
