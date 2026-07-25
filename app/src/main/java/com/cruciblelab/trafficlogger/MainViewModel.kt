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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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

/** Trafikte görülen ama TrackerCatalog'daki kürasyonlu listede olmayan bir domain. */
data class ObservedOtherDomain(val domain: String, val destIp: String, val bytes: Long)

/** Ana Sayfa'daki tek bakışlık özet kartının tüm verisi. */
data class HomeSummary(
    val totalBytesToday: Long,
    val topApps: List<TopAppUsage>,
    val unknownAppLabels: List<String>,
    val flaggedAppLabels: List<String>,
    val otherObservedDomains: List<ObservedOtherDomain> = emptyList()
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
    //
    // ÖNEMLİ: bu SADECE uygulama açıkken (bir satır ekranda göründüğünde requestIpInfo
    // çağrılır) çalışır - VPN servisi (arka planda, trafik akarken) bu repository'ye hiç
    // dokunmaz, bu yüzden arka planda ekstra ağ isteği/güç tüketimi olmaz.
    //
    // Tek tek gelen istekler ESKİDEN her biri kendi coroutine'ini anında ateşliyordu -
    // hızlı scroll'da 15-20 istek birden gidebiliyordu. Şimdi TEK sıralı kuyruk + aralarda
    // küçük bir bekleme (bkz. IP_LOOKUP_PACING_MS): istekler art arda, yavaş yavaş, "ekonomik"
    // şekilde işleniyor. Kuyruk viewModelScope'a bağlı - uygulama tamamen kapanınca
    // (ViewModel temizlenince) otomatik durur, arkada asılı kalmaz.
    private val _ipInfoMap = MutableStateFlow<Map<String, IpInfoCache>>(emptyMap())
    val ipInfoMap: StateFlow<Map<String, IpInfoCache>> = _ipInfoMap.asStateFlow()
    private val queuedIps = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val ipLookupQueue = Channel<String>(capacity = Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (ip in ipLookupQueue) {
                if (!_ipInfoMap.value.containsKey(ip)) {
                    val info = app.ipInfoRepository.getOrFetch(ip)
                    _ipInfoMap.value = _ipInfoMap.value + (ip to info)
                }
                queuedIps.remove(ip)
                delay(IP_LOOKUP_PACING_MS)
            }
        }
    }

    fun requestIpInfo(ip: String) {
        if (ip.isBlank() || _ipInfoMap.value.containsKey(ip) || !queuedIps.add(ip)) return
        ipLookupQueue.trySend(ip)
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

    // ---- Ana Sayfa "Veri Toplama Kontrolleri" (basit switch + ileri düzey tam engel) ----

    val companyProtectionStates: StateFlow<Map<String, com.cruciblelab.trafficlogger.data.CompanyProtectionState>> =
        app.trackerBlockRepository.observeProtectionStates()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setTrackingBlocked(company: com.cruciblelab.trafficlogger.data.TrackerCatalog.Company, blocked: Boolean) {
        viewModelScope.launch { app.trackerBlockRepository.setTrackingBlocked(company, blocked) }
    }

    fun setFullyBlocked(company: com.cruciblelab.trafficlogger.data.TrackerCatalog.Company, blocked: Boolean) {
        viewModelScope.launch { app.trackerBlockRepository.setFullyBlocked(company, blocked) }
    }

    fun clearHistory() {
        viewModelScope.launch { app.trafficRepository.clearAll() }
    }

    /** Ana Sayfa'daki "diğer görülen kaynaklar" listesinden tek dokunuşla, uygulama-bağımsız engelleme. */
    fun quickBlockDomain(domain: String) {
        viewModelScope.launch { app.ruleRepository.add(RuleType.BLACKLIST, null, null, domain) }
    }

    // ---- Uygulama kategorizasyonu / Ana Sayfa özeti ----

    private val activeReputationVerdicts = app.reputationRepository.observeActiveVerdicts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val mismatchedPackages = app.packageIntegrityRepository.mismatchedPackages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Bir paketi arka planda imza bütünlüğü açısından kontrol etmeyi tetikler (fire-and-forget). */
    private fun checkIntegrity(packageName: String) {
        viewModelScope.launch { app.packageIntegrityRepository.ensureChecked(packageName) }
    }

    fun categoryFor(packageName: String): AppCategoryClassifier.Category {
        checkIntegrity(packageName)
        return AppCategoryClassifier.classify(
            packageName = packageName,
            isSystemApp = app.packageClassifier.isSystemApp(packageName),
            activeVerdicts = activeReputationVerdicts.value,
            signatureMismatch = mismatchedPackages.value.contains(packageName)
        )
    }

    val homeSummary: StateFlow<HomeSummary> = combine(entries, activeReputationVerdicts, mismatchedPackages) { list, verdicts, mismatches ->
        val todayStart = startOfDayMillis()
        val todayEntries = list.filter { it.timestamp >= todayStart }
        val totalToday = todayEntries.sumOf { it.bytesUp + it.bytesDown }

        val byApp = todayEntries.groupBy { it.appPackageName to it.appLabel }
            .map { (key, rows) -> Triple(key.first, key.second, rows.sumOf { it.bytesUp + it.bytesDown }) }
            .sortedByDescending { it.third }

        val classified = byApp.map { (pkg, label, bytes) ->
            checkIntegrity(pkg)
            val category = AppCategoryClassifier.classify(
                pkg,
                app.packageClassifier.isSystemApp(pkg),
                verdicts,
                signatureMismatch = mismatches.contains(pkg)
            )
            TopAppUsage(pkg, label, bytes, category)
        }

        val curatedDomains = com.cruciblelab.trafficlogger.data.TrackerCatalog.ALL
            .flatMap { it.trackingDomains + it.fullBlockDomains }
            .toSet()

        fun isCurated(domain: String): Boolean =
            curatedDomains.any { domain.equals(it, ignoreCase = true) || domain.endsWith(".$it", ignoreCase = true) }

        // Listede olmayan bir domain'i sessizce ATLAMIYORUZ: en çok veri çeken, kürasyonlu
        // listede olmayan domain'leri ayrıca çıkarıyoruz - Ana Sayfa bunları ASN/organizasyon
        // bilgisiyle (bilinirse) gösterir, tamamen görünmez kalmazlar.
        val otherDomains = todayEntries
            .filter { !it.domain.isNullOrBlank() && !isCurated(it.domain) }
            .groupBy { it.domain!! }
            .map { (domain, rows) -> ObservedOtherDomain(domain, rows.first().destIp, rows.sumOf { it.bytesUp + it.bytesDown }) }
            .sortedByDescending { it.bytes }
            .take(5)

        HomeSummary(
            totalBytesToday = totalToday,
            topApps = classified.take(3),
            unknownAppLabels = classified.filter { it.category == AppCategoryClassifier.Category.UNKNOWN }.map { it.label },
            flaggedAppLabels = (classified.filter { it.category == AppCategoryClassifier.Category.FLAGGED } +
                classified.filter { it.category == AppCategoryClassifier.Category.SIGNATURE_MISMATCH }).map { it.label },
            otherObservedDomains = otherDomains
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

    companion object {
        /** IP bilgisi istekleri arasında bilerek bırakılan boşluk - "ekonomik", art arda değil. */
        private const val IP_LOOKUP_PACING_MS = 400L
    }
}
