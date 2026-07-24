package com.cruciblelab.trafficlogger.data

import android.content.Context
import org.json.JSONArray

/**
 * Beklenen içe aktarma JSON formatı - bir dizi (array), her eleman:
 * {
 *   "package": "com.example.app",      // zorunlu, paket adı (veya PREFIX ise önek)
 *   "verdict": "FLAGGED" | "TRUSTED",  // zorunlu değil, varsayılan FLAGGED
 *   "matchType": "EXACT" | "PREFIX",   // zorunlu değil, varsayılan EXACT
 *   "label": "Görünen ad",             // isteğe bağlı
 *   "note": "Not"                      // isteğe bağlı
 * }
 */
class ReputationRepository(
    private val context: Context,
    private val dao: ReputationDao
) {
    fun observeSources() = dao.observeSources()

    fun observeAutoBlockPackages() = dao.observeAutoBlockPackages()

    fun observeActiveVerdicts() = dao.observeActiveVerdicts()

    suspend fun isPresetLoaded(preset: PresetReputationCatalog.Preset): Boolean =
        dao.allSourceKeys().contains(preset.key)

    suspend fun loadPreset(preset: PresetReputationCatalog.Preset) {
        val json = context.assets.open(preset.assetPath).bufferedReader().use { it.readText() }
        importInternal(
            key = preset.key,
            name = preset.name,
            description = preset.description,
            isPreset = true,
            json = json
        )
    }

    /** @return kaç giriş içe aktarıldığı. Format hatalıysa exception fırlatır. */
    suspend fun importCustom(name: String, description: String, json: String): Int =
        importInternal(
            key = "custom_${System.currentTimeMillis()}",
            name = name,
            description = description,
            isPreset = false,
            json = json
        )

    suspend fun setEnabled(source: ReputationSource, enabled: Boolean) =
        dao.updateSource(source.copy(enabled = enabled))

    suspend fun setAutoBlock(source: ReputationSource, autoBlock: Boolean) =
        dao.updateSource(source.copy(autoBlock = autoBlock))

    suspend fun delete(source: ReputationSource) {
        dao.deleteEntriesForSource(source.id)
        dao.deleteSource(source)
    }

    private suspend fun importInternal(
        key: String,
        name: String,
        description: String,
        isPreset: Boolean,
        json: String
    ): Int {
        val entries = parseEntries(json)
        require(entries.isNotEmpty()) { "Veritabanı boş görünüyor, geçerli bir giriş bulunamadı." }
        val sourceId = dao.insertSource(
            ReputationSource(
                key = key,
                name = name,
                description = description,
                isPreset = isPreset,
                entryCount = entries.size
            )
        )
        dao.insertEntries(entries.map { it.copy(sourceId = sourceId) })
        return entries.size
    }

    private fun parseEntries(json: String): List<AppReputationEntry> {
        val array = JSONArray(json)
        val out = mutableListOf<AppReputationEntry>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val pkg = obj.optString("package").trim().lowercase()
            if (pkg.isBlank()) continue

            val verdict = runCatching {
                ReputationVerdict.valueOf(obj.optString("verdict", "FLAGGED").trim().uppercase())
            }.getOrDefault(ReputationVerdict.FLAGGED)

            val matchType = runCatching {
                MatchType.valueOf(obj.optString("matchType", "EXACT").trim().uppercase())
            }.getOrDefault(MatchType.EXACT)

            out.add(
                AppReputationEntry(
                    sourceId = 0,
                    packageName = pkg,
                    matchType = matchType,
                    verdict = verdict,
                    label = if (obj.has("label") && !obj.isNull("label")) obj.getString("label") else null,
                    note = if (obj.has("note") && !obj.isNull("note")) obj.getString("note") else null
                )
            )
        }
        return out
    }
}
