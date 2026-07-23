package com.cruciblelab.trafficlogger.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cruciblelab.trafficlogger.data.TrafficEntry
import com.cruciblelab.trafficlogger.util.formatBytes
import com.cruciblelab.trafficlogger.util.formatTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficDetailScreen(
    entry: TrafficEntry,
    history: List<TrafficEntry>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val totalUp = history.sumOf { it.bytesUp }
    val totalDown = history.sumOf { it.bytesDown }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry.appLabel) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(entry.domain ?: "Domain çözülemedi", style = MaterialTheme.typography.titleMedium)
                    Text("${entry.destIp}:${entry.destPort} · ${entry.protocol}")
                    Text("Toplam veri: ${formatBytes(totalUp + totalDown)} (↑${formatBytes(totalUp)} / ↓${formatBytes(totalDown)})")
                    Text("Bağlantı sayısı: ${history.size}")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val domain = entry.domain
                Button(
                    modifier = Modifier.fillMaxWidth(0.5f),
                    enabled = domain != null,
                    onClick = {
                        if (domain != null) {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.google.com/search?q=$domain")
                            )
                            context.startActivity(intent)
                        }
                    }
                ) {
                    Text("Google'da Ara")
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${entry.appPackageName}")
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Text("Uygulama Ayarları")
                }
            }

            Divider()
            Text("Geçmiş", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))

            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(history, key = { it.id }) { item ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(formatTimestamp(item.timestamp))
                        Text("↑${formatBytes(item.bytesUp)} / ↓${formatBytes(item.bytesDown)}")
                    }
                    Divider()
                }
            }
        }
    }
}
