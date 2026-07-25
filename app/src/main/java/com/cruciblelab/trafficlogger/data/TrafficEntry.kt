package com.cruciblelab.trafficlogger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Protocol { TCP, UDP }

enum class Direction { OUT, IN }

@Entity(tableName = "traffic_entries")
data class TrafficEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val appPackageName: String,
    val appLabel: String,
    val domain: String?,
    val destIp: String,
    val destPort: Int,
    val protocol: Protocol,
    val bytesUp: Long,
    val bytesDown: Long,
    val timestamp: Long,
    val direction: Direction,
    /** true if this connection was refused because it matched a blacklist rule. */
    val blocked: Boolean = false,
    /**
     * Kaç ayrı bağlantının bu satırda birleştirildiği (coalesce). Aynı (uygulama, domain)
     * çifti kısa bir pencere içinde (bkz. RelayContext.CONNECT_COALESCE_WINDOW_MS) art arda
     * yeni bağlantılar açtığında - CDN/video akışı gibi - her biri ayrı satır yerine bu satıra
     * eklenir. 1 = normal, tekil bağlantı.
     */
    val connectionCount: Int = 1
)
