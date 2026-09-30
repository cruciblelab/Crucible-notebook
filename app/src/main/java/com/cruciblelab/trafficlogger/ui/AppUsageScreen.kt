package com.cruciblelab.trafficlogger.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cruciblelab.trafficlogger.data.TrafficEntry
import com.cruciblelab.trafficlogger.ui.theme.AccentMint
import com.cruciblelab.trafficlogger.ui.theme.AccentViolet
import com.cruciblelab.trafficlogger.ui.theme.CardShapeLarge
import com.cruciblelab.trafficlogger.ui.theme.CardShapeSmall
import com.cruciblelab.trafficlogger.ui.theme.TextSecondary
import com.cruciblelab.trafficlogger.ui.theme.TextTertiary
import com.cruciblelab.trafficlogger.util.formatBytes
import com.cruciblelab.trafficlogger.util.startOfDayMillis
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val UploadColor = AccentViolet
private val DownloadColor = AccentMint

private enum class UsageRange(val label: String) {
    HOURLY("Son 24 saat"),
    DAILY("Son 7 gün")
}

private data class UsageBar(val label: String, val up: Long, val down: Long) {
    val total get() = up + down
}

private data class DestinationUsage(val target: String, val up: Long, val down: Long) {
    val total get() = up + down
}

/** Tek geçişte her kaydı kendi saat/gün kovasına yazar (kayıt, bağlantının başladığı ana göre). */
private fun buildBars(entries: List<TrafficEntry>, range: UsageRange): List<UsageBar> {
    val now = System.currentTimeMillis()
    val (bucketMs, count, firstStart, format) = when (range) {
        UsageRange.HOURLY -> {
            val hourMs = TimeUnit.HOURS.toMillis(1)
            BucketSpec(hourMs, 24, (now / hourMs) * hourMs - 23 * hourMs, SimpleDateFormat("HH", Locale.getDefault()))
        }
        UsageRange.DAILY -> {
            val dayMs = TimeUnit.DAYS.toMillis(1)
            BucketSpec(dayMs, 7, startOfDayMillis(now) - 6 * dayMs, SimpleDateFormat("d/M", Locale.getDefault()))
        }
    }
    val up = LongArray(count)
    val down = LongArray(count)
    for (entry in entries) {
        if (entry.timestamp < firstStart) continue
        val index = ((entry.timestamp - firstStart) / bucketMs).toInt()
        if (index !in 0 until count) continue
        up[index] += entry.bytesUp
        down[index] += entry.bytesDown
    }
    return (0 until count).map { i ->
        UsageBar(format.format(Date(firstStart + i * bucketMs)), up[i], down[i])
    }
}

private data class BucketSpec(val bucketMs: Long, val count: Int, val firstStart: Long, val format: SimpleDateFormat)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUsageScreen(
    packageName: String,
    entries: List<TrafficEntry>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appEntries = remember(entries, packageName) {
        entries.filter { it.appPackageName == packageName && !it.blocked }
    }
    val label = remember(entries, packageName) {
        entries.firstOrNull { it.appPackageName == packageName }?.appLabel ?: packageName
    }
    val todayStart = startOfDayMillis()
    val weekStart = todayStart - TimeUnit.DAYS.toMillis(6)
    val today = remember(appEntries) { appEntries.filter { it.timestamp >= todayStart } }
    val week = remember(appEntries) { appEntries.filter { it.timestamp >= weekStart } }
    val blockedAttempts = remember(entries, packageName) {
        entries.count { it.appPackageName == packageName && it.blocked && it.timestamp >= weekStart }
    }
    val destinations = remember(week) {
        week.groupBy { it.domain ?: it.destIp }
            .map { (target, list) -> DestinationUsage(target, list.sumOf { it.bytesUp }, list.sumOf { it.bytesDown }) }
            .sortedByDescending { it.total }
            .take(10)
    }

    var range by remember { mutableStateOf(UsageRange.HOURLY) }
    val bars = remember(appEntries, range) { buildBars(appEntries, range) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                    UsageSummaryCard("Bugün", today.sumOf { it.bytesUp }, today.sumOf { it.bytesDown }, Modifier.weight(1f))
                    UsageSummaryCard("Son 7 gün", week.sumOf { it.bytesUp }, week.sumOf { it.bytesDown }, Modifier.weight(1f))
                }
                Text(
                    "7 günde ${week.sumOf { it.connectionCount }} bağlantı" +
                        if (blockedAttempts > 0) " · $blockedAttempts engellenen deneme kaydı" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                Card(
                    shape = CardShapeLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UsageRange.entries.forEach { option ->
                                FilterChip(
                                    selected = range == option,
                                    onClick = { range = option },
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
                        UpDownBarChart(bars, labelStride = if (range == UsageRange.HOURLY) 4 else 1)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LegendDot(UploadColor, "Giden")
                            Spacer(modifier = Modifier.width(16.dp))
                            LegendDot(DownloadColor, "Gelen")
                        }
                        Text(
                            "Veri, bağlantının başladığı saate yazılır. Ayarlar'daki saklama süresinden eski kayıtlar görünmez.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            item {
                Text("En çok veri alışverişi yapılan adresler (7 gün)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                DestinationList(destinations)
            }
            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShapeSmall,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                        )
                    }
                ) {
                    Text("Uygulama ayarları")
                }
            }
        }
    }
}

@Composable
private fun UsageSummaryCard(title: String, up: Long, down: Long, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = CardShapeLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(formatBytes(up + down), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("↑ ${formatBytes(up)}  ↓ ${formatBytes(down)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

/** Giden (üstte) ve gelen (altta) veriyi aynı sütunda yığılmış gösterir. */
@Composable
private fun UpDownBarChart(bars: List<UsageBar>, labelStride: Int) {
    if (bars.all { it.total == 0L }) {
        Text("Bu aralıkta veri yok", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        return
    }
    val max = bars.maxOf { it.total }.coerceAtLeast(1)
    Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(1f)
        bars.forEachIndexed { index, bar ->
            val x = index * (barWidth + gap)
            if (bar.total == 0L) {
                drawRect(UploadColor.copy(alpha = 0.12f), Offset(x, size.height - 2.dp.toPx()), Size(barWidth, 2.dp.toPx()))
                return@forEachIndexed
            }
            val downHeight = size.height * bar.down / max
            val upHeight = size.height * bar.up / max
            drawRect(DownloadColor, Offset(x, size.height - downHeight), Size(barWidth, downHeight))
            drawRect(UploadColor, Offset(x, size.height - downHeight - upHeight), Size(barWidth, upHeight))
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        bars.forEachIndexed { index, bar ->
            Text(
                text = if (index % labelStride == 0) bar.label else "",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f)
            )
        }
    }
    Text(
        "En yoğun: ${formatBytes(max)}",
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
    Spacer(modifier = Modifier.width(6.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
}

@Composable
private fun DestinationList(destinations: List<DestinationUsage>) {
    if (destinations.isEmpty()) {
        Text("Henüz veri yok", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        return
    }
    val max = destinations.maxOf { it.total }.coerceAtLeast(1)
    Card(
        shape = CardShapeLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            destinations.forEach { destination ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            destination.target,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "↑ ${formatBytes(destination.up)}  ↓ ${formatBytes(destination.down)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val fraction = (destination.total.toFloat() / max).coerceIn(0.03f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(UploadColor.copy(alpha = 0.12f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(UploadColor)
                        )
                    }
                }
            }
        }
    }
}
