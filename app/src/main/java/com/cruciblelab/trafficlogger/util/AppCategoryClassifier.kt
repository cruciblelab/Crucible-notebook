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

    enum class Category { SYSTEM, TRUSTED, UNKNOWN, FLAGGED }

    fun classify(
        packageName: String,
        isSystemApp: Boolean,
        activeVerdicts: List<PackageVerdict>
    ): Category {
        val match = activeVerdicts.firstOrNull { entry -> matches(entry, packageName) }
        return when {
            match?.verdict == ReputationVerdict.FLAGGED -> Category.FLAGGED
            isSystemApp -> Category.SYSTEM
            match?.verdict == ReputationVerdict.TRUSTED -> Category.TRUSTED
            else -> Category.UNKNOWN
        }
    }

    private fun matches(entry: PackageVerdict, packageName: String): Boolean = when (entry.matchType) {
        MatchType.EXACT -> entry.packageName == packageName
        MatchType.PREFIX -> packageName.startsWith(entry.packageName)
    }
}
