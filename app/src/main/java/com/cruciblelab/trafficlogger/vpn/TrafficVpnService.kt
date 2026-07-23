package com.cruciblelab.trafficlogger.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.cruciblelab.trafficlogger.MainActivity
import com.cruciblelab.trafficlogger.R
import com.cruciblelab.trafficlogger.TrafficLoggerApp
import com.cruciblelab.trafficlogger.util.AppInfoResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Full-traffic local VPN: every packet the device sends is routed into the TUN (both
 * IPv4 UDP and TCP - not just DNS). Nothing is exfiltrated anywhere; every connection is
 * relayed right back out over a *protected* socket to its real destination (protected
 * sockets bypass the VPN so we don't loop traffic back into ourselves), so normal
 * connectivity keeps working exactly as before. Along the way, each connection is logged:
 * which app owns it (via ConnectionOwnerUid / package-for-uid), the destination IP:port,
 * the domain if it was recently resolved via DNS, and how many bytes went up/down.
 * Payload content itself is never inspected or stored beyond the DNS question/answer
 * names needed to label a destination IP with a domain.
 */
class TrafficVpnService : VpnService() {

    companion object {
        const val ACTION_STOP = "com.cruciblelab.trafficlogger.vpn.STOP"
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "traffic_monitor"
        private const val SESSION_SWEEP_INTERVAL_MS = 15_000L

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var workerThread: Thread? = null
    @Volatile private var running = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appInfoResolver: AppInfoResolver
    private lateinit var relayContext: RelayContext
    private lateinit var udpNat: UdpNat
    private lateinit var tcpNat: TcpNat
    private val dnsCache = DnsCache()
    private val ruleMatcher = RuleMatcher()

    override fun onCreate() {
        super.onCreate()
        appInfoResolver = AppInfoResolver(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startVpn() {
        if (running) return

        startForeground(NOTIFICATION_ID, buildNotification())

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0) // route ALL IPv4 traffic into the tunnel, not just DNS
            .setMtu(1500)
            .setBlocking(true)

        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            // shouldn't happen for our own package, but don't let it block startup
        }

        val establishedInterface = builder.establish()
        if (establishedInterface == null) {
            stopSelf()
            return
        }
        vpnInterface = establishedInterface
        running = true
        _isRunning.value = true

        val repository = (application as TrafficLoggerApp).trafficRepository
        val output = FileOutputStream(establishedInterface.fileDescriptor)
        relayContext = RelayContext(
            vpnService = this,
            repository = repository,
            appInfoResolver = appInfoResolver,
            dnsCache = dnsCache,
            scope = serviceScope,
            ruleMatcher = ruleMatcher,
            output = output
        )
        udpNat = UdpNat(relayContext)
        tcpNat = TcpNat(relayContext)

        workerThread = thread(name = "TrafficVpnWorker", start = true) {
            runPacketLoop(establishedInterface, output)
        }

        serviceScope.launch { retentionLoop() }
        serviceScope.launch { sessionSweepLoop() }
        serviceScope.launch { ruleSyncLoop() }
    }

    private fun stopVpn() {
        running = false
        _isRunning.value = false
        workerThread?.interrupt()
        workerThread = null
        if (::udpNat.isInitialized) udpNat.closeAll()
        if (::tcpNat.isInitialized) tcpNat.closeAll()
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            // interface already gone, nothing to clean up
        }
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun retentionLoop() {
        val settingsRepository = (application as TrafficLoggerApp).settingsRepository
        val repository = (application as TrafficLoggerApp).trafficRepository
        while (running) {
            val days = settingsRepository.retentionDays.first()
            repository.purgeOlderThan(days)
            delay(TimeUnit.HOURS.toMillis(1))
        }
    }

    private suspend fun sessionSweepLoop() {
        while (running) {
            delay(SESSION_SWEEP_INTERVAL_MS)
            udpNat.sweepIdleSessions()
            tcpNat.sweepIdleSessions()
        }
    }

    /** Keeps the in-memory [ruleMatcher] snapshot in sync with rules added/removed in the UI. */
    private suspend fun ruleSyncLoop() {
        val ruleRepository = (application as TrafficLoggerApp).ruleRepository
        ruleRepository.observeAll().collect { rules -> ruleMatcher.update(rules) }
    }

    private fun runPacketLoop(pfd: ParcelFileDescriptor, output: FileOutputStream) {
        val input = FileInputStream(pfd.fileDescriptor)
        val buffer = ByteArray(32767)

        while (running) {
            val length = try {
                input.read(buffer)
            } catch (e: Exception) {
                if (running) continue else break
            }
            if (length <= 0) continue

            val packetCopy = buffer.copyOf(length)
            dispatchPacket(packetCopy, output)
        }
    }

    private fun dispatchPacket(packet: ByteArray, output: FileOutputStream) {
        val header = PacketUtils.parseIpv4Header(packet, packet.size) ?: return
        when (header.protocol) {
            PROTO_UDP -> {
                val udp = PacketUtils.parseUdp(packet, packet.size, header) ?: return
                udpNat.handleClientPacket(udp)
            }
            PROTO_TCP -> {
                val tcp = PacketUtils.parseTcp(packet, packet.size, header) ?: return
                tcpNat.handleClientSegment(tcp)
            }
            else -> Unit // other protocols (ICMP etc.) aren't relayed in this stage
        }
    }

    private fun buildNotification(): Notification {
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, TrafficVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .build()
    }
}
