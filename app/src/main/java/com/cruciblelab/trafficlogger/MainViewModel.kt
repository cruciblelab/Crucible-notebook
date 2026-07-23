package com.cruciblelab.trafficlogger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cruciblelab.trafficlogger.data.BlockRule
import com.cruciblelab.trafficlogger.data.IpInfoCache
import com.cruciblelab.trafficlogger.data.RuleType
import com.cruciblelab.trafficlogger.data.TrafficEntry
import com.cruciblelab.trafficlogger.vpn.TrafficVpnService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TrafficLoggerApp

    val entries: StateFlow<List<TrafficEntry>> = app.trafficRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val retentionDays: StateFlow<Int> = app.settingsRepository.retentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 7)

    val vpnRunning: StateFlow<Boolean> = TrafficVpnService.isRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val rules: StateFlow<List<BlockRule>> = app.ruleRepository.observeAll()
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

    fun clearHistory() {
        viewModelScope.launch { app.trafficRepository.clearAll() }
    }
}
