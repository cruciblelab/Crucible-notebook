package com.cruciblelab.trafficlogger

import android.app.Application
import com.cruciblelab.trafficlogger.data.AppDatabase
import com.cruciblelab.trafficlogger.data.IpInfoRepository
import com.cruciblelab.trafficlogger.data.ReputationRepository
import com.cruciblelab.trafficlogger.data.RuleRepository
import com.cruciblelab.trafficlogger.data.SettingsRepository
import com.cruciblelab.trafficlogger.data.TrafficRepository
import com.cruciblelab.trafficlogger.util.PackageClassifier

class TrafficLoggerApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val trafficRepository by lazy { TrafficRepository(database.trafficDao()) }
    val ipInfoRepository by lazy { IpInfoRepository(database.ipInfoDao()) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val ruleRepository by lazy { RuleRepository(database.blockRuleDao(), database.reputationDao()) }
    val reputationRepository by lazy { ReputationRepository(this, database.reputationDao()) }
    val packageClassifier by lazy { PackageClassifier(this) }
}
