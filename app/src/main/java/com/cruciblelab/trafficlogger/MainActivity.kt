package com.cruciblelab.trafficlogger

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.cruciblelab.trafficlogger.ui.TrafficNavGraph
import com.cruciblelab.trafficlogger.ui.theme.NetworkTrafficLoggerTheme
import com.cruciblelab.trafficlogger.vpn.TrafficVpnService

class MainActivity : ComponentActivity() {

    companion object {
        /** Hızlı Ayarlar kutucuğu, VPN izni gerektiğinde uygulamayı bu action ile açar. */
        const val ACTION_START_VPN = "com.cruciblelab.trafficlogger.action.START_VPN"
    }

    private val viewModel: MainViewModel by viewModels()

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { requestVpnPermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NetworkTrafficLoggerTheme {
                TrafficNavGraph(
                    viewModel = viewModel,
                    onToggleVpn = ::onToggleVpn,
                    onForceResetNetwork = ::forceResetNetwork,
                    onOpenSystemVpnSettings = ::openSystemVpnSettings,
                    onExportBackup = ::exportBackup,
                    onImportBackup = ::importBackup
                )
            }
        }
        if (savedInstanceState == null) handleStartVpnIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStartVpnIntent(intent)
    }

    private fun handleStartVpnIntent(intent: Intent?) {
        if (intent?.action == ACTION_START_VPN && !TrafficVpnService.isRunning.value) {
            ensureNotificationPermissionThenPrepareVpn()
        }
    }

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportBackup(it, ::showMessage) } }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importBackup(it, ::showMessage) } }

    private fun exportBackup() {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        exportBackupLauncher.launch("canli-ag-trafigi-yedek-$date.json")
    }

    private fun importBackup() {
        // Bazı dosya yöneticileri .json'u "application/octet-stream" olarak bildirir; hepsini göster.
        importBackupLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun onToggleVpn() {
        if (TrafficVpnService.isRunning.value) {
            stopVpnService()
        } else {
            ensureNotificationPermissionThenPrepareVpn()
        }
    }

    private fun ensureNotificationPermissionThenPrepareVpn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestVpnPermission()
        }
    }

    private fun requestVpnPermission() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPrepareLauncher.launch(prepareIntent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        startForegroundService(Intent(this, TrafficVpnService::class.java))
    }

    private fun stopVpnService() {
        startService(Intent(this, TrafficVpnService::class.java).setAction(TrafficVpnService.ACTION_STOP))
    }

    /**
     * "Ayarlar > Ağı Sıfırla" için son çare kurtarma yolu. Normal [stopVpnService] servise
     * bir ACTION_STOP intent'i gönderir - ama servis process'i tıkanmış/tutarsız bir
     * durumdaysa (örn. bir istisna packet loop'unu öldürmüş ama servis nesnesi hâlâ ayakta)
     * o intent hiç işlenmeyebilir. Burada iki şey art arda deneniyor:
     *  1) Normal ACTION_STOP intent'i (servis düzgün çalışıyorsa temiz bir kapanış yapar).
     *  2) Doğrudan [stopService] - Android'in kendisi servisi zorla durdurur/onDestroy'u
     *     tetikler, servisin onStartCommand'ının hiç çalışmasına gerek kalmadan.
     * İkisi birden VPN arayüzünü kapatıp OS'un "aktif VPN" göstergesini temizlemeli. Eğer
     * bundan sonra da sistem hâlâ bir VPN'in bağlı olduğunu gösteriyorsa (nadir - genelde
     * OS'un kendi VPN durumu önbelleğinin takılı kalması), kullanıcı ayrıca sistem VPN
     * ayarlarını açıp oradan manuel olarak bağlantıyı kesebilir - bkz. [openSystemVpnSettings].
     */
    private fun forceResetNetwork() {
        stopVpnService()
        val intent = Intent(this, TrafficVpnService::class.java)
        stopService(intent)
        Toast.makeText(this, getString(R.string.network_reset_done), Toast.LENGTH_SHORT).show()
    }

    private fun openSystemVpnSettings() {
        try {
            startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.network_reset_settings_unavailable), Toast.LENGTH_SHORT).show()
        }
    }
}
