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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cruciblelab.trafficlogger.R
import com.cruciblelab.trafficlogger.ui.theme.TextSecondary

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
                InfoCard(stringResource(R.string.vpn_warning))
                Spacer(modifier = Modifier.height(12.dp))
                InfoCard(stringResource(R.string.https_disclaimer))

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

                OutlinedButton(
                    modifier = Modifier.padding(top = 28.dp),
                    shape = RoundedCornerShape(14.dp),
                    onClick = onClearHistory
                ) {
                    Text("Tüm kayıtları temizle")
                }
            }
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
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
