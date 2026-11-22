package com.selfagency.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class InstalledApp(val packageName: String, val label: String, val icon: Drawable?)

object InstalledApps {
    /** All apps that appear in the launcher, sorted by name. Call off the main thread. */
    fun load(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString(), it.loadIcon(pm)) }
            .sortedBy { it.label.lowercase() }
    }

    fun labelFor(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    fun iconFor(context: Context, pkg: String): Drawable? = try {
        context.packageManager.getApplicationIcon(pkg)
    } catch (_: Exception) {
        null
    }
}
