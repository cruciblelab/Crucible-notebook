package com.cruciblelab.trafficlogger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface IpInfoDao {

    @Query("SELECT * FROM ip_info_cache WHERE ip = :ip")
    suspend fun get(ip: String): IpInfoCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: IpInfoCache)
}
