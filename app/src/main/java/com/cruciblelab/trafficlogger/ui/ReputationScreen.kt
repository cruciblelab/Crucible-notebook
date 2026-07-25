package com.cruciblelab.trafficlogger.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cruciblelab.trafficlogger.R
import com.cruciblelab.trafficlogger.data.PresetReputationCatalog
import com.cruciblelab.trafficlogger.data.ReputationSource
import com.cruciblelab.trafficlogger.ui.theme.AccentAmber
import com.cruciblelab.trafficlogger.ui.theme.AccentCoral
import com.cruciblelab.trafficlogger.ui.theme.AccentMint
import com.cruciblelab.trafficlogger.ui.theme.AccentViolet
import com.cruciblelab.trafficlogger.ui.theme.TextSecondary
import com.cruciblelab.trafficlogger.ui.theme.TextTertiary
import com.cruciblelab.trafficlogger.ui.theme.CardShapeMedium
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReputationScreen(
    sources: List<ReputationSource>,
    presets: List<PresetReputationCatalog.Preset>,
    onLoadPreset: (PresetReputationCatalog.Preset) -> Unit,
    onImportCustom: (name: String, json: String, onResult: (Result<Int>) -> Unit) -> Unit,
    onSetEnabled: (ReputationSource, Boolean) -> Unit,
    onSetAutoBlock: (ReputationSource, Boolean) -> Unit,
    onDeleteSource: (ReputationSource) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<ReputationSource?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()?.let { text ->
            pendingImportJson = text
        } ?: scope.launch { snackbarHostState.showSnackbar("Dosya okunamadı.") }
    }

    val loadedKeys = remember(sources) { sources.map { it.key }.toSet() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        topBar = {
            TopAppBar(
                title = { Text("İtibar Veritabanları", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ReputationDisclaimerCard() }
            item { SectionLabel("Hazır veritabanları") }
            items(presets, key = { it.key }) { preset ->
                PresetCard(
                    preset = preset,
                    loaded = preset.key in loadedKeys,
                    onLoad = { onLoadPreset(preset) }
                )
            }

            item { SectionLabel("Yüklenen veritabanları") }
            if (sources.isEmpty()) {
                item {
                    Text(
                        "Henüz bir veritabanı yüklenmedi. Yukarıdan hazır bir veritabanı yükleyebilir ya da " +
                            "aşağıdan kendi listeni içe aktarabilirsin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                }
            } else {
                items(sources, key = { it.id }) { source ->
                    SourceCard(
                        source = source,
                        onSetEnabled = { onSetEnabled(source, it) },
                        onSetAutoBlock = { onSetAutoBlock(source, it) },
                        onDelete = { deleteCandidate = source }
                    )
                }
            }

            item { SectionLabel("Özel veritabanı içe aktar") }
            item {
                Card(
                    shape = CardShapeMedium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Kendi güvendiğin bir kaynaktan (kendi listen, bir topluluk projesi vb.) JSON " +
                                "formatında bir dosya seç. Format: her eleman { \"package\", \"verdict\": " +
                                "\"FLAGGED\"|\"TRUSTED\", \"matchType\": \"EXACT\"|\"PREFIX\" }.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = { filePicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("JSON dosyası seç")
                        }
                    }
                }
            }
        }
    }

    pendingImportJson?.let { json ->
        ImportNameDialog(
            onDismiss = { pendingImportJson = null },
            onConfirm = { name ->
                onImportCustom(name, json) { result ->
                    scope.launch {
                        result.onSuccess { count ->
                            snackbarHostState.showSnackbar("$count giriş içe aktarıldı.")
                        }.onFailure { e ->
                            snackbarHostState.showSnackbar(e.message ?: "İçe aktarma başarısız oldu.")
                        }
                    }
                }
                pendingImportJson = null
            }
        )
    }

    deleteCandidate?.let { source ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Veritabanını sil") },
            text = { Text("\"${source.name}\" veritabanı ve içindeki tüm girişler silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSource(source)
                    deleteCandidate = null
                }) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
private fun ReputationDisclaimerCard() {
    Card(
        shape = CardShapeMedium,
        colors = CardDefaults.cardColors(containerColor = AccentAmber.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                stringResource(R.string.reputation_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun PresetCard(
    preset: PresetReputationCatalog.Preset,
    loaded: Boolean,
    onLoad: () -> Unit
) {
    Card(
        shape = CardShapeMedium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = AccentMint)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(preset.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(preset.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (loaded) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Yüklendi", tint = AccentMint)
            } else {
                TextButton(onClick = onLoad) { Text("Yükle") }
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: ReputationSource,
    onSetEnabled: (Boolean) -> Unit,
    onSetAutoBlock: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = CardShapeMedium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = AccentViolet)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(source.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${source.entryCount} giriş" + if (source.isPreset) " - hazır veritabanı" else " - özel içe aktarım",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Sil", tint = AccentCoral)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Aktif", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = source.enabled, onCheckedChange = onSetEnabled)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Otomatik engelle", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Yalnızca birebir (EXACT) eşleşen işaretli paketler",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
                Switch(checked = source.autoBlock, onCheckedChange = onSetAutoBlock, enabled = source.enabled)
            }
        }
    }
}

@Composable
private fun ImportNameDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Veritabanına isim ver") },
        text = {
            Column {
                Text(
                    "Bu içe aktarılan listeyi daha sonra ayırt edebilmen için bir isim gir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("İsim") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.ifBlank { "Özel veritabanı" }) }) { Text("İçe aktar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        }
    )
}
