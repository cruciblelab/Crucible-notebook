package com.cruciblelab.trafficlogger.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Bir paketin imzalayan sertifikasının SHA-256 özetini çıkarır.
 *
 * NEDEN BU ÖNEMLİ: paket adı (örn. "com.whatsapp") başlı başına bir güvence değildir - bir
 * cihaza sideload edilen sahte/değiştirilmiş bir APK aynı paket adını kullanabilir (gerçek
 * uygulama telefonda kurulu değilse Android bunu engellemez). İmzalayan sertifika ise
 * değiştirilemez: bir uygulamayı yeniden imzalamak orijinal private key olmadan mümkün
 * değildir. Bu yüzden "aynı paket adı + farklı sertifika" = neredeyse kesin bir taklit/
 * değiştirilmiş uygulama sinyalidir.
 *
 * Bilinen büyük uygulamalar için referans hash veritabanı BİLEREK burada YOK: yanlış/güncel
 * olmayan bir hash sabitlemek, gerçek uygulamayı yanlışlıkla şüpheli göstermekten (ya da daha
 * kötüsü, yanlış bir hash'e sessizce "eşleşti" diyerek yanlış güven vermekten) çok daha
 * risklidir. Bunun yerine "ilk görüşte kaydet, sonra değişirse alarm ver" (trust-on-first-use)
 * modeli kullanılır - bkz. [com.cruciblelab.trafficlogger.data.PackageIntegrityRepository].
 */
class SigningCertResolver(context: Context) {

    private val packageManager: PackageManager = context.applicationContext.packageManager

    /** Returns the SHA-256 hex digest of the app's (first) signing certificate, or null if unresolvable. */
    fun resolveSignatureHash(packageName: String): String? {
        return try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = info.signingInfo ?: return null
                // Bir uygulama "geçmiş imzalarla" (rotation) meşru şekilde yükseltilmiş
                // olabilir; en güncel imzayı kullanıyoruz. hasMultipleSigners() true ise
                // (birden çok bağımsız imzacı) bu zaten olağandışıdır, ilkini alıyoruz.
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures
            }

            val cert = signatures?.firstOrNull() ?: return null
            val digest = MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: Exception) {
            // Cihaza/OEM'e özgü PackageManager tuhaflıkları için sessizce vazgeç -
            // bütünlük kontrolü opsiyonel bir ek katman, ana trafik loglamayı bozmamalı.
            null
        }
    }
}
