package com.cruciblelab.trafficlogger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached IP -> ASN / organization / country lookup result.
 *
 * The bundled RIR statistics file only maps IP blocks to a country (no ASN
 * linkage), so real ASN/organization names are resolved lazily over the
 * network the first time an IP is seen, then cached here indefinitely
 * (refreshed after [IpInfoRepository.TTL_MS]) so we never re-query the same
 * address on every screen refresh.
 */
@Entity(tableName = "ip_info_cache")
data class IpInfoCache(
    @PrimaryKey val ip: String,
    val asn: Int?,
    val org: String?,
    val isp: String?,
    val countryCode: String?,
    val countryName: String?,
    val city: String?,
    val fetchedAt: Long,
    val success: Boolean
)
