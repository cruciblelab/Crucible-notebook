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
    val blocked: Boolean = false
)
