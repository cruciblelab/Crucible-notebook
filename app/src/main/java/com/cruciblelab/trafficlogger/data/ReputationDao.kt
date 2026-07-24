package com.cruciblelab.trafficlogger.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReputationDao {

    @Query("SELECT * FROM reputation_sources ORDER BY importedAt DESC")
    fun observeSources(): Flow<List<ReputationSource>>

    @Query("SELECT key FROM reputation_sources")
    suspend fun allSourceKeys(): List<String>

    @Insert
    suspend fun insertSource(source: ReputationSource): Long

    @Update
    suspend fun updateSource(source: ReputationSource)

    @Delete
    suspend fun deleteSource(source: ReputationSource)

    @Insert
    suspend fun insertEntries(entries: List<AppReputationEntry>)

    @Query("DELETE FROM app_reputation_entries WHERE sourceId = :sourceId")
    suspend fun deleteEntriesForSource(sourceId: Long)

    /**
     * Yalnızca TAM (EXACT) eşleşmeli, "engellemesi açık" bir kaynağa ait FLAGGED paket
     * adlarını döner. Otomatik engelleme kasıtlı olarak PREFIX eşleşmelerini kapsamaz -
     * geniş bir önek yanlışlıkla ilgisiz uygulamaları engellemesin diye.
     */
    @Query(
        """
        SELECT DISTINCT e.packageName FROM app_reputation_entries e
        INNER JOIN reputation_sources s ON s.id = e.sourceId
        WHERE e.verdict = 'FLAGGED' AND e.matchType = 'EXACT' AND s.enabled = 1 AND s.autoBlock = 1
        """
    )
    fun observeAutoBlockPackages(): Flow<List<String>>

    /** Aktif (enabled) tüm kaynaklardan derlenmiş, kategorizasyon için kullanılan girişler. */
    @Query(
        """
        SELECT e.packageName as packageName, e.verdict as verdict, e.matchType as matchType
        FROM app_reputation_entries e
        INNER JOIN reputation_sources s ON s.id = e.sourceId
        WHERE s.enabled = 1
        """
    )
    fun observeActiveVerdicts(): Flow<List<PackageVerdict>>
}
