package com.cruciblelab.trafficlogger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cruciblelab.trafficlogger.data.IpInfoCache
import com.cruciblelab.trafficlogger.data.TrafficEntry
import com.cruciblelab.trafficlogger.ui.theme.AvatarPalette
import com.cruciblelab.trafficlogger.ui.theme.TextSecondary
import com.cruciblelab.trafficlogger.ui.theme.TextTertiary
import com.cruciblelab.trafficlogger.util.countryFlagEmoji
import com.cruciblelab.trafficlogger.util.formatBytes
import com.cruciblelab.trafficlogger.util.formatTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficListScreen(
    entries: List<TrafficEntry>,
    vpnRunning: Boolean,
    ipInfoMap: Map<String, IpInfoCache>,
    onRequestIpInfo: (String) -> Unit,
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Ağ Trafiği Defteri", fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ayarlar", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StatusCard(vpnRunning = vpnRunning, onToggleVpn = onToggleVpn)

            if (appNames.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedApp == null,
                            onClick = { selectedApp = null },
                            label = { Text("Tümü") },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    items(appNames) { name ->
                        FilterChip(
                            selected = selectedApp == name,
                            onClick = { selectedApp = if (selectedApp == name) null else name },
                            label = { Text(name) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            if (filteredEntries.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        TrafficRow(
                            entry = entry,
                            ipInfo = ipInfoMap[entry.destIp],
                            onRequestIpInfo = onRequestIpInfo,
                            onClick = { onEntryClick(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(vpnRunning: Boolean, onToggleVpn: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (vpnRunning) MaterialTheme.colorScheme.secondary else TextTertiary)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        if (vpnRunning) "İzleniyor" else "Durduruldu",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (vpnRunning) "Trafik gerçek zamanlı kaydediliyor" else "Başlatmak için dokun",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (vpnRunning) Color(0xFFFFECEC) else MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onToggleVpn)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (vpnRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = if (vpnRunning) Color(0xFFE05555) else Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (vpnRunning) "Durdur" else "Başlat",
                    color = if (vpnRunning) Color(0xFFE05555) else Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.WifiTethering, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Henüz kayıt yok",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "İzlemeyi başlatınca tüm bağlantılar burada listelenecek.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun TrafficRow(
    entry: TrafficEntry,
    ipInfo: IpInfoCache?,
    onRequestIpInfo: (String) -> Unit,
    onClick: () -> Unit
) {
    LaunchedEffect(entry.destIp) { onRequestIpInfo(entry.destIp) }

    val avatarColor = remember(entry.appLabel) {
        AvatarPalette[Math.floorMod(entry.appLabel.hashCode(), AvatarPalette.size)]
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    entry.appLabel.take(1).uppercase(),
                    color = avatarColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.appLabel,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    entry.domain ?: entry.destIp,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                IpInfoLine(ipInfo)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatBytes(entry.bytesUp + entry.bytesDown),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    formatTimestamp(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }
}

@Composable
private fun IpInfoLine(ipInfo: IpInfoCache?) {
    AnimatedVisibility(visible = ipInfo != null) {
        if (ipInfo == null) return@AnimatedVisibility
        val flag = countryFlagEmoji(ipInfo.countryCode)
        val label = listOfNotNull(
            flag?.let { "$it " } ?: "",
            ipInfo.org ?: ipInfo.isp,
            ipInfo.countryName?.takeIf { ipInfo.org == null && ipInfo.isp == null }
        ).joinToString(separator = "").ifBlank { null }

        if (label != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Public, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
