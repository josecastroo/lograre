package com.lograre.app.domain.tracker

/**
 * Packages that must never be tracked or blocked by default
 * (lograre itself, system settings, SystemUI, phone/dialer).
 */
object ProtectedPackages {
    val STATIC_CRITICAL_PACKAGES: Set<String> = setOf(
        "com.lograre.app",
        "com.android.settings",
        "com.android.systemui",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.incallui",
        "com.samsung.android.incallui",
        "com.android.emergency"
    )
}

interface SystemPackageProvider {
    fun getProtectedPackages(): Set<String>
    fun getInputMethodPackages(): Set<String>
}
