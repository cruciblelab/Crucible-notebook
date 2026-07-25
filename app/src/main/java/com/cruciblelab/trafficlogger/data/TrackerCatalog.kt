package com.cruciblelab.trafficlogger.data

/**
 * Teknik bilgisi olmayan bir kullanıcının bile anlayacağı, şirket/organizasyon bazlı
 * "bilinen veri toplayıcılar" listesi. Her [Company] iki seviyeli bir engelleme sunar:
 *
 * 1) [trackingDomains] - varsayılan/basit seviye: SADECE o şirketin reklam/analitik/izleme
 *    amaçlı uçları. Kapatınca (switch OFF) uygulamalar bozulmaz, sadece arka plan telemetrisi
 *    kesilir.
 * 2) [fullBlockDomains] - "teknik detay"a girip bilerek açılan ileri düzey seçenek: o şirkete
 *    ait TÜM bilinen domain'ler. Bu, örneğin Google'ı tamamen kesmek isteyen birine göredir -
 *    ama bu Gmail/Arama/YouTube gibi asıl uygulama işlevini de keser. Bu yüzden UI'da ayrı,
 *    daha belirgin bir uyarıyla sunulmalı, ana switch ile karıştırılmamalı.
 *
 * NEDEN GERÇEK ZAMANLI ASN BAZLI DEĞİL: paket işleme yolunda (yeni bağlantı anında) IP'nin
 * ASN/organizasyon bilgisini öğrenmek bir ağ isteği (ipwho.is) gerektirir - bu, canlı trafiği
 * saniyelerce kilitler. ASN bilgisi bu yüzden sadece GÖRÜNTÜLEME amaçlı, arka planda ve
 * önbellekli olarak çözülüyor (bkz. IpInfoResolver), engelleme kararı ANINDA verilmesi gereken
 * bir şey. Bu yüzden engelleme domain adına (SNI/DNS'ten anında bilinen) dayanıyor - offline
 * bir IP-aralığı/ASN veritabanı (örn. MaxMind benzeri) bundle edilmeden gerçek ASN bazlı anlık
 * engelleme yapılamaz; bu ayrı ve çok daha büyük bir özellik olurdu.
 */
object TrackerCatalog {

    data class Company(
        val key: String,
        /** Sade, teknik olmayan başlık - Ana Sayfa'da görünür. */
        val title: String,
        /** Tek cümlelik, teknik olmayan açıklama. */
        val simpleDescription: String,
        /** Basit seviye: sadece izleme/reklam/analitik uçları. */
        val trackingDomains: List<String>,
        /** İleri düzey: şirketin bilinen TÜM domain'leri (ana ürünleri de dahil). */
        val fullBlockDomains: List<String>
    )

    val ALL: List<Company> = listOf(
        Company(
            key = "google",
            title = "Google",
            simpleDescription = "Reklam ve kullanım istatistiği toplayan Google servisleri.",
            trackingDomains = listOf(
                "doubleclick.net",
                "googlesyndication.com",
                "googleadservices.com",
                "adservice.google.com",
                "google-analytics.com",
                "app-measurement.com",
                "crashlytics.com"
            ),
            fullBlockDomains = listOf(
                "google.com",
                "googleapis.com",
                "gstatic.com",
                "googleusercontent.com",
                "youtube.com",
                "ytimg.com",
                "gmail.com"
            )
        ),
        Company(
            key = "meta",
            title = "Meta (Facebook / Instagram)",
            simpleDescription = "Facebook/Instagram'ın diğer uygulamalar üzerinden seni izlemesini sağlayan uçlar.",
            trackingDomains = listOf(
                "graph.facebook.com",
                "connect.facebook.net",
                "an.facebook.com"
            ),
            fullBlockDomains = listOf(
                "facebook.com",
                "fbcdn.net",
                "instagram.com",
                "whatsapp.com",
                "messenger.com"
            )
        ),
        Company(
            key = "bytedance",
            title = "ByteDance (TikTok)",
            simpleDescription = "TikTok'un reklam ve analitik amaçlı izleme uçları.",
            trackingDomains = listOf(
                "analytics.tiktok.com",
                "ads.tiktok.com",
                "log.byteoversea.com"
            ),
            fullBlockDomains = listOf(
                "tiktok.com",
                "musical.ly",
                "byteoversea.com",
                "ibytedtos.com",
                "ibyteimg.com"
            )
        ),
        Company(
            key = "third_party_ad_networks",
            title = "Bağımsız Reklam Ağları",
            simpleDescription = "Oyun/uygulama içi reklam gösteren, tek bir büyük şirkete ait olmayan servisler (Unity, AppLovin, ironSource vb.).",
            trackingDomains = listOf(
                "unityads.unity3d.com",
                "applovin.com",
                "mopub.com",
                "chartboost.com",
                "vungle.com",
                "ironsrc.com",
                "adcolony.com",
                "moatads.com"
            ),
            fullBlockDomains = emptyList() // Bunlar zaten tek işlevli (reklam) servisler - "tam engel" ile "izleme engeli" aynı şey.
        ),
        Company(
            key = "third_party_analytics",
            title = "Bağımsız Analitik Servisleri",
            simpleDescription = "Uygulama geliştiricilerin kullanım verisi topladığı bağımsız servisler (Mixpanel, Amplitude, AppsFlyer vb.).",
            trackingDomains = listOf(
                "mixpanel.com",
                "amplitude.com",
                "segment.io",
                "appsflyer.com",
                "adjust.com",
                "branch.io",
                "flurry.com",
                "mparticle.com"
            ),
            fullBlockDomains = emptyList()
        )
    )
}
