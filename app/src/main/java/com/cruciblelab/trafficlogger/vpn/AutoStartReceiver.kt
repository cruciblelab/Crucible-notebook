package com.cruciblelab.trafficlogger.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.cruciblelab.trafficlogger.TrafficLoggerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Ayarlarda "otomatik başlat" açıksa izlemeyi telefon açıldığında ve uygulama
 * güncellendiğinde (güncelleme süreci durdurur) yeniden başlatır. Android bu iki yayın
 * için arka plandan foreground service başlatmaya izin verir. VPN izni hiç verilmemişse
 * kullanıcıya soramayacağımız için hiçbir şey yapılmaz.
 */
class AutoStartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as TrafficLoggerApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (app.settingsRepository.autoStartVpn.first() && VpnService.prepare(app) == null) {
                    app.startForegroundService(Intent(app, TrafficVpnService::class.java))
                }
            } catch (e: Exception) {
                // Sistem başlatmayı reddettiyse (ör. kısıtlanmış pil modu) sessizce vazgeç;
                // kullanıcı uygulamayı açınca normal şekilde başlatabilir.
            } finally {
                pending.finish()
            }
        }
    }
}
