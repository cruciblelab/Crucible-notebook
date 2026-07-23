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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private data class Stat(val label: String, val bytes: Long, val count: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(entries: List<TrafficEntry>, onBack: () -> Unit) {
    val totalBytes = remember(entries) { entries.sumOf { it.bytesUp + it.bytesDown } }
    val blockedCount = remember(entries) { entries.count { it.blocked } }

    val byApp = remember(entries) {
        entries.groupBy { it.appLabel }
            .map { (label, list) -> Stat(label, list.sumOf { it.bytesUp + it.bytesDown }, list.size) }
            .sortedByDescending { it.bytes }
            .take(8)
    }
    val byDomain = remember(entries) {
        entries.groupBy { it.domain ?: it.destIp }
            .map { (label, list) -> Stat(label, list.sumOf { it.bytesUp + it.bytesDown }, list.size) }
            .sortedByDescending { it.bytes }
            .take(8)
    }

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
            item { StatBarList(byDomain, monospace = true) }
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
private fun StatBarList(stats: List<Stat>, monospace: Boolean = false) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stat.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(formatBytes(stat.bytes), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
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
