package com.lograre.app.ui.lock

import android.content.Context
import com.lograre.app.domain.tracker.AppBlockLauncher

class ActivityAppBlockLauncher(
    private val context: Context
) : AppBlockLauncher {

    override fun launchBlockScreen(packageName: String) {
        val intent = LockScreenActivity.createIntent(context, packageName)
        context.startActivity(intent)
    }
}
