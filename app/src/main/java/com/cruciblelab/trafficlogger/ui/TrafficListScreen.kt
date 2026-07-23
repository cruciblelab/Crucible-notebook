package com.cruciblelab.trafficlogger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cruciblelab.trafficlogger.data.TrafficEntry
import com.cruciblelab.trafficlogger.util.formatBytes
import com.cruciblelab.trafficlogger.util.formatTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficListScreen(
    entries: List<TrafficEntry>,
    vpnRunning: Boolean,
    onToggleVpn: () -> Unit,
    onEntryClick: (TrafficEntry) -> Unit,
    onSettingsClick: () -> Unit
) {
    var selectedApp by remember { mutableStateOf<String?>(null) }
    val appNames = remember(entries) { entries.map { it.appLabel }.distinct().sorted() }
    val filteredEntries = remember(entries, selectedApp) {
        selectedApp?.let { app -> entries.filter { it.appLabel == app } } ?: entries
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ağ Trafiği Defteri") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ayarlar")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(if (vpnRunning) "İzleniyor" else "Durduruldu")
                Button(onClick = onToggleVpn) {
                    Text(if (vpnRunning) "Durdur" else "Başlat")
                }
            }

            if (appNames.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedApp == null,
                        onClick = { selectedApp = null },
                        label = { Text("Tümü") }
                    )
                    appNames.take(6).forEach { name ->
                        FilterChip(
                            selected = selectedApp == name,
                            onClick = { selectedApp = if (selectedApp == name) null else name },
                            label = { Text(name) }
                        )
                    }
                }
            }

            if (filteredEntries.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                ) {
                    Text("Henüz kayıt yok. İzlemeyi başlatınca DNS sorguları burada listelenecek.")
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        TrafficRow(entry = entry, onClick = { onEntryClick(entry) })
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun TrafficRow(entry: TrafficEntry, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(entry.appLabel) },
        supportingContent = { Text(entry.domain ?: entry.destIp) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(formatBytes(entry.bytesUp + entry.bytesDown))
                Text(formatTimestamp(entry.timestamp), style = MaterialTheme.typography.bodySmall)
            }
        }
    )
}
