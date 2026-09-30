package com.cruciblelab.trafficlogger.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class CompanyProtectionState(
    val trackingBlocked: Boolean,
    val fullyBlocked: Boolean
)

/**
 * "Ana Sayfa"daki basit aç/kapa switch'lerini ve "teknik detay"daki ileri düzey tam engelleme
 * seçeneğini, zaten var olan [BlockRule] mekanizmasına çevirir.
 *
 * İki seviye tamamen bağımsızdır: bir şirketi "tam engelle" yapmak, izleme uçlarını
 * engellemiş SAYMAZ (ve tersi) - domain listeleri kasıtlı olarak örtüşmüyor (örn.
 * "google-analytics.com" ayrı bir domain, "google.com"un alt alanı değil). Kullanıcı ikisini
 * de açık isterse ikisini de ayrı ayrı açmalı; bu bilinçli bir tasarım, üst üste binen/gizli
 * bir bağımlılık yok.
 */
class TrackerBlockRepository(private val dao: BlockRuleDao) {

    fun observeProtectionStates(): Flow<Map<String, CompanyProtectionState>> =
        dao.observeAll().map { rules ->
            val blockedDomains = rules
                .filter { it.appPackageName == null && it.type == RuleType.BLACKLIST }
                .map { it.matchValue }
                .toSet()
            TrackerCatalog.ALL.associate { company ->
                val trackingBlocked = company.trackingDomains.isNotEmpty() &&
                    company.trackingDomains.all { it in blockedDomains }
                val fullyBlocked = company.fullBlockDomains.isNotEmpty() &&
                    company.fullBlockDomains.all { it in blockedDomains }
                company.key to CompanyProtectionState(trackingBlocked, fullyBlocked)
            }
        }

    /** Ana Sayfa'daki basit switch: sadece izleme/reklam/analitik uçlarını açıp kapatır. */
    suspend fun setTrackingBlocked(company: TrackerCatalog.Company, blocked: Boolean) {
        applyDomains(company.trackingDomains, blocked)
    }

    /**
     * "Teknik detay"da bilerek açılan ileri düzey seçenek: şirketin TÜM domain'lerini
     * engeller - bu, o şirketin ana uygulamalarını/sitelerini de kullanılamaz hale getirir.
     * Çağıran taraf (UI) bunu ayrı, belirgin bir onay/uyarıyla sunmalı.
     */
    suspend fun setFullyBlocked(company: TrackerCatalog.Company, blocked: Boolean) {
        applyDomains(company.fullBlockDomains, blocked)
    }

    private suspend fun applyDomains(domains: List<String>, blocked: Boolean) {
        domains.forEach { domain ->
            if (blocked) {
                if (dao.findAppAgnostic(domain) == null) {
                    dao.insert(
                        BlockRule(
                            type = RuleType.BLACKLIST,
                            appPackageName = null,
                            appLabel = null,
                            matchValue = domain
                        )
                    )
                }
            } else {
                dao.deleteAppAgnostic(domain)
            }
        }
    }
}
