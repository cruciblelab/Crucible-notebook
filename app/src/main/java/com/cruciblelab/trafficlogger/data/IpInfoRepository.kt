package com.cruciblelab.trafficlogger.data

import com.cruciblelab.trafficlogger.net.IpInfoResolver
import java.util.concurrent.TimeUnit

class IpInfoRepository(private val dao: IpInfoDao) {

    /**
     * Returns cached info if it's fresh, otherwise resolves it over the
     * network (or synthesizes a "local network" entry for private/reserved
     * ranges so we never waste a request on those).
     */
    suspend fun getOrFetch(ip: String): IpInfoCache {
        val cached = dao.get(ip)
        val isFresh = cached != null &&
            cached.success &&
            (System.currentTimeMillis() - cached.fetchedAt) < TTL_MS
        if (isFresh) return cached!!

        if (isPrivateOrReserved(ip)) {
            val local = IpInfoCache(
                ip = ip,
                asn = null,
                org = null,
                isp = null,
                countryCode = null,
                countryName = "Yerel ağ",
                city = null,
                fetchedAt = System.currentTimeMillis(),
                success = true
            )
            dao.upsert(local)
            return local
        }

        val resolved = IpInfoResolver.fetch(ip)
        dao.upsert(resolved)
        return resolved
    }

    private fun isPrivateOrReserved(ip: String): Boolean {
        val parts = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return ip == "::1" || ip.startsWith("fe80") || ip.startsWith("fc") || ip.startsWith("fd")
        val (a, b) = parts
        return when {
            a == 10 -> true
            a == 127 -> true
            a == 169 && b == 254 -> true
            a == 172 && b in 16..31 -> true
            a == 192 && b == 168 -> true
            a == 100 && b in 64..127 -> true // CGNAT
            else -> false
        }
    }

    companion object {
        val TTL_MS = TimeUnit.DAYS.toMillis(30)
    }
}
