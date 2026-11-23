package com.lograre.app.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal fun isAnimatorScaleEnabled(scale: Float): Boolean = scale > 0f

private fun readSystemAnimationsEnabled(context: Context): Boolean = try {
    val scale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
    isAnimatorScaleEnabled(scale)
} catch (_: Exception) {
    true
}

/**
 * Returns false when the user disabled system animations (`ANIMATOR_DURATION_SCALE == 0`).
 * Refreshes automatically when the hosting lifecycle resumes.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    if (LocalInspectionMode.current) return true

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember(context) { mutableStateOf(readSystemAnimationsEnabled(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = readSystemAnimationsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return enabled
}

/**
 * Tracks whether the hosting lifecycle is currently in [Lifecycle.State.RESUMED]
 * so continuous ambient animations pause automatically when the screen is not visible.
 */
@Composable
fun rememberIsLifecycleResumed(): Boolean {
    if (LocalInspectionMode.current) return true

    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isResumed = true
                Lifecycle.Event.ON_PAUSE -> isResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return isResumed
}

/**
 * True only when system animations are enabled AND the screen is actively resumed.
 */
@Composable
fun rememberShouldRunContinuousAnimations(): Boolean {
    val animationsEnabled = rememberAnimationsEnabled()
    val isResumed = rememberIsLifecycleResumed()
    return animationsEnabled && isResumed
}
