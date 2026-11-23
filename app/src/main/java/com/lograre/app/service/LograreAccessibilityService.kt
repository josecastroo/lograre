package com.lograre.app.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import com.lograre.app.LograreApplication
import com.lograre.app.LograreContainer
import com.lograre.app.domain.tracker.ForegroundEventDecision
import com.lograre.app.domain.tracker.ForegroundEventFilter
import com.lograre.app.domain.tracker.TrackerAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Monitors foreground application changes via [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED]
 * and [AccessibilityEvent.TYPE_WINDOWS_CHANGED], processing all commands in strict FIFO order
 * to avoid race conditions during rapid app switches, Recents gestures, and transient overlays.
 */
class LograreAccessibilityService : AccessibilityService() {

    private sealed interface ServiceCommand {
        data object BootRestore : ServiceCommand
        data class EvaluateForeground(
            val isWindowStateChanged: Boolean,
            val isWindowsChanged: Boolean,
            val eventPackageName: String?,
            val eventClassName: String?,
            val isFullScreen: Boolean,
            val activeWindowPackageName: String?
        ) : ServiceCommand
        data object ScreenOff : ServiceCommand
        data object UserPresent : ServiceCommand
        data object Tick : ServiceCommand
    }

    private var serviceScope: CoroutineScope? = null
    private var commandChannel: Channel<ServiceCommand>? = null
    private var tickerJob: Job? = null

    @Volatile
    private var lastKnownForegroundPackage: String? = null
    private var screenReceiverRegistered: Boolean = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> enqueue(ServiceCommand.ScreenOff)
                Intent.ACTION_USER_PRESENT -> enqueue(ServiceCommand.UserPresent)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityServiceState.setRunning(true)

        stopTicker()
        commandChannel?.close()
        serviceScope?.cancel()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val channel = Channel<ServiceCommand>(Channel.UNLIMITED)
        serviceScope = scope
        commandChannel = channel

        if (!screenReceiverRegistered) {
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
        }

        scope.launch {
            for (command in channel) {
                val container = getContainer() ?: continue
                processCommandSequentially(container, command)
            }
        }

