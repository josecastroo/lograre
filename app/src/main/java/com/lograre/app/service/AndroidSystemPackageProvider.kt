package com.lograre.app.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import com.lograre.app.domain.tracker.ProtectedPackages
import com.lograre.app.domain.tracker.SystemPackageProvider

class AndroidSystemPackageProvider(
    private val context: Context
) : SystemPackageProvider {

    override fun getProtectedPackages(): Set<String> {
        val packages = LinkedHashSet<String>(ProtectedPackages.STATIC_CRITICAL_PACKAGES)
        packages.add(context.packageName)

        resolveDefaultLauncherPackage()?.let { packages.add(it) }
        resolveDefaultDialerPackage()?.let { packages.add(it) }
        return packages
    }

    override fun getInputMethodPackages(): Set<String> = try {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.enabledInputMethodList
            ?.mapNotNull { it.packageName }
            ?.toSet()
            ?: emptySet()
    } catch (_: Exception) {
        emptySet()
    }

    private fun resolveDefaultLauncherPackage(): String? = try {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(
            homeIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        resolveInfo?.activityInfo?.packageName
    } catch (_: Exception) {
        null
    }

    private fun resolveDefaultDialerPackage(): String? = try {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        telecomManager?.defaultDialerPackage
    } catch (_: Exception) {
        null
    }
}
