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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cruciblelab.trafficlogger.data.NetworkProfile
import com.cruciblelab.trafficlogger.ui.theme.AccentAmber
import com.cruciblelab.trafficlogger.ui.theme.AccentCoral
import com.cruciblelab.trafficlogger.ui.theme.AccentMint
import com.cruciblelab.trafficlogger.ui.theme.AccentViolet
import com.cruciblelab.trafficlogger.ui.theme.TextSecondary
import com.cruciblelab.trafficlogger.ui.theme.TextTertiary
import com.cruciblelab.trafficlogger.util.ResolvedApp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    profiles: List<NetworkProfile>,
    activeProfile: NetworkProfile?,
    installedApps: List<ResolvedApp>,
    onLoadInstalledApps: () -> Unit,
    onSetActive: (String?) -> Unit,
    onCreate: (
        name: String,
        defaultPolicy: NetworkProfile.DefaultPolicy,
        allowedPackages: Set<String>,
        domainRestrictions: Map<String, Set<String>>,
        unknownDomainPolicy: NetworkProfile.UnknownDomainPolicy,
        onResult: (Result<String>) -> Unit
    ) -> Unit,
    onImportJson: (json: String, nameOverride: String?, onResult: (Result<String>) -> Unit) -> Unit,
    onUpdate: (
        id: String,
        name: String,
        defaultPolicy: NetworkProfile.DefaultPolicy,
        allowedPackages: Set<String>,
        domainRestrictions: Map<String, Set<String>>,
        unknownDomainPolicy: NetworkProfile.UnknownDomainPolicy,
        onResult: (Result<Unit>) -> Unit
    ) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<NetworkProfile?>(null) }
    var editCandidate by remember { mutableStateOf<NetworkProfile?>(null) }

    LaunchedEffect(Unit) { onLoadInstalledApps() }

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        topBar = {
            TopAppBar(
                title = { Text("Kısıtlama Profilleri", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = { filePicker.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                        Icon(Icons.Filled.FileUpload, contentDescription = "JSON içe aktar", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Profil oluştur")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ProfilesDisclaimerCard() }

            item { SectionLabel("Şu an") }
            item {
                ActiveProfileCard(
                    activeProfile = activeProfile,
                    onDeactivate = { onSetActive(null) }
                )
            }

            item { SectionLabel("Profillerin") }
            if (profiles.isEmpty()) {
                item {
                    Text(
                        "Henüz bir profil oluşturmadın. Sağ alttaki + ile yeni bir profil oluşturabilir " +
                            "ya da yukarıdan kendi hazırladığın bir JSON dosyasını içe aktarabilirsin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                }
            } else {
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        isActive = profile.id == activeProfile?.id,
                        installedApps = installedApps,
                        onActivate = { onSetActive(profile.id) },
                        onDeactivate = { onSetActive(null) },
                        onDelete = { deleteCandidate = profile },
                        onEdit = { editCandidate = profile }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        ProfileFormDialog(
            title = "Yeni profil",
            confirmLabel = "Oluştur",
            initialProfile = null,
            installedApps = installedApps,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, policy, allowedPackages, domainRestrictions, unknownPolicy ->
                onCreate(name, policy, allowedPackages, domainRestrictions, unknownPolicy) { result ->
                    scope.launch {
                        result.onSuccess {
                            snackbarHostState.showSnackbar("\"$name\" profili oluşturuldu.")
                        }.onFailure { e ->
                            snackbarHostState.showSnackbar(e.message ?: "Profil oluşturulamadı.")
                        }
                    }
                }
                showCreateDialog = false
            }
        )
    }

    editCandidate?.let { profile ->
        ProfileFormDialog(
            title = "Profili düzenle",
            confirmLabel = "Kaydet",
            initialProfile = profile,
            installedApps = installedApps,
            onDismiss = { editCandidate = null },
            onConfirm = { name, policy, allowedPackages, domainRestrictions, unknownPolicy ->
                onUpdate(profile.id, name, policy, allowedPackages, domainRestrictions, unknownPolicy) { result ->
                    scope.launch {
                        result.onSuccess {
                            snackbarHostState.showSnackbar("\"$name\" profili güncellendi.")
                        }.onFailure { e ->
                            snackbarHostState.showSnackbar(e.message ?: "Profil güncellenemedi.")
                        }
                    }
                }
                editCandidate = null
            }
        )
    }

    pendingImportJson?.let { json ->
        ImportProfileNameDialog(
            onDismiss = { pendingImportJson = null },
            onConfirm = { name ->
                onImportJson(json, name.ifBlank { null }) { result ->
                    scope.launch {
                        result.onSuccess {
                            snackbarHostState.showSnackbar("Profil içe aktarıldı.")
                        }.onFailure { e ->
                            snackbarHostState.showSnackbar(e.message ?: "İçe aktarma başarısız oldu.")
                        }
                    }
                }
                pendingImportJson = null
            }
        )
    }

    deleteCandidate?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Profili sil") },
            text = { Text("\"${profile.name}\" profili silinecek. Şu an aktifse kısıtlama da kaldırılır.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(profile.id)
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
private fun ProfilesDisclaimerCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AccentAmber.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Bir profil aktifken, DENY politikasında izin listesinde olmayan HER ŞEY " +
                    "(uygulama ya da çözülemeyen domain) varsayılan olarak engellenir. Tarayıcıda " +
                    "\"Güvenli DNS\" (DoH) açıksa domain kısıtlamalı uygulamalarda her şey engellenebilir - " +
                    "bkz. profil detayları.",
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
private fun ActiveProfileCard(activeProfile: NetworkProfile?, onDeactivate: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (activeProfile != null) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = null,
                tint = if (activeProfile != null) AccentCoral else AccentMint
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    activeProfile?.name ?: "Kısıtlama yok",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (activeProfile != null) "Bu profil şu an aktif - kısıtlama uygulanıyor."
                    else "Normal mod - tüm uygulamalar serbest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            if (activeProfile != null) {
                TextButton(onClick = onDeactivate) { Text("Kapat") }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: NetworkProfile,
    isActive: Boolean,
    installedApps: List<ResolvedApp>,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    var expanded by remember(profile.id) { mutableStateOf(false) }
    val labelsByPackage = remember(installedApps) { installedApps.associateBy({ it.packageName }, { it.label }) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) AccentViolet.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = AccentViolet)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (profile.defaultPolicy == NetworkProfile.DefaultPolicy.DENY)
                            "${profile.allowedPackages.size} uygulamaya izin veriliyor, gerisi engelli"
                        else "Her şeye izin veriliyor, sadece istisnalar engelli",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Düzenle", tint = TextTertiary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Sil", tint = TextTertiary)
                }
            }

            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Text(if (expanded) "Detayları gizle" else "Detayları göster", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (expanded) {
                ProfileDetailBody(profile = profile, labelsByPackage = labelsByPackage)
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (isActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AccentMint, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Aktif", style = MaterialTheme.typography.bodySmall, color = AccentMint)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDeactivate) { Text("Kapat") }
                }
            } else {
                TextButton(onClick = onActivate, modifier = Modifier.fillMaxWidth()) { Text("Bu profili aktif et") }
            }
        }
    }
}

/**
 * Profil kartı genişletildiğinde gösterilen ayrıntılar: hangi uygulamalara izin
 * verildiği (yüklü uygulama listesinden etiketi bulunabilirse etiketiyle, yoksa çıplak
 * paket adıyla), varsa uygulama başına domain kısıtlaması, ve bilinmeyen domain
 * politikası. Salt-okunur - düzenlemek için kalem (Edit) ikonu kullanılır.
 */
@Composable
private fun ProfileDetailBody(profile: NetworkProfile, labelsByPackage: Map<String, String>) {
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Text(
            "Varsayılan politika: " + if (profile.defaultPolicy == NetworkProfile.DefaultPolicy.DENY) "Kısıtla (DENY)" else "Serbest bırak (ALLOW)",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        if (profile.defaultPolicy == NetworkProfile.DefaultPolicy.DENY) {
            Text(
                "Bilinmeyen domain (DoH vb.): " +
                    if (profile.unknownDomainPolicy == NetworkProfile.UnknownDomainPolicy.BLOCK) "Engelle" else "İzin ver",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (profile.allowedPackages.isEmpty()) {
            Text(
                "İzinli uygulama listesi boş.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        } else {
            Text("İzinli uygulamalar", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            profile.allowedPackages.sorted().forEach { pkg ->
                Column(modifier = Modifier.padding(bottom = 6.dp)) {
                    Text(
                        labelsByPackage[pkg] ?: pkg,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val domains = profile.domainRestrictions[pkg]
                    if (domains.isNullOrEmpty()) {
                        Text(
                            "Tüm domain'lere izinli",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    } else {
                        Text(
                            "Sadece: " + domains.sorted().joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileFormDialog(
    title: String,
    confirmLabel: String,
    initialProfile: NetworkProfile?,
    installedApps: List<ResolvedApp>,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        defaultPolicy: NetworkProfile.DefaultPolicy,
        allowedPackages: Set<String>,
        domainRestrictions: Map<String, Set<String>>,
        unknownDomainPolicy: NetworkProfile.UnknownDomainPolicy
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialProfile?.name ?: "") }
    var defaultPolicy by remember { mutableStateOf(initialProfile?.defaultPolicy ?: NetworkProfile.DefaultPolicy.DENY) }
    var unknownDomainPolicy by remember {
        mutableStateOf(initialProfile?.unknownDomainPolicy ?: NetworkProfile.UnknownDomainPolicy.BLOCK)
    }
    var query by remember { mutableStateOf("") }
    // Küçük bir cila: 100+ paketlik uzun listede sadece internete çıkabilen (INTERNET
    // izni olan) uygulamaları göstermek, arama kadar önemli bir gürültü azaltma - varsayılan
    // açık, çünkü ağ izni olmayan bir uygulamayı bir ağ profiline eklemenin zaten bir anlamı yok.
    var onlyWithInternetPermission by remember { mutableStateOf(true) }
    val selectedPackages = remember { mutableStateOf(initialProfile?.allowedPackages ?: setOf()) }
    val domainDrafts = remember {
        mutableStateOf(
            initialProfile?.domainRestrictions?.mapValues { (_, domains) -> domains.joinToString(", ") } ?: emptyMap()
        )
    }

    val filteredApps = remember(installedApps, query, onlyWithInternetPermission, selectedPackages.value) {
        installedApps
            .filter { app ->
                // Zaten seçili bir uygulama, filtre yüzünden aniden listeden kaybolup
                // seçimi "görünmez" hale getirmesin - filtre sadece henüz seçilmemişlere uygulanır.
                app.packageName in selectedPackages.value || !onlyWithInternetPermission || app.hasInternetPermission
            }
            .filter {
                query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
            }
    }

    val isDeny = defaultPolicy == NetworkProfile.DefaultPolicy.DENY
    val canConfirm = name.isNotBlank() && (!isDeny || selectedPackages.value.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profil adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("Varsayılan politika", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = defaultPolicy == NetworkProfile.DefaultPolicy.DENY,
                        onClick = { defaultPolicy = NetworkProfile.DefaultPolicy.DENY },
                        label = { Text("Kısıtla (DENY)") }
                    )
                    FilterChip(
                        selected = defaultPolicy == NetworkProfile.DefaultPolicy.ALLOW,
                        onClick = { defaultPolicy = NetworkProfile.DefaultPolicy.ALLOW },
                        label = { Text("Serbest bırak (ALLOW)") }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (isDeny) "Sadece aşağıda işaretlediğin uygulamalar ağa erişebilir."
                    else "Her şeye izin verilir - bu, kısıtlama için değil istisna listesi için kullanışlıdır.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )

                if (isDeny) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Bilinmeyen domain (DoH vb.)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = unknownDomainPolicy == NetworkProfile.UnknownDomainPolicy.BLOCK,
                            onClick = { unknownDomainPolicy = NetworkProfile.UnknownDomainPolicy.BLOCK },
                            label = { Text("Engelle (önerilen)") }
                        )
                        FilterChip(
                            selected = unknownDomainPolicy == NetworkProfile.UnknownDomainPolicy.ALLOW,
                            onClick = { unknownDomainPolicy = NetworkProfile.UnknownDomainPolicy.ALLOW },
                            label = { Text("İzin ver") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "İzinli uygulamalar (${selectedPackages.value.size} seçili)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Uygulama ara") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FilterChip(
                        selected = onlyWithInternetPermission,
                        onClick = { onlyWithInternetPermission = !onlyWithInternetPermission },
                        leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Sadece ağ izni olanlar") }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (installedApps.isEmpty()) {
                        Text(
                            "Uygulama listesi yükleniyor...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                val checked = app.packageName in selectedPackages.value
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { isChecked ->
                                                selectedPackages.value = if (isChecked) {
                                                    selectedPackages.value + app.packageName
                                                } else {
                                                    domainDrafts.value = domainDrafts.value - app.packageName
                                                    selectedPackages.value - app.packageName
                                                }
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                app.packageName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextTertiary,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                    if (checked) {
                                        OutlinedTextField(
                                            value = domainDrafts.value[app.packageName] ?: "",
                                            onValueChange = { text ->
                                                domainDrafts.value = domainDrafts.value + (app.packageName to text)
                                            },
                                            label = { Text("Sadece bu domain'lere izin ver (opsiyonel)") },
                                            placeholder = { Text("örn. example.com, bank.com") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 40.dp, bottom = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    val domainRestrictions = domainDrafts.value
                        .filterKeys { it in selectedPackages.value }
                        .mapValues { (_, raw) ->
                            raw.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
                        }
                        .filterValues { it.isNotEmpty() }
                    onConfirm(name.trim(), defaultPolicy, selectedPackages.value, domainRestrictions, unknownDomainPolicy)
                }
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        }
    )
}

@Composable
private fun ImportProfileNameDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profile isim ver") },
        text = {
            Column {
                Text(
                    "Boş bırakırsan JSON içindeki \"name\" alanı kullanılır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("İsim (opsiyonel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text("İçe aktar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        }
    )
}
