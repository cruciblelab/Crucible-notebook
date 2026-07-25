package com.cruciblelab.trafficlogger.data

import java.util.concurrent.TimeUnit

class TrafficRepository(
    private val dao: TrafficDao,
    /** Yeni bir hedef IP loglandığında çağrılır - [IpResolutionQueue.submit] buraya bağlanır. */
    private val onNewDestination: ((String) -> Unit)? = null
) {
    fun observeAll() = dao.observeAll()

    fun observeById(id: Long) = dao.observeById(id)

    suspend fun getByIdOnce(id: Long) = dao.getByIdOnce(id)

    fun observeConnectionHistory(packageName: String, domain: String?, destIp: String) =
        dao.observeConnectionHistory(packageName, domain, destIp)

    suspend fun insert(entry: TrafficEntry): Long {
        val id = dao.insert(entry)
        if (entry.destIp.isNotBlank()) onNewDestination?.invoke(entry.destIp)
        return id
    }

    suspend fun update(entry: TrafficEntry) = dao.update(entry)

    suspend fun purgeOlderThan(days: Int) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        dao.deleteOlderThan(cutoff)
    }

    suspend fun usageSince(sinceTimestamp: Long): List<AppUsage> = dao.usageSince(sinceTimestamp)

    suspend fun clearAll() = dao.clearAll()
}
