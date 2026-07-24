package com.cruciblelab.trafficlogger.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Bir paketin cihaza önceden yüklenmiş (sistem/OEM) bir uygulama mı yoksa kullanıcının
 * kendisinin kurduğu bir uygulama mı olduğunu söyler. Bu, "sistem apps ayrı, bilinmeyen
 * uygulamalar ayrı" kategorizasyonunun temelidir - kayıtlı trafiği olan her paket için ucuz
 * ve cihaz-yerel bir kontrol (ağ çağrısı yok).
 */
class PackageClassifier(context: Context) {

    private val packageManager: PackageManager = context.applicationContext.packageManager
    private val cache = ConcurrentHashMap<String, Boolean>()

    fun isSystemApp(packageName: String): Boolean = cache.getOrPut(packageName) {
        try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            // Artık yüklü değil (kaldırılmış) - sistem uygulaması olmadığını varsay.
            false
        }
    }
}
