package com.cruciblelab.trafficlogger

import android.app.Application
import com.cruciblelab.trafficlogger.data.AppDatabase
import com.cruciblelab.trafficlogger.data.IpInfoRepository
import com.cruciblelab.trafficlogger.data.IpResolutionQueue
import com.cruciblelab.trafficlogger.data.PackageIntegrityRepository
import com.cruciblelab.trafficlogger.data.ProfileRepository
import com.cruciblelab.trafficlogger.data.ReputationRepository
import com.cruciblelab.trafficlogger.data.RuleRepository
import com.cruciblelab.trafficlogger.data.SettingsRepository
import com.cruciblelab.trafficlogger.data.TrackerBlockRepository
import com.cruciblelab.trafficlogger.data.TrafficRepository
import com.cruciblelab.trafficlogger.util.PackageClassifier
import com.cruciblelab.trafficlogger.util.SigningCertResolver

class TrafficLoggerApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val ipInfoRepository by lazy { IpInfoRepository(database.ipInfoDao()) }
    val ipResolutionQueue by lazy { IpResolutionQueue(ipInfoRepository) }
    val trafficRepository by lazy {
        TrafficRepository(database.trafficDao(), onNewDestination = { ip -> ipResolutionQueue.submit(ip) })
    }
    val settingsRepository by lazy { SettingsRepository(this) }
    val ruleRepository by lazy { RuleRepository(database.blockRuleDao(), database.reputationDao()) }
    val trackerBlockRepository by lazy { TrackerBlockRepository(database.blockRuleDao()) }
    val reputationRepository by lazy { ReputationRepository(this, database.reputationDao()) }
    val packageClassifier by lazy { PackageClassifier(this) }
    val packageIntegrityRepository by lazy {
        PackageIntegrityRepository(database.packageSignatureDao(), SigningCertResolver(this))
    }
    val profileRepository by lazy { ProfileRepository(database.profileDao(), settingsRepository) }

    override fun onCreate() {
        super.onCreate()
        // Sadece ön plandayken çalışan, ekonomik IP çözümleme kuyruğunu başlat - bkz.
        // IpResolutionQueue dokümantasyonu. Burada başlatmak sadece lifecycle observer'ı
        // kaydeder, hiçbir ağ isteği atmaz (onStart/onStop tetiklenene kadar hareketsiz).
        ipResolutionQueue.start()
    }
}
