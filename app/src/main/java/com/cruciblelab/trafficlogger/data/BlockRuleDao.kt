package com.cruciblelab.trafficlogger.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockRuleDao {

    @Query("SELECT * FROM block_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BlockRule>>

    @Insert
    suspend fun insert(rule: BlockRule): Long

    @Delete
    suspend fun delete(rule: BlockRule)

    @Query("DELETE FROM block_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}
