package com.lograre.app.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.lograre.app.domain.tracker.SystemPackageProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val label: String
)

interface InstalledAppsProvider {
    suspend fun getLaunchableApps(): List<InstalledAppInfo>
}

class AndroidInstalledAppsProvider(
    private val context: Context,
    private val systemPackageProvider: SystemPackageProvider,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : InstalledAppsProvider {

    override suspend fun getLaunchableApps(): List<InstalledAppInfo> = withContext(ioDispatcher) {
        val pm = context.packageManager
        val protectedPackages = systemPackageProvider.getProtectedPackages()
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolveInfos = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        val uniqueApps = LinkedHashMap<String, InstalledAppInfo>()

        for (info in resolveInfos) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg in protectedPackages || pkg in uniqueApps) continue
            val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
            uniqueApps[pkg] = InstalledAppInfo(packageName = pkg, label = label)
        }

        uniqueApps.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
}

object AppIconLoader {
    fun loadIconBitmap(context: Context, packageName: String, sizePx: Int = 96): ImageBitmap? = try {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        drawable.toBitmap(width = sizePx, height = sizePx).asImageBitmap()
    } catch (_: Exception) {
        null
    }

    fun resolveAppLabel(context: Context, packageName: String): String = try {
        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(appInfo).toString()
    } catch (_: Exception) {
        packageName
    }
}
