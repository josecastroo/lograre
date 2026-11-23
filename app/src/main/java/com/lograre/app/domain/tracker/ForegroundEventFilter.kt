package com.lograre.app.domain.tracker

sealed interface ForegroundEventDecision {
    data class SwitchTo(val packageName: String) : ForegroundEventDecision
    data object Ignore : ForegroundEventDecision
    data object PauseScreenOff : ForegroundEventDecision
}

/**
 * Pure decision logic that filters transient overlays, soft keyboards, and SystemUI popups
 * so foreground tracking remains accurate across real Android 14–16 devices and apps like Instagram.
 */
object ForegroundEventFilter {

    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

    private val TRANSIENT_OVERLAY_PACKAGES: Set<String> = setOf(
        "android",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.as",
        "com.google.android.ext.services",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller"
    )

    private val TRANSIENT_CLASS_MARKERS: List<String> = listOf(
        "Toast",
        "PopupWindow",
        "VolumeDialog",
        "Snackbar",
        "Autofill"
    )

    fun decide(
        isWindowStateChanged: Boolean,
        isWindowsChanged: Boolean,
        eventPackageName: String?,
        eventClassName: String?,
        isFullScreen: Boolean,
        activeApplicationWindowPackage: String?,
        inputMethodPackages: Set<String>,
        isScreenInteractiveAndUnlocked: Boolean
    ): ForegroundEventDecision {
        if (!isScreenInteractiveAndUnlocked) {
            return ForegroundEventDecision.PauseScreenOff
        }

        val cleanEventPkg = eventPackageName?.takeIf { it.isNotBlank() }
        val cleanActivePkg = activeApplicationWindowPackage
            ?.takeIf { it.isNotBlank() && it != SYSTEM_UI_PACKAGE && it !in inputMethodPackages }

        // If TYPE_WINDOWS_CHANGED fired (e.g., Recents gesture swipe back into an already-open app),
        // rely on the focused TYPE_APPLICATION window package.
        if (isWindowsChanged && !isWindowStateChanged) {
            return if (cleanActivePkg != null && cleanActivePkg !in TRANSIENT_OVERLAY_PACKAGES) {
                ForegroundEventDecision.SwitchTo(cleanActivePkg)
            } else {
                ForegroundEventDecision.Ignore
            }
        }

        if (!isWindowStateChanged) {
            return ForegroundEventDecision.Ignore
        }

        if (cleanEventPkg != null) {
            if (cleanEventPkg in inputMethodPackages || cleanEventPkg == SYSTEM_UI_PACKAGE) {
                return ForegroundEventDecision.Ignore
            }

            if (!isFullScreen && isTransientOverlay(cleanEventPkg, eventClassName)) {
                return if (cleanActivePkg != null && cleanActivePkg !in TRANSIENT_OVERLAY_PACKAGES) {
                    ForegroundEventDecision.SwitchTo(cleanActivePkg)
                } else {
                    ForegroundEventDecision.Ignore
                }
            }
        }

        // Prefer the OS-reported focused TYPE_APPLICATION window when it disagrees with a non-fullscreen event.
        if (cleanActivePkg != null &&
            cleanActivePkg !in TRANSIENT_OVERLAY_PACKAGES &&
            (!isFullScreen || cleanEventPkg == null || cleanEventPkg in TRANSIENT_OVERLAY_PACKAGES)
        ) {
            return ForegroundEventDecision.SwitchTo(cleanActivePkg)
        }

        // Fall back to eventPackageName (important when rootInActiveWindow is intermittently null,
        // e.g. Instagram Reels/SurfaceView).
        if (cleanEventPkg != null && cleanEventPkg !in TRANSIENT_OVERLAY_PACKAGES) {
            return ForegroundEventDecision.SwitchTo(cleanEventPkg)
        }

        return ForegroundEventDecision.Ignore
    }

    private fun isTransientOverlay(packageName: String, className: String?): Boolean {
        if (packageName in TRANSIENT_OVERLAY_PACKAGES) return true
        if (className != null && TRANSIENT_CLASS_MARKERS.any { className.contains(it, ignoreCase = true) }) {
            return true
        }
        return false
    }
}
