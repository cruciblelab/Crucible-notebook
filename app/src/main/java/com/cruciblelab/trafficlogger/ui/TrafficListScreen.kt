package com.cruciblelab.trafficlogger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertChartOutlined
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.cruciblelab.trafficlogger.data.Direction
import com.cruciblelab.trafficlogger.data.IpInfoCache
import com.cruciblelab.trafficlogger.data.Protocol
import com.cruciblelab.trafficlogger.data.TrafficEntry
import com.cruciblelab.trafficlogger.ui.theme.AccentCoral
import com.cruciblelab.trafficlogger.ui.theme.AvatarPalette
import com.cruciblelab.trafficlogger.ui.theme.TextSecondary
import com.cruciblelab.trafficlogger.ui.theme.TextTertiary
import com.cruciblelab.trafficlogger.util.countryFlagEmoji
import com.cruciblelab.trafficlogger.util.formatBytes
import com.cruciblelab.trafficlogger.util.formatTimestamp
import com.cruciblelab.trafficlogger.util.startOfDayMillis
import java.util.concurrent.TimeUnit

/** Local (non-persisted) date-range presets for the traffic list filter sheet. */
enum class DateRangePreset(val label: String) {
    ALL("Tümü"),
    TODAY("Bugün"),
    LAST_7_DAYS("Son 7 gün"),
    LAST_30_DAYS("Son 30 gün")
}

