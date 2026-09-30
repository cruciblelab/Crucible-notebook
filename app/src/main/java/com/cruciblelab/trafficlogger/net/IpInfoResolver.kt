package com.cruciblelab.trafficlogger.net

import com.cruciblelab.trafficlogger.data.IpInfoCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resolves an IP address to its ASN / organization / country using a free,
 * key-less HTTPS lookup (ipwho.is). Uses only [HttpURLConnection] and
 * [org.json], both part of the Android platform, to keep the project free
 * of third-party dependencies as required by its architecture.
 */
object IpInfoResolver {

    suspend fun fetch(ip: String): IpInfoCache = withContext(Dispatchers.IO) {
        try {
            val connection = (URL("https://ipwho.is/$ip").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6_000
                readTimeout = 6_000
            }
            val code = connection.responseCode
            if (code !in 200..299) return@withContext failure(ip)

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (!json.optBoolean("success", true)) return@withContext failure(ip)

            val connectionInfo = json.optJSONObject("connection")
            val asn = connectionInfo?.let { if (it.has("asn") && !it.isNull("asn")) it.optInt("asn") else null }

            IpInfoCache(
                ip = ip,
                asn = asn,
                org = connectionInfo?.optString("org")?.takeIf { it.isNotBlank() },
                isp = connectionInfo?.optString("isp")?.takeIf { it.isNotBlank() },
                countryCode = json.optString("country_code").takeIf { it.isNotBlank() },
                countryName = json.optString("country").takeIf { it.isNotBlank() },
                city = json.optString("city").takeIf { it.isNotBlank() },
                fetchedAt = System.currentTimeMillis(),
                success = true
            )
        } catch (e: Exception) {
            failure(ip)
        }
    }

    private fun failure(ip: String) = IpInfoCache(
        ip = ip,
        asn = null,
        org = null,
        isp = null,
        countryCode = null,
        countryName = null,
        city = null,
        fetchedAt = System.currentTimeMillis(),
        success = false
    )
}
