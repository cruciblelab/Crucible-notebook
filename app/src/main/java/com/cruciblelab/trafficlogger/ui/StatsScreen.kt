package com.cruciblelab.trafficlogger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
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
import com.cruciblelab.trafficlogger.data.TrafficEntry
import com.cruciblelab.trafficlogger.ui.theme.AccentCoral
import com.cruciblelab.trafficlogger.ui.theme.AccentViolet
import com.cruciblelab.trafficlogger.ui.theme.AvatarPalette
import com.cruciblelab.trafficlogger.ui.theme.TextSecondary
import com.cruciblelab.trafficlogger.ui.theme.TextTertiary
import com.cruciblelab.trafficlogger.util.formatBytes
import com.cruciblelab.trafficlogger.util.startOfDayMillis
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private data class Stat(val label: String, val bytes: Long, val bytesUp: Long, val bytesDown: Long, val count: Int)

private enum class TimelineGranularity(val label: String) {
    HOURLY("Saatlik"),
    DAILY("Günlük")
}

private data class TimeBucket(val label: String, val bytes: Long)

/**
 * Buckets entries into either the last 24 hours (one bar per hour) or the last 14 days
 * (one bar per day), for the traffic-volume timeline chart.
 */
private fun buildTimelineBuckets(entries: List<TrafficEntry>, granularity: TimelineGranularity): List<TimeBucket> {
    val now = System.currentTimeMillis()
    return when (granularity) {
        TimelineGranularity.HOURLY -> {
            val hourMs = TimeUnit.HOURS.toMillis(1)
            val currentHourStart = (now / hourMs) * hourMs
            val hourFormat = SimpleDateFormat("HH", Locale.getDefault())
            (23 downTo 0).map { offset ->
                val bucketStart = currentHourStart - offset * hourMs
                val bucketEnd = bucketStart + hourMs
                val bytes = entries
                    .asSequence()
                    .filter { it.timestamp >= bucketStart && it.timestamp < bucketEnd }
                    .sumOf { it.bytesUp + it.bytesDown }
                TimeBucket(hourFormat.format(Date(bucketStart)), bytes)
            }
        }
        TimelineGranularity.DAILY -> {
            val dayMs = TimeUnit.DAYS.toMillis(1)
            val todayStart = startOfDayMillis(now)
            val dayFormat = SimpleDateFormat("d/M", Locale.getDefault())
            (13 downTo 0).map { offset ->
                val bucketStart = todayStart - offset * dayMs
                val bucketEnd = bucketStart + dayMs
                val bytes = entries
                    .asSequence()
                    .filter { it.timestamp >= bucketStart && it.timestamp < bucketEnd }
                    .sumOf { it.bytesUp + it.bytesDown }
                TimeBucket(dayFormat.format(Date(bucketStart)), bytes)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(entries: List<TrafficEntry>, onBack: () -> Unit) {
    val totalBytes = remember(entries) { entries.sumOf { it.bytesUp + it.bytesDown } }
    val blockedCount = remember(entries) { entries.count { it.blocked } }

    val byApp = remember(entries) {
        entries.groupBy { it.appLabel }
            .map { (label, list) ->
                Stat(
                    label = label,
                    bytes = list.sumOf { it.bytesUp + it.bytesDown },
                    bytesUp = list.sumOf { it.bytesUp },
                    bytesDown = list.sumOf { it.bytesDown },
                    count = list.size
                )
            }
            .sortedByDescending { it.bytes }
            .take(8)
    }
    val byDomain = remember(entries) {
        entries.groupBy { it.domain ?: it.destIp }
            .map { (label, list) ->
                Stat(
                    label = label,
                    bytes = list.sumOf { it.bytesUp + it.bytesDown },
                    bytesUp = list.sumOf { it.bytesUp },
                    bytesDown = list.sumOf { it.bytesDown },
                    count = list.size
                )
            }
            .sortedByDescending { it.bytes }
            .take(8)
    }

    var granularity by remember { mutableStateOf(TimelineGranularity.HOURLY) }
    val timelineBuckets = remember(entries, granularity) { buildTimelineBuckets(entries, granularity) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("İstatistikler", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard(title = "Toplam veri", value = formatBytes(totalBytes), modifier = Modifier.weight(1f))
                    SummaryCard(
                        title = "Engellenen",
                        value = blockedCount.toString(),
                        modifier = Modifier.weight(1f),
                        accent = AccentCoral,
                        icon = Icons.Filled.Block
                    )
                }
            }
            item { SectionTitle("En çok veri kullanan uygulamalar") }
            item { StatBarList(byApp) }
            item { SectionTitle("En çok bağlanılan adresler") }
            item { StatBarList(byDomain, monospace = true, showCategoryBadge = true) }
            item {
                SectionTitle("Trafik yoğunluğu")
                Spacer(modifier = Modifier.height(10.dp))
                TimelineChart(
                    buckets = timelineBuckets,
                    granularity = granularity,
                    onGranularityChange = { granularity = it }
                )
            }
        }
    }
}

@Composable
private fun TimelineChart(
    buckets: List<TimeBucket>,
    granularity: TimelineGranularity,
    onGranularityChange: (TimelineGranularity) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimelineGranularity.entries.forEach { option ->
                    FilterChip(
                        selected = granularity == option,
                        onClick = { onGranularityChange(option) },
                        label = { Text(option.label) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (buckets.all { it.bytes == 0L }) {
                Text("Henüz veri yok", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                return@Column
            }

            val maxBytes = buckets.maxOf { it.bytes }.coerceAtLeast(1)
            val labelStride = if (granularity == TimelineGranularity.HOURLY) 4 else 2

            Row(
                modifier = Modifier.fillMaxWidth().height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                buckets.forEach { bucket ->
                    val fraction = (bucket.bytes.toFloat() / maxBytes).coerceIn(0.02f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(if (bucket.bytes > 0) AccentViolet else AccentViolet.copy(alpha = 0.15f))
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                buckets.forEachIndexed { index, bucket ->
                    Text(
                        text = if (index % labelStride == 0) bucket.label else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "En yoğun ${if (granularity == TimelineGranularity.HOURLY) "saat" else "gün"}: ${formatBytes(maxBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = AccentViolet,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    Icon(it, contentDescription = null, tint = accent, modifier = Modifier.width(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(title, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun StatBarList(stats: List<Stat>, monospace: Boolean = false, showCategoryBadge: Boolean = false) {
    if (stats.isEmpty()) {
        Text("Henüz veri yok", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        return
    }
    val max = stats.maxOf { it.bytes }.coerceAtLeast(1)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            stats.forEachIndexed { index, stat ->
                val color = AvatarPalette[Math.floorMod(stat.label.hashCode(), AvatarPalette.size)]
                val fraction = (stat.bytes.toFloat() / max).coerceIn(0.03f, 1f)
                // Best-effort heuristic match against the domain/address string itself
                // (e.g. "googlevideo.com", "akamaiedge.net") - a lighter-weight cue than
                // the ASN-based match on the detail screen, just for a quick visual tag.
                val knownMatch = if (showCategoryBadge) {
                    com.cruciblelab.trafficlogger.util.KnownOrgCategorizer.categorize(stat.label, null)
                } else null
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stat.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (knownMatch != null) {
                            Text(
                                "✓ ${knownMatch.company} · ${knownMatch.category.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = com.cruciblelab.trafficlogger.ui.theme.AccentMint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatBytes(stat.bytes), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.ArrowUpward,
                                contentDescription = "Giden",
                                tint = TextTertiary,
                                modifier = Modifier.height(11.dp)
                            )
                            Text(
                                formatBytes(stat.bytesUp),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.ArrowDownward,
                                contentDescription = "Gelen",
                                tint = TextTertiary,
                                modifier = Modifier.height(11.dp)
                            )
                            Text(
                                formatBytes(stat.bytesDown),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(color.copy(alpha = 0.12f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(color)
                    )
                }
                if (index != stats.lastIndex) Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
