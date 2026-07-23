package com.cruciblelab.trafficlogger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cruciblelab.trafficlogger.R

private val RETENTION_OPTIONS = listOf(1, 7, 30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    retentionDays: Int,
    onRetentionChange: (Int) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
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
                    Text(stringResource(R.string.vpn_warning), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.https_disclaimer), style = MaterialTheme.typography.bodyMedium)
            }

            Text(
                "Kayıt saklama süresi",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
            Row {
                RETENTION_OPTIONS.forEach { days ->
                    FilterChip(
                        modifier = Modifier.padding(end = 8.dp),
                        selected = retentionDays == days,
                        onClick = { onRetentionChange(days) },
                        label = { Text("$days gün") }
                    )
                }
            }

            OutlinedButton(
                modifier = Modifier.padding(top = 24.dp),
                onClick = onClearHistory
            ) {
                Text("Tüm kayıtları temizle")
            }
        }
    }
}
