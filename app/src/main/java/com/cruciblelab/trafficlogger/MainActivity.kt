package com.cruciblelab.trafficlogger

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.cruciblelab.trafficlogger.ui.TrafficNavGraph
import com.cruciblelab.trafficlogger.ui.theme.NetworkTrafficLoggerTheme
import com.cruciblelab.trafficlogger.vpn.TrafficVpnService

class MainActivity : ComponentActivity() {

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
                TrafficNavGraph(viewModel = viewModel, onToggleVpn = ::onToggleVpn)
            }
        }
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
}
