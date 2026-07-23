package com.cruciblelab.trafficlogger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cruciblelab.trafficlogger.data.TrafficEntry
import com.cruciblelab.trafficlogger.vpn.TrafficVpnService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
