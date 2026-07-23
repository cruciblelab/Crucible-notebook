package com.cruciblelab.trafficlogger.data

import java.util.concurrent.TimeUnit

class TrafficRepository(private val dao: TrafficDao) {
    fun observeAll() = dao.observeAll()

    fun observeById(id: Long) = dao.observeById(id)

    fun observeConnectionHistory(packageName: String, domain: String?, destIp: String) =
        dao.observeConnectionHistory(packageName, domain, destIp)

    suspend fun insert(entry: TrafficEntry): Long = dao.insert(entry)

    suspend fun update(entry: TrafficEntry) = dao.update(entry)

    suspend fun purgeOlderThan(days: Int) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        dao.deleteOlderThan(cutoff)
    }

    suspend fun clearAll() = dao.clearAll()
}
