package com.cruciblelab.trafficlogger.util

/**
 * Classifies an IP's resolved organization/ISP name (from [IpInfoResolver]) against a
 * curated list of well-known major cloud/CDN/tech companies.
 *
 * IMPORTANT ON TRUST: the org/ISP name here comes from ASN (Autonomous System Number)
 * registry data - who actually owns and announces that block of IP addresses on the
 * public internet (RIR/BGP routing records), resolved via ipwho.is. This is NOT a value
 * the destination server can just claim about itself over the connection; to appear as
 * e.g. "GOOGLE" an attacker would need to actually control IP space registered to and
 * BGP-announced by Google - a real, rare, and easily-detected attack (a BGP hijack), not
 * something a normal malicious app/server can fake. So a match here is a genuinely
 * reliable signal, not a spoofable label - unlike, say, an app-chosen display name.
 *
 * This is a light heuristic on top of that trustworthy data, purely for grouping/display:
 * it doesn't change what's shown (the exact org name is always still visible), it just
 * adds an "known major provider" category so a long tail of unfamiliar-looking domains
 * belonging to big, recognizable companies doesn't read as suspicious at a glance.
 */
object KnownOrgCategorizer {

    enum class Category(val displayName: String, val sharedInfrastructure: Boolean) {
        // sharedInfrastructure = true: herhangi biri (iyi ya da kötü niyetli) burada kiralık
        // sunucu/IP alabilir. Eşleşme yalnızca "kim barındırıyor" bilgisidir, hedefin o şirket
        // olduğu ya da güvenilir olduğu anlamına GELMEZ. UI bu ikisini asla aynı "onaylı"
        // görünümle göstermemeli.
        CLOUD_HOSTING("Bulut / Barındırma Devi", sharedInfrastructure = true),
        CDN("İçerik Dağıtım Ağı (CDN)", sharedInfrastructure = true),
        // sharedInfrastructure = true: bir VPN sağlayıcısının çıkış IP'si de tıpkı bulut/CDN
        // gibi paylaşımlıdır - o IP'den trafik görmek yalnızca "birileri bu VPN'i kullanıyor"
        // demektir, trafiğin kaynağının/sahibinin o VPN şirketi ya da güvenilir olduğu
        // anlamına GELMEZ. Kötü amaçlı bir uygulama, gerçek hedefini gizlemek için tam da
        // bilinen/itibarlı bir VPN sağlayıcısının altyapısını kullanabilir - bu kategori bunu
        // "onaylı" gibi göstermemek için var.
        VPN_PROVIDER("VPN Sağlayıcısı (paylaşımlı çıkış)", sharedInfrastructure = true),
        SOCIAL_MESSAGING("Sosyal Medya / Mesajlaşma", sharedInfrastructure = false),
        DEVICE_OEM("Cihaz Üreticisi Servisi", sharedInfrastructure = false),
        OTHER_MAJOR("Bilinen Büyük Şirket", sharedInfrastructure = false)
    }

    data class Match(val company: String, val category: Category)

    // Keyword -> (canonical company name, category). Matching is case-insensitive and
    // substring-based against both the "org" and "isp" fields, since registries format
    // these inconsistently (e.g. "GOOGLE", "Google LLC", "Google Cloud").
    private val KNOWN_ORGS: List<Triple<String, String, Category>> = listOf(
        Triple("google", "Google", Category.CLOUD_HOSTING),
        Triple("amazon", "Amazon (AWS)", Category.CLOUD_HOSTING),
        Triple("microsoft", "Microsoft (Azure)", Category.CLOUD_HOSTING),
        Triple("alibaba", "Alibaba Cloud", Category.CLOUD_HOSTING),
        Triple("tencent", "Tencent Cloud", Category.CLOUD_HOSTING),
        Triple("oracle", "Oracle Cloud", Category.CLOUD_HOSTING),
        Triple("digitalocean", "DigitalOcean", Category.CLOUD_HOSTING),
        Triple("hetzner", "Hetzner", Category.CLOUD_HOSTING),
        Triple("ovh", "OVH", Category.CLOUD_HOSTING),

        Triple("akamai", "Akamai", Category.CDN),
        Triple("cloudflare", "Cloudflare", Category.CDN),
        Triple("fastly", "Fastly", Category.CDN),
        Triple("edgecast", "Edgecast", Category.CDN),
        Triple("limelight", "Limelight", Category.CDN),

        Triple("facebook", "Meta / Facebook", Category.SOCIAL_MESSAGING),
        Triple("meta platforms", "Meta / Facebook", Category.SOCIAL_MESSAGING),
        Triple("whatsapp", "WhatsApp", Category.SOCIAL_MESSAGING),
        Triple("telegram", "Telegram", Category.SOCIAL_MESSAGING),
        Triple("twitter", "X / Twitter", Category.SOCIAL_MESSAGING),
        Triple("bytedance", "ByteDance / TikTok", Category.SOCIAL_MESSAGING),
        Triple("tiktok", "ByteDance / TikTok", Category.SOCIAL_MESSAGING),
        Triple("snap inc", "Snapchat", Category.SOCIAL_MESSAGING),

        Triple("xiaomi", "Xiaomi", Category.DEVICE_OEM),
        Triple("samsung", "Samsung", Category.DEVICE_OEM),
        Triple("huawei", "Huawei", Category.DEVICE_OEM),
        Triple("oppo", "OPPO", Category.DEVICE_OEM),
        Triple("apple", "Apple", Category.DEVICE_OEM),

        // Bilinen büyük VPN sağlayıcıları - eşleşme "bu trafiğin bir VPN çıkışından geldiği"
        // demektir, hedefin kendisinin veya barındırdığı uygulamanın güvenilir olduğu anlamına
        // GELMEZ (bkz. Category.VPN_PROVIDER dokümanı).
        Triple("nordvpn", "NordVPN", Category.VPN_PROVIDER),
        Triple("expressvpn", "ExpressVPN", Category.VPN_PROVIDER),
        Triple("surfshark", "Surfshark", Category.VPN_PROVIDER),
        Triple("protonvpn", "ProtonVPN", Category.VPN_PROVIDER),
        Triple("proton ag", "ProtonVPN", Category.VPN_PROVIDER),
        Triple("mullvad", "Mullvad", Category.VPN_PROVIDER),
        Triple("private internet access", "Private Internet Access", Category.VPN_PROVIDER),
        Triple("windscribe", "Windscribe", Category.VPN_PROVIDER),
        Triple("m247", "M247 (birçok VPN'in altyapısı)", Category.VPN_PROVIDER),
        Triple("datacamp", "DataCamp (birçok VPN'in altyapısı)", Category.VPN_PROVIDER),

        Triple("netflix", "Netflix", Category.OTHER_MAJOR),
        Triple("valve", "Valve / Steam", Category.OTHER_MAJOR),
        Triple("riot games", "Riot Games", Category.OTHER_MAJOR),
        Triple("epic games", "Epic Games", Category.OTHER_MAJOR)
    )

    /**
     * Returns a [Match] if the given org/isp string corresponds to a known major
     * company, or null if it looks like a small/unrecognized/unclassified provider
     * (which is not itself a red flag - most legitimate small businesses and self-hosted
     * servers will fall in this bucket too - just not one we have a friendly label for).
     */
    fun categorize(org: String?, isp: String?): Match? {
        val haystack = listOfNotNull(org, isp).joinToString(" ").lowercase()
        if (haystack.isBlank()) return null
        for ((keyword, company, category) in KNOWN_ORGS) {
            if (haystack.contains(keyword)) return Match(company, category)
        }
        return null
    }
}
