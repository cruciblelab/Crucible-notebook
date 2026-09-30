package com.cruciblelab.trafficlogger.data

/**
 * Uygulamayla birlikte gelen, kullanıcının tek dokunuşla yükleyebileceği hazır itibar
 * veritabanlarının kataloğu. Yalnızca *olumlu* (TRUSTED) bir preset içerir - bilinen büyük
 * yayıncılara/sistem bileşenlerine ait paket adları/önekleri, tamamen kanıtlanabilir ve
 * tartışmasız bir eşleşmedir.
 *
 * Kasıtlı olarak hazır bir "şüpheli/FLAGGED" preset veritabanı YOK: hangi uygulamaların
 * istenmeyen/takip amaçlı sayılacağı kullanıcıya/kuruma göre değişir, bu nedenle bu karar
 * uygulama tarafından önceden verilmez. Bunun yerine "Özel veritabanı içe aktar" akışıyla
 * kullanıcı kendi güvendiği kaynaktan (kendi derlediği bir liste, bir topluluk projesi vb.)
 * bir JSON dosyası yükleyip FLAGGED olarak işaretleyebilir ve isterse otomatik engellemeyi
 * açabilir.
 */
object PresetReputationCatalog {

    data class Preset(
        val key: String,
        val name: String,
        val description: String,
        val assetPath: String
    )

    val ALL: List<Preset> = listOf(
        Preset(
            key = "preset_trusted_publishers_v1",
            name = "Bilinen Güvenilir Yayıncılar",
            description = "Büyük işletim sistemi/cihaz üreticilerine (Google, Samsung, Xiaomi, Huawei, OPPO...) ve " +
                "yaygın popüler uygulamalara (WhatsApp, Telegram, Spotify...) ait paket adları. Bu uygulamalar " +
                "\"bilinmiyor\" rozetiyle işaretlenmez.",
            assetPath = "reputation/trusted_publishers.json"
        )
    )
}
