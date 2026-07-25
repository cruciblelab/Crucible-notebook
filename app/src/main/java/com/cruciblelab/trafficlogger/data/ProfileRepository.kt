package com.cruciblelab.trafficlogger.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * Kullanıcının kendi oluşturduğu ağ profillerini yönetir (bkz. [NetworkProfile]).
 * Uygulama hiçbir hazır/dahili profille gelmez - "Bankacılık Modu" yalnızca bir örnekti.
 * Kullanıcı kendi profilini ya [create] ile (arayüzden doldurulan alanlardan) ya da
 * [importJson] ile (kendi hazırladığı bir JSON dosyasından) oluşturur. Motor tarafı
 * (RuleMatcher/VPN servisi) tamamen jenerik - hangi profillerin var olduğunu bilmez,
 * sadece "şu an aktif profil" ile ilgilenir.
 */
class ProfileRepository(
    private val dao: ProfileDao,
    private val settingsRepository: SettingsRepository
) {
    fun observeAll(): Flow<List<NetworkProfile>> = dao.observeAll().map { rows ->
        rows.mapNotNull { row -> runCatching { toDomain(row) }.getOrNull() }
    }

    /** Aktif profil, yoksa null (kısıtlama yok, normal - tam erişim - davranış). */
    val activeProfile: Flow<NetworkProfile?> =
        combine(observeAll(), settingsRepository.activeProfileId) { profiles, activeId ->
            profiles.firstOrNull { it.id == activeId }
        }

    suspend fun setActiveProfile(id: String?) = settingsRepository.setActiveProfileId(id)

    /** Arayüzden (izin verilen uygulamalar/domainler seçilerek) yeni bir profil oluşturur. */
    suspend fun create(
        name: String,
        defaultPolicy: NetworkProfile.DefaultPolicy,
        allowedPackages: Set<String>,
        domainRestrictions: Map<String, Set<String>> = emptyMap(),
        unknownDomainPolicy: NetworkProfile.UnknownDomainPolicy = NetworkProfile.UnknownDomainPolicy.BLOCK
    ): String {
        require(name.isNotBlank()) { "Profil adı boş olamaz." }
        require(allowedPackages.isNotEmpty() || defaultPolicy == NetworkProfile.DefaultPolicy.ALLOW) {
            "DENY politikalı bir profilde en az bir izinli uygulama olmalı, yoksa hiçbir şey çalışmaz."
        }
        val draft = NetworkProfile(
            id = "",
            name = name,
            defaultPolicy = defaultPolicy,
            allowedPackages = allowedPackages,
            domainRestrictions = domainRestrictions,
            unknownDomainPolicy = unknownDomainPolicy
        )
        val rowId = dao.upsert(ProfileEntity(name = name, json = draft.toJson().toString()))
        return rowId.toString()
    }

    /**
     * Kullanıcının kendi hazırladığı bir JSON metnini profil olarak içe aktarır (bkz.
     * [NetworkProfile.fromJson] için şema). Format hatalıysa exception fırlatır - çağıran
     * taraf bunu yakalayıp kullanıcıya göstermeli.
     */
    suspend fun importJson(json: String, nameOverride: String? = null): String {
        val parsed = NetworkProfile.fromJson(JSONObject(json)) // erken doğrulama
        val name = nameOverride?.takeIf { it.isNotBlank() } ?: parsed.name
        val rowId = dao.upsert(ProfileEntity(name = name, json = json))
        return rowId.toString()
    }

    suspend fun delete(id: String) {
        val rowId = id.toLongOrNull() ?: return
        dao.findById(rowId)?.let { dao.delete(it) }
        // Silinen profil o an aktifse, kısıtlamayı da kaldır - kullanıcı yanlışlıkla
        // "hiçbir uygulama çalışmıyor" durumunda kilitli kalmasın.
        if (settingsRepository.activeProfileId.first() == id) {
            settingsRepository.setActiveProfileId(null)
        }
    }

    private fun toDomain(row: ProfileEntity): NetworkProfile =
        NetworkProfile.fromJson(JSONObject(row.json)).copy(id = row.id.toString(), name = row.name)
}
