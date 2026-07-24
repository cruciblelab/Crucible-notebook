package com.cruciblelab.trafficlogger.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.InsertChartOutlined
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cruciblelab.trafficlogger.HomeSummary
import com.cruciblelab.trafficlogger.TopAppUsage
import com.cruciblelab.trafficlogger.ui.theme.AccentAmber
import com.cruciblelab.trafficlogger.ui.theme.AccentCoral
import com.cruciblelab.trafficlogger.ui.theme.AccentMint
import com.cruciblelab.trafficlogger.ui.theme.AccentViolet
import com.cruciblelab.trafficlogger.ui.theme.AvatarPalette
import com.cruciblelab.trafficlogger.ui.theme.TextSecondary
import com.cruciblelab.trafficlogger.ui.theme.TextTertiary
import com.cruciblelab.trafficlogger.util.AppCategoryClassifier
import com.cruciblelab.trafficlogger.util.formatBytes

/**
 * "Tek bakışta anlaşılır" ana ekran: teknik detaya girmeden bugünün özetini, en çok veri
 * kullanan uygulamaları ve "her şey tanıdık mı yoksa göz atman gereken bir şey var mı"
 * sorusunun cevabını verir. StatsScreen (detaylı istatistikler) hâlâ ayrı bir ekran olarak
 * duruyor; bu ekran onun bir üst katmanı / basitleştirilmiş girişi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    summary: HomeSummary,
    vpnRunning: Boolean,
    onToggleVpn: () -> Unit,
    onOpenList: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenReputation: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Ana Sayfa", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ayarlar", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { VpnStatusCard(vpnRunning = vpnRunning, onToggleVpn = onToggleVpn) }
            item { InsightCard(summary = summary, onOpenList = onOpenList, onOpenReputation = onOpenReputation) }
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Bugün toplam veri", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            formatBytes(summary.totalBytesToday),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            item { SectionLabel("En çok veri kullanan 3 uygulama") }
            if (summary.topApps.isEmpty()) {
                item {
                    Text(
                        "Henüz bugüne ait kayıt yok.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                }
            } else {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            summary.topApps.forEachIndexed { index, appUsage ->
                                TopAppRow(appUsage)
                                if (index != summary.topApps.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                                }
                            }
                        }
                    }
                }
            }
            item { SectionLabel("Hızlı erişim") }
            item {
                QuickNavGrid(
                    onOpenList = onOpenList,
                    onOpenStats = onOpenStats,
                    onOpenRules = onOpenRules,
                    onOpenReputation = onOpenReputation
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun VpnStatusCard(vpnRunning: Boolean, onToggleVpn: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(Modifier),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (vpnRunning) AccentMint.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
                .clickableNoRipple(onToggleVpn),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (vpnRunning) Icons.Filled.Shield else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = if (vpnRunning) AccentMint else AccentViolet,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (vpnRunning) "İzleme açık" else "İzleme kapalı",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (vpnRunning) "Dokun, durdur" else "Dokun, başlat",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(
                if (vpnRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = if (vpnRunning) AccentCoral else AccentViolet
            )
        }
    }
}

/** Basit tıklanabilir Row - Card zaten ripple/clickable eklemediğinden burada elle ekliyoruz. */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

@Composable
private fun InsightCard(
    summary: HomeSummary,
    onOpenList: () -> Unit,
    onOpenReputation: () -> Unit
) {
    val flaggedCount = summary.flaggedAppLabels.size
    val unknownCount = summary.unknownAppLabels.size

    val (icon, accent, title, subtitle, onClick) = when {
        flaggedCount > 0 -> Quintuple(
            Icons.Filled.Flag,
            AccentCoral,
            "$flaggedCount uygulama işaretli",
            summary.flaggedAppLabels.take(3).joinToString(", ") + " - incelemeni öneririz.",
            onOpenReputation
        )
        unknownCount > 0 -> Quintuple(
            Icons.Filled.HelpOutline,
            AccentAmber,
            "$unknownCount uygulama bilinmiyor",
            summary.unknownAppLabels.take(3).joinToString(", ") + " - bir bak istersen.",
            onOpenList
        )
        summary.topApps.isEmpty() -> Quintuple(
            Icons.Filled.VerifiedUser,
            TextTertiary,
            "Henüz veri yok",
            "İzleme başladığında bugünkü uygulamalarını burada özetleyeceğiz.",
            onOpenList
        )
        else -> Quintuple(
            Icons.Filled.CheckCircle,
            AccentMint,
            "Hepsi tanıdık ve güvenli",
            "Bugün tespit edilen tüm uygulamalar bilinen/sistem uygulamaları.",
            onOpenList
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickableNoRipple(onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextTertiary)
        }
    }
}

private data class Quintuple(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accent: Color,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@Composable
private fun TopAppRow(appUsage: TopAppUsage) {
    val avatarColor = AvatarPalette[Math.floorMod(appUsage.label.hashCode(), AvatarPalette.size)]
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(avatarColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(appUsage.label.take(1).uppercase(), color = avatarColor, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(appUsage.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            CategoryBadge(appUsage.category)
        }
        Text(formatBytes(appUsage.bytes), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CategoryBadge(category: AppCategoryClassifier.Category) {
    val (label, color) = when (category) {
        AppCategoryClassifier.Category.SYSTEM -> "Sistem uygulaması" to TextTertiary
        AppCategoryClassifier.Category.TRUSTED -> "Bilinen / güvenilir" to AccentMint
        AppCategoryClassifier.Category.UNKNOWN -> "Bilinmiyor" to AccentAmber
        AppCategoryClassifier.Category.FLAGGED -> "İşaretli" to AccentCoral
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun QuickNavGrid(
    onOpenList: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenReputation: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickNavCard("Tüm bağlantılar", Icons.Filled.InsertChartOutlined, Modifier.weight(1f), onOpenList)
            QuickNavCard("İstatistikler", Icons.Filled.InsertChartOutlined, Modifier.weight(1f), onOpenStats)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickNavCard("Kara/Beyaz Liste", Icons.Filled.Shield, Modifier.weight(1f), onOpenRules)
            QuickNavCard("İtibar Veritabanları", Icons.Filled.Security, Modifier.weight(1f), onOpenReputation)
        }
    }
}

@Composable
private fun QuickNavCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickableNoRipple(onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}
