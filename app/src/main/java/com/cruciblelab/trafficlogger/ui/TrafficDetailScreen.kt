package com.cruciblelab.trafficlogger.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cruciblelab.trafficlogger.data.IpInfoCache
import com.cruciblelab.trafficlogger.data.TrafficEntry
import com.cruciblelab.trafficlogger.ui.theme.AccentMint
import com.cruciblelab.trafficlogger.util.KnownOrgCategorizer
import com.cruciblelab.trafficlogger.ui.theme.TextSecondary
import com.cruciblelab.trafficlogger.ui.theme.TextTertiary
import com.cruciblelab.trafficlogger.ui.theme.CardShapeLarge
import com.cruciblelab.trafficlogger.ui.theme.CardShapeSmall
import com.cruciblelab.trafficlogger.util.countryFlagEmoji
import com.cruciblelab.trafficlogger.util.formatBytes
import com.cruciblelab.trafficlogger.util.formatTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficDetailScreen(
    entry: TrafficEntry,
    history: List<TrafficEntry>,
    ipInfo: IpInfoCache?,
    onRequestIpInfo: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val totalUp = history.sumOf { it.bytesUp }
    val totalDown = history.sumOf { it.bytesDown }

    LaunchedEffect(entry.destIp) { onRequestIpInfo(entry.destIp) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(entry.appLabel, fontWeight = FontWeight.Bold) },
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
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            item {
                SoftCard {
                    Text(entry.domain ?: "Domain çözülemedi", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${entry.destIp}:${entry.destPort}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        StatItem(
                            icon = Icons.Filled.ArrowUpward,
                            label = "Yüklenen",
                            value = formatBytes(totalUp)
                        )
                        StatItem(
                            icon = Icons.Filled.ArrowDownward,
                            label = "İndirilen",
                            value = formatBytes(totalDown)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${history.size} bağlantı · ${entry.protocol}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                IpInfoCard(ipInfo = ipInfo, ip = entry.destIp)
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val domain = entry.domain
                    Button(
                        modifier = Modifier.weight(1f),
                        shape = CardShapeSmall,
                        enabled = domain != null,
                        onClick = {
                            if (domain != null) {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$domain"))
                                )
                            }
                        }
                    ) { Text("Google'da Ara") }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        shape = CardShapeSmall,
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${entry.appPackageName}")
                                )
                            )
                        }
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.height(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Uygulama Ayarları")
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text("Geçmiş", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(history, key = { it.id }) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatTimestamp(item.timestamp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        "↑${formatBytes(item.bytesUp)} · ↓${formatBytes(item.bytesDown)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun SoftCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShapeLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun StatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = TextTertiary, modifier = Modifier.height(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun IpInfoCard(ipInfo: IpInfoCache?, ip: String) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("IP Bilgisi", style = MaterialTheme.typography.titleSmall)
        }
        Spacer(modifier = Modifier.height(12.dp))

        when {
            ipInfo == null -> Text(
                "Sorgulanıyor…",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
            !ipInfo.success -> Text(
                "IP bilgisi alınamadı (bağlantı yok ya da servis yanıt vermedi).",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
            else -> {
                val flag = countryFlagEmoji(ipInfo.countryCode)
                InfoRow(
                    icon = null,
                    emoji = flag,
                    label = "Ülke",
                    value = ipInfo.countryName ?: "Bilinmiyor"
                )
                (ipInfo.org ?: ipInfo.isp)?.let {
                    InfoRow(icon = Icons.Filled.Business, emoji = null, label = "Sağlayıcı / ASN sahibi", value = it)
                }
                KnownOrgCategorizer.categorize(ipInfo.org, ipInfo.isp)?.let { match ->
                    if (match.category.sharedInfrastructure) {
                        // Kiralık altyapı: bu şirket sadece sunucuyu barındırıyor, hedefin kim
                        // olduğunu doğrulamaz. Zararlı bir sunucu da aynı buluta ait olabilir -
                        // bu yüzden bilgilendirici (nötr) göster, "onaylandı" gibi değil.
                        InfoRow(
                            icon = Icons.Filled.Business,
                            emoji = null,
                            label = "Barındırma",
                            value = "${match.company} (${match.category.displayName}) — hedefin kendisi değil, sadece sunucuyu kiraladığı yer"
                        )
                    } else {
                        InfoRow(
                            icon = Icons.Filled.CheckCircle,
                            emoji = null,
                            label = "Kategori",
                            value = "${match.company} · ${match.category.displayName}",
                            valueColor = AccentMint
                        )
                    }
                }
                ipInfo.asn?.let {
                    InfoRow(icon = Icons.Filled.Tag, emoji = null, label = "ASN", value = "AS$it")
                }
                InfoRow(icon = Icons.Filled.Language, emoji = null, label = "IP adresi", value = ip, monospace = true)
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    emoji: String?,
    label: String,
    value: String,
    monospace: Boolean = false,
    valueColor: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.width(160.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                emoji != null -> Text(emoji, modifier = Modifier.width(20.dp))
                icon != null -> Icon(icon, contentDescription = null, tint = TextTertiary, modifier = Modifier.height(16.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            color = valueColor ?: Color.Unspecified
        )
    }
}
