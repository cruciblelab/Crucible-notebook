package com.cruciblelab.trafficlogger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PackageSignatureDao {

    @Query("SELECT * FROM package_signatures WHERE packageName = :packageName LIMIT 1")
    suspend fun find(packageName: String): PackageSignatureRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: PackageSignatureRecord)

    @Query("UPDATE package_signatures SET lastConfirmedAt = :now WHERE packageName = :packageName")
    suspend fun touch(packageName: String, now: Long)

    @Query("SELECT * FROM package_signatures WHERE packageName = :packageName LIMIT 1")
    fun observe(packageName: String): Flow<PackageSignatureRecord?>
}