        // Enqueue restore first, then evaluate whatever app is currently on screen.
        enqueue(ServiceCommand.BootRestore)
        val currentWindowPkg = resolveActiveApplicationWindowPackage()
        if (currentWindowPkg != null) {
            enqueue(
                ServiceCommand.EvaluateForeground(
                    isWindowStateChanged = false,
                    isWindowsChanged = true,
                    eventPackageName = null,
                    eventClassName = null,
                    isFullScreen = true,
                    activeWindowPackageName = currentWindowPkg
                )
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val isWindowStateChanged = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val isWindowsChanged = event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (!isWindowStateChanged && !isWindowsChanged) return

        val eventPackage = event.packageName?.toString()
        val eventClass = event.className?.toString()
        val isFullScreen = event.isFullScreen
        val activeWindowPkg = resolveActiveApplicationWindowPackage()

        enqueue(
            ServiceCommand.EvaluateForeground(
                isWindowStateChanged = isWindowStateChanged,
                isWindowsChanged = isWindowsChanged,
                eventPackageName = eventPackage,
                eventClassName = eventClass,
                isFullScreen = isFullScreen,
                activeWindowPackageName = activeWindowPkg
            )
        )
    }

    override fun onInterrupt() {
        stopTicker()
        val container = getContainer() ?: return
        serviceScope?.launch {
            container.foregroundUsageTracker.onServiceStopped()
        }
    }

    override fun onDestroy() {
        AccessibilityServiceState.setRunning(false)
        stopTicker()
        commandChannel?.close()
        commandChannel = null

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

    private fun enqueue(command: ServiceCommand) {
        commandChannel?.trySend(command)
    }

    private suspend fun processCommandSequentially(
        container: LograreContainer,
        command: ServiceCommand
    ) {
        when (command) {
            ServiceCommand.BootRestore -> {
                container.bootRestoreHandler.onDeviceBootOrRestore()
            }

            is ServiceCommand.EvaluateForeground -> {
                val imePackages = container.systemPackageProvider.getInputMethodPackages()
                val decision = ForegroundEventFilter.decide(
                    isWindowStateChanged = command.isWindowStateChanged,
                    isWindowsChanged = command.isWindowsChanged,
                    eventPackageName = command.eventPackageName,
                    eventClassName = command.eventClassName,
                    isFullScreen = command.isFullScreen,
                    activeApplicationWindowPackage = command.activeWindowPackageName,
                    inputMethodPackages = imePackages,
                    isScreenInteractiveAndUnlocked = isScreenInteractiveAndUnlocked()
                )
                when (decision) {
                    ForegroundEventDecision.Ignore -> Unit
                    ForegroundEventDecision.PauseScreenOff -> {
                        stopTicker()
                        val action = container.foregroundUsageTracker.onScreenOff()
                        dispatchAction(container, action)
                    }
                    is ForegroundEventDecision.SwitchTo -> {
                        lastKnownForegroundPackage = decision.packageName
                        val action = container.foregroundUsageTracker.onForegroundPackageChanged(
                            decision.packageName
                        )
                        dispatchAction(container, action)
                        updateTickerState(container)
                    }
                }
            }

            ServiceCommand.ScreenOff -> {
                stopTicker()
                val action = container.foregroundUsageTracker.onScreenOff()
                dispatchAction(container, action)
            }

            ServiceCommand.UserPresent -> {
                if (!isScreenInteractiveAndUnlocked()) return
                val activePkg = resolveActiveApplicationWindowPackage() ?: lastKnownForegroundPackage
                if (activePkg != null) {
                    lastKnownForegroundPackage = activePkg
                    val action = container.foregroundUsageTracker.onForegroundPackageChanged(activePkg)
                    dispatchAction(container, action)
                    updateTickerState(container)
                }
            }

            ServiceCommand.Tick -> {
                if (!isScreenInteractiveAndUnlocked()) {
                    stopTicker()
                    val action = container.foregroundUsageTracker.onScreenOff()
                    dispatchAction(container, action)
                    return
                }
                val activeWindowPkg = resolveActiveApplicationWindowPackage()
                val currentSessionPkg = container.foregroundUsageTracker.currentActivePackage()
                if (activeWindowPkg != null &&
                    currentSessionPkg != null &&
                    activeWindowPkg != currentSessionPkg
                ) {
                    val decision = ForegroundEventFilter.decide(
                        isWindowStateChanged = false,
                        isWindowsChanged = true,
                        eventPackageName = null,
                        eventClassName = null,
                        isFullScreen = true,
                        activeApplicationWindowPackage = activeWindowPkg,
                        inputMethodPackages = container.systemPackageProvider.getInputMethodPackages(),
                        isScreenInteractiveAndUnlocked = true
                    )
                    if (decision is ForegroundEventDecision.SwitchTo &&
                        decision.packageName != currentSessionPkg
                    ) {
                        lastKnownForegroundPackage = decision.packageName
                        val switchAction = container.foregroundUsageTracker.onForegroundPackageChanged(
                            decision.packageName
                        )
                        dispatchAction(container, switchAction)
                        updateTickerState(container)
                        return
                    }
                }
                val action = container.foregroundUsageTracker.tick()
                dispatchAction(container, action)
                if (!container.foregroundUsageTracker.hasActiveSession()) {
                    stopTicker()
                }
            }
        }
    }

    private fun resolveActiveApplicationWindowPackage(): String? = try {
        val appWindows = windows?.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val focusedWindow = appWindows?.firstOrNull { it.isFocused }
            ?: appWindows?.firstOrNull { it.isActive }
            ?: appWindows?.maxByOrNull { it.layer }

        val fromWindows = focusedWindow?.root?.packageName?.toString()?.takeIf { it.isNotBlank() }
        fromWindows ?: rootInActiveWindow?.packageName?.toString()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private suspend fun updateTickerState(container: LograreContainer) {
        if (container.foregroundUsageTracker.hasActiveSession()) {
            startTickerIfNeeded()
        } else {
            stopTicker()
        }
    }

    private fun startTickerIfNeeded() {
        if (tickerJob?.isActive == true) return
        val scope = serviceScope ?: return
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MILLIS)
                enqueue(ServiceCommand.Tick)
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
        private const val TICK_INTERVAL_MILLIS = 1_000L
    }
}
