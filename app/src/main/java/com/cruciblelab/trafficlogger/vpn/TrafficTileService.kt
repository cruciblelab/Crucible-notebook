package com.cruciblelab.trafficlogger.vpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.cruciblelab.trafficlogger.MainActivity

/** Bildirim panelindeki "Canlı Ağ Trafiği" kutucuğu: tek dokunuşla izlemeyi açar/kapatır. */
class TrafficTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render(TrafficVpnService.isRunning.value)
    }

    override fun onClick() {
        super.onClick()
        if (TrafficVpnService.isRunning.value) {
            startService(Intent(this, TrafficVpnService::class.java).setAction(TrafficVpnService.ACTION_STOP))
            render(running = false)
            return
        }
        // VPN izni daha önce verildiyse doğrudan başlat; izin gerekiyorsa ya da sistem arka
        // plandan başlatmaya izin vermezse, izin/başlatma akışı için uygulamayı aç.
        val started = VpnService.prepare(this) == null &&
            runCatching { startForegroundService(Intent(this, TrafficVpnService::class.java)) }.isSuccess
        if (started) {
            render(running = true)
        } else {
            openAppAndStart()
        }
    }

    private fun openAppAndStart() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_START_VPN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(running: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (running) "Açık" else "Kapalı"
        }
        tile.updateTile()
    }
}
