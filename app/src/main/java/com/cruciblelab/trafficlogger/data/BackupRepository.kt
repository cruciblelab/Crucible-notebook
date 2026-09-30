package com.cruciblelab.trafficlogger.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

data class BackupSummary(val rules: Int, val profiles: Int, val reputationSources: Int)

/**
 * Kullanıcının elle kurduğu her şeyi (kurallar, profiller, itibar listeleri, ayarlar) tek bir
 * JSON dosyasına yazar / o dosyadan geri yükler. Trafik geçmişi ve IP önbelleği dahil DEĞİL:
 * büyükler, zaten süreli tutuluyorlar ve yeniden oluşuyorlar.
 *
 * Satırlar orijinal id'leriyle yazılır; böylece aktif profil ve itibar girişlerinin kaynak
 * bağlantıları (sourceId) geri yüklemeden sonra da doğru kalır.
 */
class BackupRepository(
    private val database: AppDatabase,
    private val settings: SettingsRepository
) {
    private companion object {
        const val FORMAT = "canli-ag-trafigi-backup"
        const val FORMAT_VERSION = 1
    }

    private class Contents(
        val rules: List<BlockRule>,
        val profiles: List<ProfileEntity>,
        val sources: List<ReputationSource>,
        val entries: List<AppReputationEntry>,
        val settings: JSONObject
    )

    suspend fun export(output: OutputStream): BackupSummary {
        val rules = database.blockRuleDao().getAll()
        val profiles = database.profileDao().getAll()
        val sources = database.reputationDao().getAllSources()
        val entries = database.reputationDao().getAllEntries()

        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", FORMAT_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("settings", exportSettings())
            .put("rules", JSONArray(rules.map { it.toJson() }))
            .put("profiles", JSONArray(profiles.map { it.toJson() }))
            .put("reputationSources", JSONArray(sources.map { it.toJson() }))
            .put("reputationEntries", JSONArray(entries.map { it.toJson() }))

        output.bufferedWriter().use { it.write(root.toString(2)) }
        return BackupSummary(rules.size, profiles.size, sources.size)
    }

    /** Dosya hatalıysa hiçbir şeye dokunmadan exception fırlatır; mevcut veriler korunur. */
    suspend fun restore(input: InputStream): BackupSummary {
        val contents = parse(input.bufferedReader().use { it.readText() })

        database.withTransaction {
            database.blockRuleDao().deleteAll()
            database.blockRuleDao().insertAll(contents.rules)
            database.profileDao().deleteAll()
            database.profileDao().insertAll(contents.profiles)
            database.reputationDao().deleteAllEntries()
            database.reputationDao().deleteAllSources()
            database.reputationDao().insertSources(contents.sources)
            database.reputationDao().insertEntries(contents.entries)
        }
        restoreSettings(contents.settings, contents.profiles)
        return BackupSummary(contents.rules.size, contents.profiles.size, contents.sources.size)
    }

    private fun parse(text: String): Contents {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Dosya okunamadı: geçerli bir yedek dosyası değil.")
        }
        require(root.optString("format") == FORMAT) { "Bu dosya bir Canlı Ağ Trafiği yedeği değil." }
        require(root.optInt("version") <= FORMAT_VERSION) {
            "Bu yedek daha yeni bir sürümle alınmış. Önce uygulamayı güncelleyin."
        }
        return try {
            Contents(
                rules = root.objects("rules").map { it.toBlockRule() },
                profiles = root.objects("profiles").map { it.toProfile() },
                sources = root.objects("reputationSources").map { it.toReputationSource() },
                entries = root.objects("reputationEntries").map { it.toReputationEntry() },
                settings = root.optJSONObject("settings") ?: JSONObject()
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("Yedek dosyası bozuk ya da eksik; hiçbir şey değiştirilmedi.")
        }
    }

    private suspend fun exportSettings() = JSONObject()
        .put("retentionDays", settings.retentionDays.first())
        .put("dailyLimitMb", settings.dailyLimitMb.first())
        .put("blockKnownDoh", settings.blockKnownDoh.first())
        .put("autoUpdateCheck", settings.autoUpdateCheck.first())
        .put("autoStartVpn", settings.autoStartVpn.first())
        .put("activeProfileId", settings.activeProfileId.first() ?: JSONObject.NULL)

    private suspend fun restoreSettings(json: JSONObject, profiles: List<ProfileEntity>) {
        if (json.has("retentionDays")) settings.setRetentionDays(json.getInt("retentionDays"))
        if (json.has("dailyLimitMb")) settings.setDailyLimitMb(json.getInt("dailyLimitMb"))
        if (json.has("blockKnownDoh")) settings.setBlockKnownDoh(json.getBoolean("blockKnownDoh"))
        if (json.has("autoUpdateCheck")) settings.setAutoUpdateCheck(json.getBoolean("autoUpdateCheck"))
        if (json.has("autoStartVpn")) settings.setAutoStartVpn(json.getBoolean("autoStartVpn"))
        // Yedekteki aktif profil geri yüklenen profiller arasında yoksa kısıtlamayı kaldır;
        // aksi halde var olmayan bir profil yüzünden kilitli kalınabilirdi.
        val activeId = json.stringOrNull("activeProfileId")
        settings.setActiveProfileId(activeId?.takeIf { id -> profiles.any { it.id.toString() == id } })
    }

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

    private fun BlockRule.toJson() = JSONObject()
        .put("id", id)
        .put("type", type.name)
        .put("appPackageName", appPackageName ?: JSONObject.NULL)
        .put("appLabel", appLabel ?: JSONObject.NULL)
        .put("matchValue", matchValue)
        .put("createdAt", createdAt)

    private fun JSONObject.toBlockRule() = BlockRule(
        id = getLong("id"),
        type = RuleType.valueOf(getString("type")),
        appPackageName = stringOrNull("appPackageName"),
        appLabel = stringOrNull("appLabel"),
        matchValue = getString("matchValue"),
        createdAt = getLong("createdAt")
    )

    private fun ProfileEntity.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("json", json)
        .put("createdAt", createdAt)

    private fun JSONObject.toProfile() = ProfileEntity(
        id = getLong("id"),
        name = getString("name"),
        json = getString("json"),
        createdAt = getLong("createdAt")
    )

    private fun ReputationSource.toJson() = JSONObject()
        .put("id", id)
        .put("key", key)
        .put("name", name)
        .put("description", description)
        .put("isPreset", isPreset)
        .put("enabled", enabled)
        .put("autoBlock", autoBlock)
        .put("entryCount", entryCount)
        .put("importedAt", importedAt)

    private fun JSONObject.toReputationSource() = ReputationSource(
        id = getLong("id"),
        key = getString("key"),
        name = getString("name"),
        description = getString("description"),
        isPreset = getBoolean("isPreset"),
        enabled = getBoolean("enabled"),
        autoBlock = getBoolean("autoBlock"),
        entryCount = getInt("entryCount"),
        importedAt = getLong("importedAt")
    )

    private fun AppReputationEntry.toJson() = JSONObject()
        .put("id", id)
        .put("sourceId", sourceId)
        .put("packageName", packageName)
        .put("matchType", matchType.name)
        .put("verdict", verdict.name)
        .put("label", label ?: JSONObject.NULL)
        .put("note", note ?: JSONObject.NULL)

    private fun JSONObject.toReputationEntry() = AppReputationEntry(
        id = getLong("id"),
        sourceId = getLong("sourceId"),
        packageName = getString("packageName"),
        matchType = MatchType.valueOf(getString("matchType")),
        verdict = ReputationVerdict.valueOf(getString("verdict")),
        label = stringOrNull("label"),
        note = stringOrNull("note")
    )
}
