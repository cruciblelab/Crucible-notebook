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
import androidx.compose.material.icons.filled.Block
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.cruciblelab.trafficlogger.BlockedDestinationSummary
import com.cruciblelab.trafficlogger.HomeSummary
import com.cruciblelab.trafficlogger.ObservedOtherDomain
import com.cruciblelab.trafficlogger.TopAppUsage
import com.cruciblelab.trafficlogger.ui.theme.AccentAmber
import com.cruciblelab.trafficlogger.ui.theme.AccentCoral
import com.cruciblelab.trafficlogger.ui.theme.AccentCritical
import com.cruciblelab.trafficlogger.ui.theme.AccentMint
import com.cruciblelab.trafficlogger.ui.theme.AccentViolet
import com.cruciblelab.trafficlogger.ui.theme.AvatarPalette
import com.cruciblelab.trafficlogger.ui.theme.TextSecondary
import com.cruciblelab.trafficlogger.ui.theme.TextTertiary
import com.cruciblelab.trafficlogger.util.AppCategoryClassifier
import com.cruciblelab.trafficlogger.util.formatBytes
import com.cruciblelab.trafficlogger.util.formatTimestamp

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
    companyProtectionStates: Map<String, com.cruciblelab.trafficlogger.data.CompanyProtectionState>,
    onSetTrackingBlocked: (com.cruciblelab.trafficlogger.data.TrackerCatalog.Company, Boolean) -> Unit,
    onSetFullyBlocked: (com.cruciblelab.trafficlogger.data.TrackerCatalog.Company, Boolean) -> Unit,
    ipInfoMap: Map<String, com.cruciblelab.trafficlogger.data.IpInfoCache>,
    onRequestIpInfo: (String) -> Unit,
    onQuickBlockDomain: (String) -> Unit,
    onOpenList: () -> Unit,
    onOpenBlockedList: () -> Unit,
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
            if (summary.blockedCountToday > 0) {
                item { SectionLabel("Bugün engellenenler") }
                item {
                    BlockedTodayCard(
                        blockedCount = summary.blockedCountToday,
                        topBlocked = summary.topBlockedToday,
                        onOpenBlockedList = onOpenBlockedList
                    )
                }
            }
            item { SectionLabel("Veri toplama kontrolleri") }
            item {
                PrivacyControlsCard(
                    companyProtectionStates = companyProtectionStates,
                    onSetTrackingBlocked = onSetTrackingBlocked,
                    onSetFullyBlocked = onSetFullyBlocked
                )
            }
            if (summary.otherObservedDomains.isNotEmpty()) {
                item { SectionLabel("Listede olmayan, bugün görülen diğer kaynaklar") }
                item {
                    OtherSourcesCard(
                        domains = summary.otherObservedDomains,
                        ipInfoMap = ipInfoMap,
                        onRequestIpInfo = onRequestIpInfo,
                        onQuickBlockDomain = onQuickBlockDomain
                    )
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

/**
 * "Basit üstte, karmaşık derinde" ilkesi: her satır tek bir switch (sadece izleme uçlarını
 * keser) ve tek cümlelik açıklama - hiçbir teknik terim yok. Şirketin TÜM domain'lerini
 * kesen ("tamamen engelle") ileri düzey seçenek, satıra dokunup açan (varsayılan kapalı)
 * bir alt bölümde saklı ve etkinleştirmeden önce onay istiyor - çünkü o seçenek ana
 * uygulamayı/siteyi de kullanılamaz hale getirir.
 */
@Composable
private fun PrivacyControlsCard(
    companyProtectionStates: Map<String, com.cruciblelab.trafficlogger.data.CompanyProtectionState>,
    onSetTrackingBlocked: (com.cruciblelab.trafficlogger.data.TrackerCatalog.Company, Boolean) -> Unit,
    onSetFullyBlocked: (com.cruciblelab.trafficlogger.data.TrackerCatalog.Company, Boolean) -> Unit
) {
    var fullBlockCandidate by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<com.cruciblelab.trafficlogger.data.TrackerCatalog.Company?>(null)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            com.cruciblelab.trafficlogger.data.TrackerCatalog.ALL.forEachIndexed { index, company ->
                val state = companyProtectionStates[company.key]
                    ?: com.cruciblelab.trafficlogger.data.CompanyProtectionState(trackingBlocked = false, fullyBlocked = false)
                TrackerCompanyRow(
                    company = company,
                    state = state,
                    onSetTrackingBlocked = { onSetTrackingBlocked(company, it) },
                    onRequestFullBlock = { requestOn ->
                        if (requestOn) fullBlockCandidate = company else onSetFullyBlocked(company, false)
                    }
                )
                if (index != com.cruciblelab.trafficlogger.data.TrackerCatalog.ALL.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                }
            }
        }
    }

    fullBlockCandidate?.let { company ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { fullBlockCandidate = null },
            title = { Text("${company.title} tamamen engellensin mi?") },
            text = {
                Text(
                    "Bu, sadece reklam/izleme uçlarını değil, ${company.title}'e ait TÜM " +
                        "servisleri keser - ana uygulama(lar)/site(ler) de dahil olmak üzere " +
                        "artık çalışmayabilir. İleri düzey bir seçenektir."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onSetFullyBlocked(company, true)
                    fullBlockCandidate = null
                }) { Text("Tamamen Engelle", color = AccentCritical) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { fullBlockCandidate = null }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
private fun TrackerCompanyRow(
    company: com.cruciblelab.trafficlogger.data.TrackerCatalog.Company,
    state: com.cruciblelab.trafficlogger.data.CompanyProtectionState,
    onSetTrackingBlocked: (Boolean) -> Unit,
    onRequestFullBlock: (Boolean) -> Unit
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val allowed = !state.trackingBlocked
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(company.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(
                    if (allowed) company.simpleDescription else "Şu an engelleniyor - bu verileri artık toplayamıyor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (allowed) TextSecondary else AccentMint
                )
                if (state.fullyBlocked) {
                    Text(
                        "⚠ Tamamen engellenmiş durumda",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCritical,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            androidx.compose.material3.Switch(checked = allowed, onCheckedChange = { onSetTrackingBlocked(!it) })
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (expanded) "Teknik detayı gizle" else "Teknik detay (hangi servisler? ileri düzey seçenekler)",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
        if (expanded) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    "İzleme uçları: " + company.trackingDomains.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
                if (company.fullBlockDomains.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "İleri düzey: ${company.title}'i tamamen engelle",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = AccentCritical
                            )
                            Text(
                                "Ana uygulama/site dahil, ${company.title}'e ait her şeyi keser.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = state.fullyBlocked,
                            onCheckedChange = onRequestFullBlock
                        )
                    }
                }
            }
        }
    }
}

/**
 * Kürasyonlu listede (TrackerCatalog) olmayan ama bugün trafiğinde görülen domain'ler burada
 * SESSİZCE ATLANMAK yerine gösterilir. İsim listede yoksa, o IP'nin çözülmüş ASN/organizasyon
 * bilgisiyle ("kim barındırıyor/kime ait") gösterilir - bilgi henüz çözülmediyse tek seferlik
 * bir istek kuyruğa alınır (bkz. MainViewModel.requestIpInfo - aralıklı/ekonomik).
 */
@Composable
private fun OtherSourcesCard(
    domains: List<ObservedOtherDomain>,
    ipInfoMap: Map<String, com.cruciblelab.trafficlogger.data.IpInfoCache>,
    onRequestIpInfo: (String) -> Unit,
    onQuickBlockDomain: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            domains.forEachIndexed { index, item ->
                androidx.compose.runtime.LaunchedEffect(item.destIp) { onRequestIpInfo(item.destIp) }
                val info = ipInfoMap[item.destIp]
                val orgMatch = info?.let {
                    com.cruciblelab.trafficlogger.util.KnownOrgCategorizer.categorize(it.org, it.isp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.domain,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.appLabels.isNotEmpty()) {
                            Text(
                                item.appLabels.first() +
                                    if (item.appLabels.size > 1) " +${item.appLabels.size - 1} diğer" else "",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = AccentViolet,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            when {
                                orgMatch != null -> "${orgMatch.company} · ${orgMatch.category.displayName}" +
                                    if (orgMatch.category.sharedInfrastructure) " (barındırma, sahibi değil)" else ""
                                info != null -> info.org ?: info.isp ?: info.countryName ?: "Kaynağı bilinmiyor"
                                else -> "Çözülüyor…"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    androidx.compose.material3.TextButton(onClick = { onQuickBlockDomain(item.domain) }) {
                        Text("Engelle", color = AccentCoral)
                    }
                }
                if (index != domains.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                }
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

/**
 * Bugün engellenen bağlantı denemelerini özetler. ÖNEMLİ: engellenen bağlantılar hiçbir
 * veri aktarmadan (bağlanmadan önce) reddedilir, bu yüzden "kaç bayt engellendi" diye bir
 * sayı YOKTUR - göstermek yanıltıcı olurdu. Bunun yerine "kaç kez denendi" gösteriyoruz;
 * kullanıcının asıl merak ettiği "ne engellendi, kim deniyor" sorusuna da domain + uygulama
 * + son deneme zamanıyla cevap veriyoruz.
 */
@Composable
private fun BlockedTodayCard(
    blockedCount: Int,
    topBlocked: List<BlockedDestinationSummary>,
    onOpenBlockedList: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Block, contentDescription = null, tint = AccentCoral, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "$blockedCount bağlantı denemesi engellendi",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Kurallara takılan bağlantılar hiçbir veri aktarmadan reddedildi, bu yüzden burada bir \"veri miktarı\" yok - yalnızca kaç kez denendiğini gösteriyoruz.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }
            if (topBlocked.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.background)
                topBlocked.forEach { item -> BlockedRow(item) }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickableNoRipple(onOpenBlockedList),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tümünü gör", style = MaterialTheme.typography.labelLarge, color = AccentViolet)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun BlockedRow(item: BlockedDestinationSummary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.target,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                (item.appLabel?.let { "$it · " } ?: "") + "son deneme: ${formatTimestamp(item.lastAttemptAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "${item.attemptCount}×",
            style = MaterialTheme.typography.labelLarge,
            color = AccentCoral,
            fontWeight = FontWeight.Bold
        )
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
        AppCategoryClassifier.Category.SIGNATURE_MISMATCH -> "⚠ İmza değişti!" to AccentCritical
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
