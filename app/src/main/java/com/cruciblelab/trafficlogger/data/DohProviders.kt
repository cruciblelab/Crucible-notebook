package com.cruciblelab.trafficlogger.data

/**
 * Bilinen genel DoH (DNS-over-HTTPS) sağlayıcılarının domain ve IP adresleri.
 *
 * Faz 2'nin "kaba ama basit" alt yolu: SNI ayrıştırma (bkz. [com.cruciblelab.trafficlogger.vpn.TlsSni])
 * bilinmeyen/özel bir DoH sunucusunu da domain bazlı kurallarla yakalayabilir, ama bir
 * uygulama IP'ye doğrudan bağlanıyorsa (SNI'siz TLS ya da DoH-over-HTTP/3/QUIC) domain
 * hiç görünmeyebilir. Bu liste, en yaygın herkese açık DoH sağlayıcılarının bilinen
 * domain/IP'lerini statik olarak eşleştirerek o durumu da kapatır.
 *
 * KAPSAMLI DEĞİLDİR: listede olmayan bir DoH sunucusu (örn. self-hosted, ya da bir
 * ISP'nin kendi DoH'u) bu şekilde yakalanmaz. IP'ler zamanla değişebilir/genişleyebilir -
 * liste elle güncellenmesi gereken, en iyi çaba (best-effort) bir kara liste.
 */
object DohProviders {

    val DOMAINS: Set<String> = setOf(
        "dns.google",
        "dns.google.com",
        "cloudflare-dns.com",
        "mozilla.cloudflare-dns.com",
        "family.cloudflare-dns.com",
        "security.cloudflare-dns.com",
        "dns.quad9.net",
        "doh.opendns.com",
        "doh.familyshield.opendns.com",
        "doh.cleanbrowsing.org",
        "family-filter-dns.cleanbrowsing.org",
        "adult-filter-dns.cleanbrowsing.org",
        "dns.adguard.com",
        "dns-family.adguard.com",
        "dns-unfiltered.adguard.com",
        "unfiltered.adguard-dns.com",
        "dns.adguard-dns.com",
        "doh.dns.sb",
        "dns.nextdns.io",
        "doh.libredns.gr",
        "doh.ffmuc.net"
    )

    val IPS: Set<String> = setOf(
        "8.8.8.8", "8.8.4.4", // Google Public DNS
        "1.1.1.1", "1.0.0.1", // Cloudflare
        "9.9.9.9", "149.112.112.112", // Quad9
        "208.67.222.222", "208.67.220.220", // OpenDNS
        "94.140.14.14", "94.140.15.15", // AdGuard
        "185.228.168.9", "185.228.169.9" // CleanBrowsing
    )

    fun isKnownDomain(domain: String?): Boolean {
        if (domain.isNullOrBlank()) return false
        val d = domain.trim().lowercase()
        return DOMAINS.any { d == it || d.endsWith(".$it") }
    }

    fun isKnownIp(destIp: String): Boolean = destIp in IPS
}
