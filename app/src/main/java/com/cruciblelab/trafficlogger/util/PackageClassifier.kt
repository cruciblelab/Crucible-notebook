package com.cruciblelab.trafficlogger.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
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
    private val trustedStoreCache = ConcurrentHashMap<String, Boolean>()

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

    /**
     * Bir paketin bilinen bir uygulama mağazasından (şu an için Google Play) kurulup
     * kurulmadığını söyler.
     *
     * NEDEN ÖNEMLİ: itibar veritabanındaki "TRUSTED" eşleşmeleri (özellikle
     * [com.cruciblelab.trafficlogger.data.PresetReputationCatalog] gibi PREFIX tabanlı geniş
     * kurallar - örn. "com.microsoft.") salt paket ADINA bakıyor; paket adının kendisi
     * sideload edilen bir APK'da HERHANGİ bir şey olarak seçilebilir (örn. birisi
     * "com.microsoft.sahteapp" adıyla bir APK sideload edebilir ve bu, gerçek Microsoft
     * uygulaması değilken PREFIX kuralına takılıp "güvenilir" rozetini alırdı). Play Store
     * üzerinde ise paket adı + geliştirici hesabı eşleşmesi mağaza tarafından zaten
     * doğrulanıyor - yani "Play Store'dan kuruldu" bilgisi, bu spesifik taklit senaryosuna
     * karşı ucuz ve gerçek bir ek sinyal. Statik bir imza-hash listesi tutmuyoruz (bkz.
     * [SigningCertResolver] dokümanı - yanlış/güncel olmayan bir hash sabitlemek daha
     * riskli) - bunun yerine zaten var olan, cihazın kendisinin bildiği bu bilgiyi kullanıyoruz.
     *
     * SINIR: bu bir "kesin doğrulama" değil, bir ek sinyaldir - Play Store'un kendisi de
     * teorik olarak kötüye kullanılabilir, ve meşru bir uygulamanın APK'sını elle (F-Droid,
     * APK dosyası vb.) kurmuş olmak onu otomatik olarak şüpheli yapmaz. Bu yüzden
     * [com.cruciblelab.trafficlogger.util.AppCategoryClassifier] bunu TRUSTED rozetini
     * tamamen reddetmek için değil, sadece PREFIX/EXACT itibar eşleşmesini "mağaza
     * doğrulamalı" hale getirmek için kullanır - sideload edilmiş bir eşleşme sessizce
     * TRUSTED değil, UNKNOWN olarak görünür (kullanıcı yine de kendi özel veritabanıyla
     * onu elle FLAGGED/TRUSTED işaretleyebilir).
     */
    fun isInstalledFromTrustedStore(packageName: String): Boolean = trustedStoreCache.getOrPut(packageName) {
        try {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            }
            installer == "com.android.vending"
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            // OEM/cihaz tuhaflıkları için sessizce vazgeç - bu opsiyonel bir ek sinyal,
            // ana kategorizasyonu bozmamalı; bilinmiyorsa "mağazadan değil" varsayılır
            // (daha güvenli taraf - false negative, TRUSTED yerine UNKNOWN gösterir).
            false
        }
    }
}
