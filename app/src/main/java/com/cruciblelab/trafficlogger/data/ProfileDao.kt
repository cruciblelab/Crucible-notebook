package com.cruciblelab.trafficlogger.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM network_profiles ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM network_profiles WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity): Long

    @Delete
    suspend fun delete(profile: ProfileEntity)

    // --- Yedekleme (bkz. BackupRepository) ---
    @Query("SELECT * FROM network_profiles")
    suspend fun getAll(): List<ProfileEntity>

    @Query("DELETE FROM network_profiles")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(profiles: List<ProfileEntity>)
}
