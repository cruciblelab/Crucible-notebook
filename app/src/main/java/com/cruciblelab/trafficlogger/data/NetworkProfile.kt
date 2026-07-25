package com.cruciblelab.trafficlogger.data

/**
 * JSON'dan yüklenen bir ağ profili ("Bankacılık Modu" gibi - bu sadece bir örnek, uygulama
 * dahili hiçbir profille gelmez). Kullanıcı kendi profilini arayüzden oluşturur ya da JSON
 * içe aktarır; profiller DB'de tutulur (bkz. [ProfileEntity], [ProfileRepository]).
 *
 * ÖNEMLİ GÜVENLİK VARSAYIMI - "fail closed": [defaultPolicy] DENY olduğunda, izin
 * listesinde olmayan HER ŞEY (uygulama, çözülemeyen/bilinmeyen domain) varsayılan olarak
 * ENGELLENİR. Bu kasıtlı: bir kısıtlama profilinde "emin olamadığımda geçir" değil,
 * "emin olamadığımda engelle" davranışı güvenlik açısından doğru olan taraf.
 */
data class NetworkProfile(
    val id: String,
    val name: String,
    val defaultPolicy: DefaultPolicy,
    /** DENY modunda, ağ erişimine izin verilen tek uygulamalar (paket adları). */
    val allowedPackages: Set<String>,
    /**
     * Belirli bir uygulama için (örn. tarayıcı) ek domain kısıtlaması. Uygulama
     * [allowedPackages] içinde olsa bile, burada bir kaydı varsa SADECE listelenen
     * domain'lere (ve alt domainlerine) bağlanabilir.
     */
    val domainRestrictions: Map<String, Set<String>>,
    /**
     * Bir bağlantının domain'i bilinmiyorsa (DNS bu VPN üzerinden geçmediyse - örn.
     * DoH/DoT, ya da uygulama IP'yi doğrudan biliyorsa) ne yapılacağı. Varsayılan BLOCK:
     * domain kısıtlaması olan bir uygulamada domain doğrulanamıyorsa bağlantıyı reddet.
     * Bilerek ALLOW yapmak, DoH gibi yollarla kısıtlamanın atlanabilmesi anlamına gelir -
     * bu yüzden JSON'da explicit olarak belirtilmediği sürece BLOCK kullanılır.
     */
    val unknownDomainPolicy: UnknownDomainPolicy = UnknownDomainPolicy.BLOCK
) {
    enum class DefaultPolicy { ALLOW, DENY }
    enum class UnknownDomainPolicy { BLOCK, ALLOW }

    companion object {
        /**
         * Beklenen JSON şeması:
         * {
         *   "name": "Örnek profilim",
         *   "defaultPolicy": "DENY",
         *   "allowedPackages": ["com.android.chrome", "com.example.app"],
         *   "domainRestrictions": {
         *     "com.android.chrome": ["example.com"]
         *   },
         *   "unknownDomainPolicy": "BLOCK"
         * }
         * "id" bilerek şemada yok/opsiyonel: gerçek kimlik DB satır id'si (bkz.
         * ProfileRepository) - kullanıcının içe aktardığı JSON'da olsa bile yok sayılır.
         */
        fun fromJson(json: org.json.JSONObject): NetworkProfile {
            val id = json.optString("id", "").trim()

            val allowedPackages = mutableSetOf<String>()
            json.optJSONArray("allowedPackages")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val pkg = arr.optString(i)?.trim()?.lowercase()
                    if (!pkg.isNullOrBlank()) allowedPackages.add(pkg)
                }
            }

            val domainRestrictions = mutableMapOf<String, Set<String>>()
            json.optJSONObject("domainRestrictions")?.let { obj ->
                obj.keys().forEach { pkg ->
                    val domains = mutableSetOf<String>()
                    obj.optJSONArray(pkg)?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val d = arr.optString(i)?.trim()?.lowercase()
                            if (!d.isNullOrBlank()) domains.add(d)
                        }
                    }
                    if (domains.isNotEmpty()) domainRestrictions[pkg.trim().lowercase()] = domains
                }
            }

            val defaultPolicy = runCatching {
                DefaultPolicy.valueOf(json.optString("defaultPolicy", "DENY").trim().uppercase())
            }.getOrDefault(DefaultPolicy.DENY)

            val unknownDomainPolicy = runCatching {
                UnknownDomainPolicy.valueOf(json.optString("unknownDomainPolicy", "BLOCK").trim().uppercase())
            }.getOrDefault(UnknownDomainPolicy.BLOCK)

            return NetworkProfile(
                id = id,
                name = json.optString("name", id),
                defaultPolicy = defaultPolicy,
                allowedPackages = allowedPackages,
                domainRestrictions = domainRestrictions,
                unknownDomainPolicy = unknownDomainPolicy
            )
        }
    }

    /** [fromJson]'un tersi - bu profili DB'de saklanacak JSON metnine çevirir. */
    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("name", name)
        put("defaultPolicy", defaultPolicy.name)
        put("allowedPackages", org.json.JSONArray(allowedPackages.toList()))
        put("domainRestrictions", org.json.JSONObject().apply {
            domainRestrictions.forEach { (pkg, domains) -> put(pkg, org.json.JSONArray(domains.toList())) }
        })
        put("unknownDomainPolicy", unknownDomainPolicy.name)
    }
}
