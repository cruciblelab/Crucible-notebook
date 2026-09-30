package com.cruciblelab.trafficlogger.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RuleRepository(
    private val dao: BlockRuleDao,
    private val reputationDao: ReputationDao
) {

    /** Manuel kullanıcı kuralları - Kara/Beyaz Liste ekranında gösterilir ve silinebilir. */
    fun observeManualOnly(): Flow<List<BlockRule>> = dao.observeAll()

    /**
     * VPN servisinin kullandığı tam kural seti: manuel kurallar + itibar veritabanından
     * türeyen otomatik engelleme kuralları (matchValue = "*", tüm uygulamayı engeller).
     * Bu sentetik kurallar DB'ye yazılmaz, yalnızca bu akışta anlık olarak birleştirilir.
     */
    fun observeAll(): Flow<List<BlockRule>> =
        combine(dao.observeAll(), reputationDao.observeAutoBlockPackages()) { manual, autoBlockPackages ->
            val synthetic = autoBlockPackages.map { packageName ->
                BlockRule(
                    id = -1,
                    type = RuleType.BLACKLIST,
                    appPackageName = packageName,
                    appLabel = null,
                    matchValue = "*",
                    createdAt = 0
                )
            }
            manual + synthetic
        }

    suspend fun add(
        type: RuleType,
        appPackageName: String?,
        appLabel: String?,
        matchValue: String
    ): Long = dao.insert(
        BlockRule(
            type = type,
            appPackageName = appPackageName,
            appLabel = appLabel,
            matchValue = matchValue
        )
    )

    suspend fun delete(rule: BlockRule) = dao.delete(rule)
}
