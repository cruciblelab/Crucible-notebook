package com.cruciblelab.trafficlogger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * TRUSTED = "bu paket bilinen/tanınan bir yayıncıya ait" (olumlu, kanıtlanabilir bir eşleşme).
 * FLAGGED = "bu paket, yüklenen veritabanına göre şüpheli/istenmeyen olarak işaretli".
 *
 * Kasıtlı olarak "MALICIOUS" değil "FLAGGED" adlandırıldı: bu uygulama hiçbir paket hakkında
 * kesin bir "kötü amaçlı yazılım" iddiasında bulunmaz - sadece kullanıcının kendi yüklediği
 * veritabanındaki işaretlemeyi gösterir. Sorumluluk ve doğruluk, veritabanını seçen/yükleyen
 * kullanıcıdadır.
 */
enum class ReputationVerdict { TRUSTED, FLAGGED }

/** EXACT: paket adı birebir eşleşmeli. PREFIX: paket adı bu değerle başlamalı (örn. "com.miui."). */
enum class MatchType { EXACT, PREFIX }

/**
 * Bir "itibar veritabanı": ya uygulamayla birlikte gelen hazır bir preset (varlık/asset
 * dosyasından yüklenir) ya da kullanıcının kendi içe aktardığı özel bir liste.
 *
 * [enabled] = bu kaynaktaki girişler kategorizasyonda (Ana Sayfa / liste rozetleri) dikkate
 * alınsın mı. [autoBlock] = bu kaynaktaki FLAGGED girişlerin trafiği gerçekten engellensin mi
 * (yalnızca EXACT eşleşmeli girişler için etkilidir, bkz. ReputationRepository).
 */
@Entity(tableName = "reputation_sources")
data class ReputationSource(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val key: String,
    val name: String,
    val description: String,
    val isPreset: Boolean,
    val enabled: Boolean = true,
    val autoBlock: Boolean = false,
    val entryCount: Int = 0,
    val importedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "app_reputation_entries",
    indices = [Index("packageName"), Index("sourceId")]
)
data class AppReputationEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceId: Long,
    /** Her zaman küçük harfe çevrilmiş halde saklanır. */
    val packageName: String,
    val matchType: MatchType,
    val verdict: ReputationVerdict,
    val label: String? = null,
    val note: String? = null
)

/** DAO projeksiyonu: aktif kaynaklardan derlenmiş (paket, hüküm, eşleşme türü) listesi. */
data class PackageVerdict(
    val packageName: String,
    val verdict: ReputationVerdict,
    val matchType: MatchType
)
