package com.cruciblelab.trafficlogger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cruciblelab.trafficlogger.data.BlockRule
import com.cruciblelab.trafficlogger.data.IpInfoCache
import com.cruciblelab.trafficlogger.data.PresetReputationCatalog
import com.cruciblelab.trafficlogger.data.ReputationSource
import com.cruciblelab.trafficlogger.data.RuleType
import com.cruciblelab.trafficlogger.data.TrafficEntry
import com.cruciblelab.trafficlogger.util.AppCategoryClassifier
import com.cruciblelab.trafficlogger.util.startOfDayMillis
import com.cruciblelab.trafficlogger.vpn.TrafficVpnService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ana Sayfa'da "en çok tüketen 3 uygulama" satırlarından biri. */
data class TopAppUsage(
    val packageName: String,
    val label: String,
    val bytes: Long,
    val category: AppCategoryClassifier.Category
)

/** Ana Sayfa'daki tek bakışlık özet kartının tüm verisi. */
data class HomeSummary(
    val totalBytesToday: Long,
    val topApps: List<TopAppUsage>,
    val unknownAppLabels: List<String>,
    val flaggedAppLabels: List<String>
) {
    val allKnown: Boolean get() = unknownAppLabels.isEmpty() && flaggedAppLabels.isEmpty()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TrafficLoggerApp

    val entries: StateFlow<List<TrafficEntry>> = app.trafficRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val retentionDays: StateFlow<Int> = app.settingsRepository.retentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 7)

    val dailyLimitMb: StateFlow<Int> = app.settingsRepository.dailyLimitMb
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val vpnRunning: StateFlow<Boolean> = TrafficVpnService.isRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** null = henüz DataStore'dan okunmadı (yükleniyor), UI bu sırada bekler. */
    val onboardingCompleted: StateFlow<Boolean?> = app.settingsRepository.onboardingCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch { app.settingsRepository.setOnboardingCompleted(completed) }
    }

    // Sadece manuel (kullanıcının kendi eklediği) kurallar - Kara/Beyaz Liste ekranı bunu kullanır.
    val rules: StateFlow<List<BlockRule>> = app.ruleRepository.observeManualOnly()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Blacklists just this one app + destination pair, not the whole domain everywhere. */
    fun blockEntry(entry: TrafficEntry) {
        viewModelScope.launch {
            app.ruleRepository.add(
                type = RuleType.BLACKLIST,
                appPackageName = entry.appPackageName,
                appLabel = entry.appLabel,
                matchValue = entry.domain ?: entry.destIp
            )
        }
    }

    fun whitelistEntry(entry: TrafficEntry) {
        viewModelScope.launch {
            app.ruleRepository.add(
                type = RuleType.WHITELIST,
                appPackageName = entry.appPackageName,
                appLabel = entry.appLabel,
                matchValue = entry.domain ?: entry.destIp
            )
        }
    }

    fun addRule(type: RuleType, appPackageName: String?, appLabel: String?, matchValue: String) {
        if (matchValue.isBlank()) return
        viewModelScope.launch {
            app.ruleRepository.add(type, appPackageName, appLabel, matchValue.trim())
        }
    }

    fun deleteRule(rule: BlockRule) {
        viewModelScope.launch { app.ruleRepository.delete(rule) }
    }

    // ip -> resolved ASN / organization / country info, filled in lazily as
    // rows become visible. See IpInfoRepository for caching behaviour.
    private val _ipInfoMap = MutableStateFlow<Map<String, IpInfoCache>>(emptyMap())
    val ipInfoMap: StateFlow<Map<String, IpInfoCache>> = _ipInfoMap.asStateFlow()
    private val pendingIpLookups = mutableSetOf<String>()

    fun requestIpInfo(ip: String) {
        if (ip.isBlank() || _ipInfoMap.value.containsKey(ip) || !pendingIpLookups.add(ip)) return
        viewModelScope.launch {
            val info = app.ipInfoRepository.getOrFetch(ip)
            _ipInfoMap.value = _ipInfoMap.value + (ip to info)
            pendingIpLookups.remove(ip)
        }
    }

    fun connectionHistory(entry: TrafficEntry): Flow<List<TrafficEntry>> =
        app.trafficRepository.observeConnectionHistory(entry.appPackageName, entry.domain, entry.destIp)

    fun entryById(id: Long): Flow<TrafficEntry?> = app.trafficRepository.observeById(id)

    fun setRetentionDays(days: Int) {
        viewModelScope.launch { app.settingsRepository.setRetentionDays(days) }
    }

    fun setDailyLimitMb(mb: Int) {
        viewModelScope.launch { app.settingsRepository.setDailyLimitMb(mb) }
    }

    fun clearHistory() {
        viewModelScope.launch { app.trafficRepository.clearAll() }
    }

    // ---- Uygulama kategorizasyonu / Ana Sayfa özeti ----

    private val activeReputationVerdicts = app.reputationRepository.observeActiveVerdicts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun categoryFor(packageName: String): AppCategoryClassifier.Category = AppCategoryClassifier.classify(
        packageName = packageName,
        isSystemApp = app.packageClassifier.isSystemApp(packageName),
        activeVerdicts = activeReputationVerdicts.value
    )

    val homeSummary: StateFlow<HomeSummary> = combine(entries, activeReputationVerdicts) { list, verdicts ->
        val todayStart = startOfDayMillis()
        val todayEntries = list.filter { it.timestamp >= todayStart }
        val totalToday = todayEntries.sumOf { it.bytesUp + it.bytesDown }

        val byApp = todayEntries.groupBy { it.appPackageName to it.appLabel }
            .map { (key, rows) -> Triple(key.first, key.second, rows.sumOf { it.bytesUp + it.bytesDown }) }
            .sortedByDescending { it.third }

        val classified = byApp.map { (pkg, label, bytes) ->
            val category = AppCategoryClassifier.classify(pkg, app.packageClassifier.isSystemApp(pkg), verdicts)
            TopAppUsage(pkg, label, bytes, category)
        }

        HomeSummary(
            totalBytesToday = totalToday,
            topApps = classified.take(3),
            unknownAppLabels = classified.filter { it.category == AppCategoryClassifier.Category.UNKNOWN }.map { it.label },
            flaggedAppLabels = classified.filter { it.category == AppCategoryClassifier.Category.FLAGGED }.map { it.label }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeSummary(0L, emptyList(), emptyList(), emptyList())
    )

    // ---- İtibar veritabanları ----

    val reputationSources: StateFlow<List<ReputationSource>> = app.reputationRepository.observeSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availablePresets: List<PresetReputationCatalog.Preset> = PresetReputationCatalog.ALL

    fun loadPreset(preset: PresetReputationCatalog.Preset, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                app.reputationRepository.loadPreset(preset)
            } catch (e: Exception) {
                onError(e.message ?: "Veritabanı yüklenemedi.")
            }
        }
    }

    fun importCustomReputation(name: String, json: String, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { app.reputationRepository.importCustom(name, "Özel içe aktarılan veritabanı", json) }
            onResult(result)
        }
    }

    fun setSourceEnabled(source: ReputationSource, enabled: Boolean) {
        viewModelScope.launch { app.reputationRepository.setEnabled(source, enabled) }
    }

    fun setSourceAutoBlock(source: ReputationSource, autoBlock: Boolean) {
        viewModelScope.launch { app.reputationRepository.setAutoBlock(source, autoBlock) }
    }

    fun deleteReputationSource(source: ReputationSource) {
        viewModelScope.launch { app.reputationRepository.delete(source) }
    }
}
