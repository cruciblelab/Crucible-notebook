package com.cruciblelab.trafficlogger.update

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val versionCode: Long,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val apkSizeBytes: Long
)

/**
 * Uygulamanın kendi GitHub deposundaki "latest" release'i okur (CI main'e her push'ta bir tane
 * yayınlar - bkz. .github/workflows/android-build.yml). Anahtarsız, herkese açık API; ek
 * bağımlılık yok (HttpURLConnection + org.json).
 */
object GithubReleases {

    const val USER_AGENT = "CanliAgTrafigi-Android"
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/cruciblelab/Crucible-notebook/releases/latest"

    // Etiket "v0.2.57" biçiminde; son sayı CI build numarası = versionCode (bkz. app/build.gradle.kts).
    private val TRAILING_NUMBER = Regex("(\\d+)$")

    class RateLimitedException : IOException()

    /** null = henüz APK içeren yayınlanmış bir sürüm yok. */
    fun fetchLatest(): ReleaseInfo? {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> Unit
                HttpURLConnection.HTTP_NOT_FOUND -> return null
                HttpURLConnection.HTTP_FORBIDDEN, 429 -> throw RateLimitedException()
                else -> throw IOException("HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parse(JSONObject(body))
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(json: JSONObject): ReleaseInfo? {
        val tag = json.optString("tag_name")
        val versionCode = TRAILING_NUMBER.find(tag)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val assets = json.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?: return null
        return ReleaseInfo(
            versionCode = versionCode,
            versionName = tag.removePrefix("v"),
            // optString, JSON null için "null" metnini döndürür - boş notu ayrıca ele alıyoruz.
            notes = if (json.isNull("body")) "" else json.getString("body").trim(),
            apkUrl = apk.getString("browser_download_url"),
            apkSizeBytes = apk.optLong("size")
        )
    }
}
