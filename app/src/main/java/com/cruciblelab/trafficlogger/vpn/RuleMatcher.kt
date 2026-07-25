package com.cruciblelab.trafficlogger.vpn

import com.cruciblelab.trafficlogger.data.BlockRule
import com.cruciblelab.trafficlogger.data.NetworkProfile
import com.cruciblelab.trafficlogger.data.RuleType
import java.util.concurrent.atomic.AtomicReference

/**
 * In-memory snapshot of the block/allow rules, refreshed whenever [update] is called
 * (the VpnService keeps this in sync with the DB via a Flow collector). Kept separate
 * from the DB so the hot packet path never touches Room directly.
 *
 * Matching is per (app, domain-or-ip) pair, not "block the whole domain everywhere":
 * a rule with a non-null [BlockRule.appPackageName] only ever matches that one app.
 * Domain matching allows subdomains of a blocked domain to match too (blocking
 * "doubleclick.net" also blocks "googleads.g.doubleclick.net"), which keeps rules
 * created from a single row effective even if the same tracker resolves to a
 * slightly different subdomain next time.
 *
 * A [NetworkProfile] (e.g. "Bankacılık Modu") sits *in front of* the manual/reputation
 * rules as a hard gate: if a profile with DENY default policy is active, an app/domain
 * has to pass the profile check first before the normal blacklist/whitelist logic even
 * runs. This is deliberately fail-closed - see [NetworkProfile] doc comment.
 */
class RuleMatcher {

    private val rulesRef = AtomicReference<List<BlockRule>>(emptyList())
    private val profileRef = AtomicReference<NetworkProfile?>(null)

    fun update(rules: List<BlockRule>) {
        rulesRef.set(rules)
    }

    /** Aktif kısıtlama profilini günceller. null = profil kapalı, normal davranışa dön. */
    fun updateProfile(profile: NetworkProfile?) {
        profileRef.set(profile)
    }

    /** Returns true if this specific app+destination should be blocked. */
    fun isBlocked(appPackageName: String, domain: String?, destIp: String): Boolean {
        val profile = profileRef.get()
        if (profile != null && profile.defaultPolicy == NetworkProfile.DefaultPolicy.DENY) {
            if (profileBlocks(profile, appPackageName, domain)) return true
        }

        val rules = rulesRef.get()
        if (rules.isEmpty()) return false

        val whitelisted = rules.any { it.type == RuleType.WHITELIST && matches(it, appPackageName, domain, destIp) }
        if (whitelisted) return false

        return rules.any { it.type == RuleType.BLACKLIST && matches(it, appPackageName, domain, destIp) }
    }

    /**
     * true döner = profil bu bağlantıyı kesin olarak engelliyor (manuel kurallara bile
     * bakılmaz). Kısıtlama profili, kullanıcının normal moddaki whitelist kurallarını
     * genişletmek için değil, geçici olarak daraltmak için var.
     */
    private fun profileBlocks(profile: NetworkProfile, appPackageName: String, domain: String?): Boolean {
        // 1) Uygulama izin listesinde değilse, hiç tartışmasız engellenir.
        if (appPackageName !in profile.allowedPackages) return true

        // 2) Uygulama izinliyse ama bu uygulama için domain kısıtlaması tanımlıysa
        //    (örn. tarayıcı sadece banka domain'lerine gidebilir), domain kontrolü yapılır.
        val allowedDomains = profile.domainRestrictions[appPackageName] ?: return false

        if (domain == null) {
            // DNS bu VPN'den geçmediyse (DoH/DoT, ya da uygulama IP'yi zaten biliyorsa)
            // domain bilinmiyordur. Varsayılan (BLOCK) burada "emin olamadığımda
            // engelle" ilkesini uygular - aksi halde kısıtlama IP üzerinden atlanabilir.
            return profile.unknownDomainPolicy == NetworkProfile.UnknownDomainPolicy.BLOCK
        }

        val isAllowedDomain = allowedDomains.any { allowed ->
            domain.equals(allowed, ignoreCase = true) || domain.endsWith(".$allowed", ignoreCase = true)
        }
        return !isAllowedDomain
    }

    private fun matches(rule: BlockRule, appPackageName: String, domain: String?, destIp: String): Boolean {
        if (rule.appPackageName != null && rule.appPackageName != appPackageName) return false

        // "*" is a wildcard used for reputation-database-driven auto-block rules (see
        // RuleRepository): it means "block this app entirely", not tied to one domain/IP.
        if (rule.matchValue == "*") return true

        if (rule.matchValue == destIp) return true

        val d = domain ?: return false
        return d.equals(rule.matchValue, ignoreCase = true) ||
            d.endsWith(".${rule.matchValue}", ignoreCase = true)
    }
}
