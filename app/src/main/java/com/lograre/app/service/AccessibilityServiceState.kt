package com.lograre.app.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AccessibilityServiceState {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    internal fun setRunning(running: Boolean) {
        _isRunning.value = running
    }

    fun isEnabledInSettings(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val expectedComponent = ComponentName(context, LograreAccessibilityService::class.java)
        val flattenedFull = expectedComponent.flattenToString()
        val flattenedShort = expectedComponent.flattenToShortString()

        return enabledServices
            .split(':')
            .any { entry ->
                entry.equals(flattenedFull, ignoreCase = true) ||
                    entry.equals(flattenedShort, ignoreCase = true)
            }
    }
}
