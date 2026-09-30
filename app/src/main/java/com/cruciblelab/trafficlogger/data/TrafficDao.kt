package com.cruciblelab.trafficlogger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Aggregated usage for one app, used for the daily data-limit check. */
data class AppUsage(
    val appPackageName: String,
    val appLabel: String,
    val totalBytes: Long
)

@Dao
interface TrafficDao {

    @Query("SELECT * FROM traffic_entries ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TrafficEntry>>

    @Query("SELECT * FROM traffic_entries WHERE id = :id")
    fun observeById(id: Long): Flow<TrafficEntry?>

    /**
     * [observeById]'nin Flow olmayan, tek seferlik hâli - coalesce (satır birleştirme)
     * mantığı bir satırı yeniden kullanmadan önce mevcut byte/bağlantı sayısını okumak için
     * kullanır.
     */
    @Query("SELECT * FROM traffic_entries WHERE id = :id")
    suspend fun getByIdOnce(id: Long): TrafficEntry?

    @Query(
        "SELECT * FROM traffic_entries WHERE appPackageName = :packageName " +
            "AND ifnull(domain, '') = ifnull(:domain, '') AND destIp = :destIp " +
            "ORDER BY timestamp DESC"
    )
    fun observeConnectionHistory(packageName: String, domain: String?, destIp: String): Flow<List<TrafficEntry>>

    @Insert
    suspend fun insert(entry: TrafficEntry): Long

    @Update
    suspend fun update(entry: TrafficEntry)

    @Query("DELETE FROM traffic_entries WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long)

    /** Per-app byte totals since [sinceTimestamp], used to evaluate the daily usage limit. */
    @Query(
        "SELECT appPackageName, appLabel, SUM(bytesUp + bytesDown) as totalBytes " +
            "FROM traffic_entries WHERE timestamp >= :sinceTimestamp " +
            "GROUP BY appPackageName"
    )
    suspend fun usageSince(sinceTimestamp: Long): List<AppUsage>

    @Query("DELETE FROM traffic_entries")
    suspend fun clearAll()
}
