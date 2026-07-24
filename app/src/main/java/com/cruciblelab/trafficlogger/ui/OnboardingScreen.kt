package com.cruciblelab.trafficlogger.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cruciblelab.trafficlogger.R
import com.cruciblelab.trafficlogger.ui.theme.AccentAmber
import com.cruciblelab.trafficlogger.ui.theme.AccentMint
import com.cruciblelab.trafficlogger.ui.theme.AccentViolet
import com.cruciblelab.trafficlogger.ui.theme.TextSecondary

/**
 * Tek problem odaklı kurulum akışı: "Neden bu izni istiyoruz" -> izni ver -> (varsa) Xiaomi/MIUI
 * için ek bir uyarı -> bitir. Her adım tek bir işe odaklanır, uzun bir metin duvarı yerine.
 */
@Composable
fun OnboardingScreen(
    onRequestVpnPermission: () -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val isXiaomiLike = remember { isXiaomiLikeDevice() }

    // Xiaomi/Redmi/POCO cihazlarda ek bir adım gösteriyoruz; diğerlerinde 2 adımlık akış yeterli.
    val stepCount = if (isXiaomiLike) 3 else 2
    var step by rememberSaveable { mutableStateOf(0) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StepDots(total = stepCount, current = step)
            Spacer(modifier = Modifier.height(24.dp))

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when (step) {
                    0 -> WelcomeStep()
                    1 -> VpnPermissionStep()
                    else -> XiaomiStep()
                }
            }

            when (step) {
                0 -> Button(
                    onClick = { step = 1 },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
                ) { Text("Devam et") }

                1 -> Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            onRequestVpnPermission()
                            if (isXiaomiLike) step = 2 else onFinish()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
                    ) { Text("İzni ver") }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!isXiaomiLike) {
                        TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                            Text("Daha sonra ayarlardan da yapabilirsin, atla")
                        }
                    }
                }

                else -> Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { openXiaomiAutostartSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Otomatik başlatma ayarlarını aç")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onFinish,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
                    ) { Text("Anladım, başla") }
                }
            }
        }
    }
}

@Composable
private fun StepDots(total: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (index == current) AccentViolet else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(AccentViolet.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Visibility, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(34.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Cihazının ne konuştuğunu gör",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Hangi uygulama, ne zaman, nereye ne kadar veri gönderiyor - hepsini tek bir yerden, " +
                "cihazından hiçbir şey dışarı çıkmadan görebilirsin.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun VpnPermissionStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(AccentMint.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = AccentMint, modifier = Modifier.size(34.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Tek bir izin gerekiyor",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Text(
                stringResource(R.string.vpn_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Az sonra Android'in kendi izin ekranı açılacak.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun XiaomiStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(AccentAmber.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.BatteryAlert, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(34.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Xiaomi/MIUI cihazlar için bir not",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "MIUI, arka planda çalışan izleme servisini pil tasarrufu için durdurabilir. " +
                "Kesintisiz çalışması için \"Otomatik başlatma\" iznini bu uygulamaya açık bırakman yeterli.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AccentMint, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Ayarlar > Uygulamalar > İzinler > Otomatik başlatma", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
    }
}

private fun isXiaomiLikeDevice(): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val brand = Build.BRAND.lowercase()
    return listOf("xiaomi", "redmi", "poco").any { manufacturer.contains(it) || brand.contains(it) }
}

private fun openXiaomiAutostartSettings(context: android.content.Context) {
    try {
        val intent = Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Cihazda bu ekran yoksa (MIUI sürümü farklıysa), uygulamanın kendi ayar sayfasına düş.
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(fallback)
    }
}
