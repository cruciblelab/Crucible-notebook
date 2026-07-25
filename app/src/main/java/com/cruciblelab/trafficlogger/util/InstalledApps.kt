package com.cruciblelab.trafficlogger.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cihazda yüklü, ağ erişimi olabilecek uygulamaların listesini döner (etikete göre
 * alfabetik sıralı). Profil oluşturma ekranında "izinli uygulamalar" seçimi için
 * kullanılır - [ResolvedApp] zaten AppInfoResolver'da tanımlı, burada onu yeniden
 * kullanıyoruz ki traffic listesindeki uygulama satırlarıyla tutarlı olsun.
 *
 * Sistem uygulamalarını (kullanıcı bunları normalde göremez/seçmez) ve kendi
 * uygulamamızı listeden çıkarıyoruz - bir kısıtlama profilinde kendimizi
 * kısıtlamanın hiçbir anlamı yok.
 */
suspend fun loadInstalledApps(context: Context): List<ResolvedApp> = withContext(Dispatchers.IO) {
    val packageManager = context.applicationContext.packageManager
    val selfPackage = context.applicationContext.packageName

    val apps: List<ApplicationInfo> = try {
        packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
    } catch (e: Exception) {
        emptyList()
    }

    apps.asSequence()
        .filter { it.packageName != selfPackage }
        .filter { info ->
            // Kullanıcı tarafından yüklenmiş VEYA güncellenmiş bir sistem uygulaması
            // (örn. Chrome, Google Play Services'in bazı sürümleri) - saf sistem
            // bileşenlerini (ör. com.android.systemui) listeden çıkarır, uzun bir
            // kaydırma listesinin kullanıcı için gerçekten anlamlı olan kısmını gösterir.
            (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }
        .map { info ->
            val label = try {
                packageManager.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                info.packageName
            }
            ResolvedApp(packageName = info.packageName, label = label)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
        .toList()
}
