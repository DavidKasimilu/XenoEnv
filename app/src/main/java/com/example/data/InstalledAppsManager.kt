package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.data.models.InstalledAppItem

object InstalledAppsManager {

    fun getInstalledApps(context: Context): List<InstalledAppItem> {
        val pm = context.packageManager
        val myPackage = context.packageName

        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveList = pm.queryIntentActivities(mainIntent, 0)
        val seen = mutableSetOf<String>()
        val result = mutableListOf<InstalledAppItem>()

        for (ri in resolveList) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == myPackage || seen.contains(pkg)) continue
            seen.add(pkg)

            val label = try {
                ri.loadLabel(pm).toString()
            } catch (_: Exception) {
                pkg
            }

            val icon = try {
                ri.loadIcon(pm)
            } catch (_: Exception) {
                null
            }

            result.add(
                InstalledAppItem(
                    packageName = pkg,
                    appName = label,
                    icon = icon
                )
            )
        }

        return result.sortedBy { it.appName.lowercase() }
    }

    fun launchInstalledApp(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
