package com.cruciblelab.trafficlogger.util

import android.content.Context
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

data class ResolvedApp(val packageName: String, val label: String)

/**
 * Resolves a Linux UID to the installed app that owns it, caching results
 * since PackageManager lookups are relatively expensive and UIDs repeat constantly.
 */
class AppInfoResolver(context: Context) {

    private val packageManager: PackageManager = context.applicationContext.packageManager
    private val cache = ConcurrentHashMap<Int, ResolvedApp>()

    fun resolve(uid: Int): ResolvedApp {
        cache[uid]?.let { return it }

        val packages = packageManager.getPackagesForUid(uid)
        val resolved = if (packages.isNullOrEmpty()) {
            ResolvedApp(packageName = "uid:$uid", label = "Bilinmeyen (UID $uid)")
        } else {
            val packageName = packages[0]
            val label = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            }
            ResolvedApp(packageName = packageName, label = label)
        }

        cache[uid] = resolved
        return resolved
    }
}