/** Local (non-persisted) sort options for the traffic list. */
enum class TrafficSortOption(val label: String) {
    DATE_DESC("Tarih: yeni → eski"),
    DATE_ASC("Tarih: eski → yeni"),
    SIZE_DESC("Veri boyutu: büyük → küçük"),
    SIZE_ASC("Veri boyutu: küçük → büyük")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficListScreen(
    entries: List<TrafficEntry>,
    vpnRunning: Boolean,
    ipInfoMap: Map<String, IpInfoCache>,
    onRequestIpInfo: (String) -> Unit,
    onToggleVpn: () -> Unit,
    onEntryClick: (TrafficEntry) -> Unit,
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onRulesClick: () -> Unit,
    onBlockEntry: (TrafficEntry) -> Unit,
    onWhitelistEntry: (TrafficEntry) -> Unit,
    initialOnlyBlocked: Boolean = false
) {
    var selectedApp by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var protocolFilter by remember { mutableStateOf<Protocol?>(null) }
    var directionFilter by remember { mutableStateOf<Direction?>(null) }
    var dateRangeFilter by remember { mutableStateOf(DateRangePreset.ALL) }
    var sortOption by remember { mutableStateOf(TrafficSortOption.DATE_DESC) }
    var onlyBlockedFilter by remember { mutableStateOf(initialOnlyBlocked) }
    var showFilterSheet by remember { mutableStateOf(false) }

    val appNames = remember(entries) { entries.map { it.appLabel }.distinct().sorted() }
    val filtersActive = protocolFilter != null || directionFilter != null || dateRangeFilter != DateRangePreset.ALL ||
        sortOption != TrafficSortOption.DATE_DESC || onlyBlockedFilter

    val filteredEntries = remember(entries, selectedApp, query, protocolFilter, directionFilter, dateRangeFilter, sortOption, onlyBlockedFilter) {
        entries
            .let { list -> selectedApp?.let { app -> list.filter { it.appLabel == app } } ?: list }
            .let { list ->
                if (query.isBlank()) list
                else list.filter {
                    it.appLabel.contains(query, ignoreCase = true) ||
                        (it.domain?.contains(query, ignoreCase = true) ?: false) ||
                        it.destIp.contains(query, ignoreCase = true)
                }
            }
            .let { list -> if (onlyBlockedFilter) list.filter { it.blocked } else list }
            .let { list -> protocolFilter?.let { p -> list.filter { it.protocol == p } } ?: list }
            .let { list -> directionFilter?.let { d -> list.filter { it.direction == d } } ?: list }
            .let { list ->
                val cutoff = when (dateRangeFilter) {
                    DateRangePreset.ALL -> null
                    DateRangePreset.TODAY -> startOfDayMillis()
                    DateRangePreset.LAST_7_DAYS -> System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
                    DateRangePreset.LAST_30_DAYS -> System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
                }
                cutoff?.let { since -> list.filter { it.timestamp >= since } } ?: list
            }
            .let { list ->
                when (sortOption) {
                    TrafficSortOption.DATE_DESC -> list.sortedByDescending { it.timestamp }
                    TrafficSortOption.DATE_ASC -> list.sortedBy { it.timestamp }
                    TrafficSortOption.SIZE_DESC -> list.sortedByDescending { it.bytesUp + it.bytesDown }
                    TrafficSortOption.SIZE_ASC -> list.sortedBy { it.bytesUp + it.bytesDown }
                }
            }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Canlı Ağ Trafiği", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onHomeClick) {
                        Icon(Icons.Filled.Home, contentDescription = "Ana Sayfa", tint = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = "Filtrele ve sırala",
                            tint = if (filtersActive) MaterialTheme.colorScheme.primary else TextSecondary
                        )
                    }
                    IconButton(onClick = onStatsClick) {
                        Icon(Icons.Filled.InsertChartOutlined, contentDescription = "İstatistikler", tint = TextSecondary)
                    }
                    IconButton(onClick = onRulesClick) {
                        Icon(Icons.Filled.Shield, contentDescription = "Kara/Beyaz liste", tint = TextSecondary)
                    }
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

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Uygulama, alan adı veya IP ara") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextTertiary) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Temizle", tint = TextTertiary)
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp)
            )

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
                            onClick = { onEntryClick(entry) },
                            onBlock = { onBlockEntry(entry) },
                            onWhitelist = { onWhitelistEntry(entry) }
                        )
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterSortSheet(
            onlyBlockedFilter = onlyBlockedFilter,
            onOnlyBlockedChange = { onlyBlockedFilter = it },
            protocolFilter = protocolFilter,
            onProtocolChange = { protocolFilter = it },
            directionFilter = directionFilter,
            onDirectionChange = { directionFilter = it },
            dateRangeFilter = dateRangeFilter,
            onDateRangeChange = { dateRangeFilter = it },
            sortOption = sortOption,
            onSortChange = { sortOption = it },
            onReset = {
                onlyBlockedFilter = false
                protocolFilter = null
                directionFilter = null
                dateRangeFilter = DateRangePreset.ALL
                sortOption = TrafficSortOption.DATE_DESC
            },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSortSheet(
    onlyBlockedFilter: Boolean,
    onOnlyBlockedChange: (Boolean) -> Unit,
    protocolFilter: Protocol?,
    onProtocolChange: (Protocol?) -> Unit,
    directionFilter: Direction?,
    onDirectionChange: (Direction?) -> Unit,
    dateRangeFilter: DateRangePreset,
    onDateRangeChange: (DateRangePreset) -> Unit,
    sortOption: TrafficSortOption,
    onSortChange: (TrafficSortOption) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filtrele ve sırala", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Sıfırla",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onReset)
                )
            }

            SheetSectionTitle("Durum")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetChip("Tümü", !onlyBlockedFilter) { onOnlyBlockedChange(false) }
                SheetChip("Sadece engellenenler", onlyBlockedFilter) { onOnlyBlockedChange(true) }
            }

            SheetSectionTitle("Protokol")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetChip("Tümü", protocolFilter == null) { onProtocolChange(null) }
                Protocol.entries.forEach { protocol ->
                    SheetChip(protocol.name, protocolFilter == protocol) { onProtocolChange(protocol) }
                }
            }

            SheetSectionTitle("Yön")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetChip("Tümü", directionFilter == null) { onDirectionChange(null) }
                SheetChip("Giden", directionFilter == Direction.OUT) { onDirectionChange(Direction.OUT) }
                SheetChip("Gelen", directionFilter == Direction.IN) { onDirectionChange(Direction.IN) }
            }

            SheetSectionTitle("Tarih aralığı")
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DateRangePreset.entries.toList()) { preset ->
                    SheetChip(preset.label, dateRangeFilter == preset) { onDateRangeChange(preset) }
                }
            }

            SheetSectionTitle("Sırala")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TrafficSortOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSortChange(option) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = sortOption == option,
                            onClick = { onSortChange(option) }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(option.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp)
    )
}

@Composable
private fun SheetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = Color.White
        )
    )
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TrafficRow(
    entry: TrafficEntry,
    ipInfo: IpInfoCache?,
    onRequestIpInfo: (String) -> Unit,
    onClick: () -> Unit,
    onBlock: () -> Unit,
    onWhitelist: () -> Unit
) {
    LaunchedEffect(entry.destIp) { onRequestIpInfo(entry.destIp) }

    val avatarColor = remember(entry.appLabel) {
        AvatarPalette[Math.floorMod(entry.appLabel.hashCode(), AvatarPalette.size)]
    }
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.blocked) AccentCoral.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box {
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
                    if (entry.blocked) {
                        Icon(Icons.Filled.Block, contentDescription = "Engellendi", tint = AccentCoral, modifier = Modifier.size(20.dp))
                    } else {
                        Text(
                            entry.appLabel.take(1).uppercase(),
                            color = avatarColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            entry.appLabel,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (entry.blocked) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "engellendi",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentCoral
                            )
                        }
                    }
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

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("${entry.appLabel} için kara listeye ekle") },
                    leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null, tint = AccentCoral) },
                    onClick = { menuOpen = false; onBlock() }
                )
                DropdownMenuItem(
                    text = { Text("${entry.appLabel} için beyaz listeye ekle") },
                    leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                    onClick = { menuOpen = false; onWhitelist() }
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
