package com.cruciblelab.trafficlogger.util

import android.content.Context
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

/**
 * [hasInternetPermission] varsayılan olarak true: bu sınıf hem trafik kayıtlarındaki
 * (zaten ağa çıkmış, dolayısıyla izni olan) uygulamalar için hem de profil oluşturma
 * ekranındaki tüm cihaz uygulamaları listesi için kullanılıyor - sadece ikincisi
 * [com.cruciblelab.trafficlogger.util.loadInstalledApps] içinde gerçek değeri hesaplayıp
 * bu alanı dolduruyor.
 */
data class ResolvedApp(
    val packageName: String,
    val label: String,
    val hasInternetPermission: Boolean = true
)

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
