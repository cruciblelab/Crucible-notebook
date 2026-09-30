package com.cruciblelab.trafficlogger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cruciblelab.trafficlogger.R
import com.cruciblelab.trafficlogger.ui.theme.TextSecondary
import com.cruciblelab.trafficlogger.ui.theme.CardShapeMedium
import com.cruciblelab.trafficlogger.ui.theme.CardShapeSmall
import com.cruciblelab.trafficlogger.update.UpdateState

private val RETENTION_OPTIONS = listOf(1, 7, 30)

/** 0 = disabled ("Kapalı"); others are megabytes. */
private val DAILY_LIMIT_OPTIONS = listOf(0, 100, 500, 1000, 2000)

private fun dailyLimitLabel(mb: Int): String = when {
    mb <= 0 -> "Kapalı"
    mb < 1000 -> "$mb MB"
    else -> "${mb / 1000} GB"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    retentionDays: Int,
    onRetentionChange: (Int) -> Unit,
    dailyLimitMb: Int,
    onDailyLimitChange: (Int) -> Unit,
    blockKnownDoh: Boolean,
    onBlockKnownDohChange: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onForceResetNetwork: () -> Unit,
    onOpenSystemVpnSettings: () -> Unit,
    appVersionName: String,
    updateState: UpdateState,
    autoUpdateCheck: Boolean,
    onAutoUpdateCheckChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onShowUpdatePrompt: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar", fontWeight = FontWeight.Bold) },
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
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                UpdateCard(
                    appVersionName = appVersionName,
                    state = updateState,
                    autoUpdateCheck = autoUpdateCheck,
                    onAutoUpdateCheckChange = onAutoUpdateCheckChange,
                    onCheckForUpdates = onCheckForUpdates,
                    onShowUpdatePrompt = onShowUpdatePrompt
                )
                Spacer(modifier = Modifier.height(12.dp))
                InfoCard(stringResource(R.string.vpn_warning))
                Spacer(modifier = Modifier.height(12.dp))
                InfoCard(stringResource(R.string.https_disclaimer))
                Spacer(modifier = Modifier.height(12.dp))
                InfoCard(stringResource(R.string.data_accuracy_disclaimer))

                Text(
                    "Kayıt saklama süresi",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RETENTION_OPTIONS.forEach { days ->
                        FilterChip(
                            selected = retentionDays == days,
                            onClick = { onRetentionChange(days) },
                            label = { Text("$days gün") },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Text(
                    "Uygulama başına günlük veri uyarısı",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)
                )
                Text(
                    "Bir uygulama gün içinde seçilen sınırı aşınca bildirim gönderilir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(DAILY_LIMIT_OPTIONS) { mb ->
                        FilterChip(
                            selected = dailyLimitMb == mb,
                            onClick = { onDailyLimitChange(mb) },
                            label = { Text(dailyLimitLabel(mb)) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bilinen DoH sunucularını engelle", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Google/Cloudflare/Quad9 gibi bilinen genel DoH (DNS-over-HTTPS) " +
                                "sunucularına giden bağlantılar tamamen engellenir - böylece DNS " +
                                "sorguları bu VPN'i şifreli bir kanaldan atlayamaz. Kapsamlı bir " +
                                "engelleme değildir: listelenmeyen özel bir DoH sunucusu bu şekilde " +
                                "yakalanmaz.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(checked = blockKnownDoh, onCheckedChange = onBlockKnownDohChange)
                }

                OutlinedButton(
                    modifier = Modifier.padding(top = 28.dp),
                    shape = CardShapeSmall,
                    onClick = onClearHistory
                ) {
                    Text("Tüm kayıtları temizle")
                }

                Text(
                    stringResource(R.string.network_reset_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 32.dp, bottom = 4.dp)
                )
                Text(
                    stringResource(R.string.network_reset_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShapeSmall,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = onForceResetNetwork
                ) {
                    Text(stringResource(R.string.network_reset_action))
                }

                Text(
                    stringResource(R.string.network_reset_open_system_settings_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShapeSmall,
                    onClick = onOpenSystemVpnSettings
                ) {
                    Text(stringResource(R.string.network_reset_open_system_settings))
                }
            }
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShapeMedium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(16.dp)
        )
    }
}

private fun updateStatusText(state: UpdateState): String = when (state) {
    UpdateState.Idle -> "Güncellemeler henüz denetlenmedi."
    UpdateState.Checking -> "Denetleniyor…"
    UpdateState.UpToDate -> "En güncel sürümü kullanıyorsunuz."
    is UpdateState.Available -> "Yeni sürüm mevcut: v${state.release.versionName}"
    is UpdateState.Downloading ->
        state.progress?.let { "İndiriliyor… %${(it * 100).toInt()}" } ?: "İndiriliyor…"
    is UpdateState.NeedsInstallPermission -> "İndirildi - kurulum izni bekleniyor."
    is UpdateState.ReadyToInstall -> "İndirildi - kuruluma hazır."
    is UpdateState.Failed -> state.message
}

@Composable
private fun UpdateCard(
    appVersionName: String,
    state: UpdateState,
    autoUpdateCheck: Boolean,
    onAutoUpdateCheckChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onShowUpdatePrompt: () -> Unit
) {
    // Bir sürüm bulunduysa (indiriliyor/kurulum bekliyor dahil) düğme diyaloğu geri açar.
    val hasPendingRelease = when (state) {
        is UpdateState.Available, is UpdateState.Downloading,
        is UpdateState.NeedsInstallPermission, is UpdateState.ReadyToInstall -> true
        is UpdateState.Failed -> state.release != null
        else -> false
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShapeMedium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Uygulama güncellemeleri", style = MaterialTheme.typography.titleSmall)
            Text(
                "Kurulu sürüm: v$appVersionName",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                updateStatusText(state),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = CardShapeSmall,
                enabled = state != UpdateState.Checking,
                onClick = if (hasPendingRelease) onShowUpdatePrompt else onCheckForUpdates
            ) {
                Text(if (hasPendingRelease) "Güncellemeyi göster" else "Güncellemeleri denetle")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Açılışta otomatik denetle", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Yalnızca GitHub'daki son sürüm bilgisi okunur; siz onaylamadan hiçbir şey indirilmez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Switch(checked = autoUpdateCheck, onCheckedChange = onAutoUpdateCheckChange)
            }
        }
    }
}
