package com.cruciblelab.trafficlogger.data

class RuleRepository(private val dao: BlockRuleDao) {

    fun observeAll() = dao.observeAll()

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
