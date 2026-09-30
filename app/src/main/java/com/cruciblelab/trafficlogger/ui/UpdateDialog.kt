package com.cruciblelab.trafficlogger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cruciblelab.trafficlogger.ui.theme.TextSecondary
import com.cruciblelab.trafficlogger.update.ReleaseInfo
import com.cruciblelab.trafficlogger.update.UpdateState
import com.cruciblelab.trafficlogger.util.formatBytes

/** Güncellemenin tüm etkileşimli adımları tek bir diyalogda; Ayarlar ekranı sadece durumu gösterir. */
@Composable
fun UpdateDialog(
    state: UpdateState,
    currentVersionName: String,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Yeni sürüm: v${state.release.versionName}", fontWeight = FontWeight.Bold) },
            text = { ReleaseDetails(state.release, currentVersionName) },
            confirmButton = { TextButton(onClick = onUpdate) { Text("Güncelle") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Sonra") } }
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("İndiriliyor…", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    val progress = state.progress
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text(
                            "%${(progress * 100).toInt()} · ${formatBytes(state.release.apkSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Arka planda devam et") } }
        )

        is UpdateState.NeedsInstallPermission -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Kurulum izni gerekli", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Güncelleme indirildi. Android, bu uygulamanın güncelleme yükleyebilmesi için bir " +
                        "kerelik izin istiyor: açılacak ekranda \"Bu kaynaktan izin ver\"i açın, geri " +
                        "dönün ve tekrar \"Yükle\"ye dokunun."
                )
            },
            confirmButton = { TextButton(onClick = onInstall) { Text("İzin ver / Yükle") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Sonra") } }
        )

        is UpdateState.ReadyToInstall -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Kuruluma hazır", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Sistem yükleyicisi açıldı. Açılmadıysa ya da iptal ettiyseniz tekrar deneyin. " +
                        "Kurulum sırasında trafik izleme durur. Ayarlar'da \"Otomatik başlat\" açıksa " +
                        "güncellemeden sonra kendiliğinden yeniden başlar."
                )
            },
            confirmButton = { TextButton(onClick = onInstall) { Text("Yükle") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
        )

        is UpdateState.Failed -> if (state.release != null) {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Güncelleme tamamlanamadı", fontWeight = FontWeight.Bold) },
                text = { Text(state.message) },
                confirmButton = { TextButton(onClick = onUpdate) { Text("Tekrar dene") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
            )
        }

        UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate -> Unit
    }
}

@Composable
private fun ReleaseDetails(release: ReleaseInfo, currentVersionName: String) {
    // CI'ın ürettiği notlar "## Değişiklikler" başlığı + madde listesi; başlığı atıp listeyi gösteriyoruz.
    val notes = release.notes.lines().filterNot { it.trimStart().startsWith("#") }.joinToString("\n").trim()
    Column(
        modifier = Modifier
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Kurulu sürüm: v$currentVersionName · İndirme: ${formatBytes(release.apkSizeBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        if (notes.isNotEmpty()) {
            Text(
                "Yenilikler",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            Text(notes, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Kurulum sırasında trafik izleme kısa süreliğine durur. Kayıtlarınız ve ayarlarınız korunur.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
