package com.cruciblelab.trafficlogger.util

import com.cruciblelab.trafficlogger.data.MatchType
import com.cruciblelab.trafficlogger.data.PackageVerdict
import com.cruciblelab.trafficlogger.data.ReputationVerdict

/**
 * Ana Sayfa ve trafik listesinde bir uygulamayı tek bakışta anlaşılır bir kovaya (bucket)
 * yerleştirir. Öncelik sırası kasıtlı: bir FLAGGED eşleşmesi sistem/güvenilir etiketini her
 * zaman ezer (kullanıcı kendi yüklediği bir veritabanıyla bir sistem uygulamasını işaretlediyse
 * bu bilgi gizlenmemeli).
 */
object AppCategoryClassifier {

    enum class Category { SYSTEM, TRUSTED, UNKNOWN, FLAGGED, SIGNATURE_MISMATCH }

    fun classify(
        packageName: String,
        isSystemApp: Boolean,
        activeVerdicts: List<PackageVerdict>,
        signatureMismatch: Boolean = false,
        /**
         * Paket Play Store'dan mı kuruldu (bkz. [PackageClassifier.isInstalledFromTrustedStore]).
         * Bir TRUSTED eşleşmesi (özellikle PREFIX tabanlı geniş kurallar - "com.microsoft." gibi)
         * yalnızca paket ADINA bakar; sideload edilmiş bir APK aynı adı taklit edebilir. Bu yüzden
         * TRUSTED rozeti yalnızca mağaza doğrulamalı kurulumlarda gösterilir - sideload edilmiş
         * bir eşleşme sessizce güvenilir sayılmaz, UNKNOWN'a düşer (kullanıcı isterse kendi özel
         * veritabanıyla elle işaretleyebilir). Varsayılan true: bu parametreyi geçmeyen eski
         * çağrı yerleri davranışı aniden değiştirmesin diye - ama [MainViewModel] artık gerçek
         * değeri geçiyor.
         */
        installedFromTrustedStore: Boolean = true
    ): Category {
        val match = activeVerdicts.firstOrNull { entry -> matches(entry, packageName) }
        return when {
            // Kanıtlanmış bir imza değişikliği, itibar veritabanındaki herhangi bir eşleşmeden
            // (hatta "sistem uygulaması" bayrağından) daha güvenilir bir sinyaldir - bu yüzden
            // en üstte. Bir paket adının sistem/güvenilir görünmesi, imzası değiştiyse artık
            // hiçbir şey ifade etmez.
            signatureMismatch -> Category.SIGNATURE_MISMATCH
            match?.verdict == ReputationVerdict.FLAGGED -> Category.FLAGGED
            isSystemApp -> Category.SYSTEM
            match?.verdict == ReputationVerdict.TRUSTED && installedFromTrustedStore -> Category.TRUSTED
            else -> Category.UNKNOWN
        }
    }

    private fun matches(entry: PackageVerdict, packageName: String): Boolean = when (entry.matchType) {
        MatchType.EXACT -> entry.packageName == packageName
        MatchType.PREFIX -> packageName.startsWith(entry.packageName)
    }
}
