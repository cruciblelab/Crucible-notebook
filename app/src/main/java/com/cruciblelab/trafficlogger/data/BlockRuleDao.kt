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

    /** Uygulama-bağımsız (appPackageName IS NULL), belirli bir domain'e ait kural var mı? */
    @Query("SELECT * FROM block_rules WHERE appPackageName IS NULL AND matchValue = :domain LIMIT 1")
    suspend fun findAppAgnostic(domain: String): BlockRule?

    /** Bir tracker kategorisini kapatırken (izin ver) o domain'e ait kuralı kaldırır. */
    @Query("DELETE FROM block_rules WHERE appPackageName IS NULL AND matchValue = :domain")
    suspend fun deleteAppAgnostic(domain: String)
}
