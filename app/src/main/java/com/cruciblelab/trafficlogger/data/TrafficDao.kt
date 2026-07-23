package com.cruciblelab.trafficlogger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrafficDao {

    @Query("SELECT * FROM traffic_entries ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TrafficEntry>>

    @Query("SELECT * FROM traffic_entries WHERE id = :id")
    fun observeById(id: Long): Flow<TrafficEntry?>

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

    @Query("DELETE FROM traffic_entries")
    suspend fun clearAll()
}
