package com.cruciblelab.trafficlogger.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import com.cruciblelab.trafficlogger.MainActivity
import com.cruciblelab.trafficlogger.R
import com.cruciblelab.trafficlogger.TrafficLoggerApp
import com.cruciblelab.trafficlogger.data.Direction
import com.cruciblelab.trafficlogger.data.Protocol
import com.cruciblelab.trafficlogger.data.TrafficEntry
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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ParcelFileDescriptor
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Local, non-routing VPN: it only intercepts UDP/53 traffic (by routing solely the
 * device's current DNS server addresses into the TUN), reads the DNS query, logs
 * which app/domain it belongs to, then relays the query to the real DNS server over
 * a protected socket and writes the real reply back into the TUN so name resolution
 * keeps working normally. Nothing else is captured or proxied in this first stage.
 */
class TrafficVpnService : VpnService() {

    companion object {
        const val ACTION_STOP = "com.cruciblelab.trafficlogger.vpn.STOP"
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "traffic_monitor"
        private const val DNS_FORWARD_TIMEOUT_MS = 5000
        private val FALLBACK_DNS_SERVERS = listOf("8.8.8.8", "1.1.1.1")

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var workerThread: Thread? = null
    @Volatile private var running = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appInfoResolver: AppInfoResolver

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

        val dnsServers = activeDnsServers().ifEmpty { FALLBACK_DNS_SERVERS }

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress("10.0.0.2", 32)
            .setMtu(1500)
            .setBlocking(true)
        dnsServers.forEach { server -> builder.addRoute(server, 32) }

        val establishedInterface = builder.establish()
        if (establishedInterface == null) {
            stopSelf()
            return
        }
        vpnInterface = establishedInterface
        running = true
        _isRunning.value = true

        workerThread = thread(name = "TrafficVpnWorker", start = true) { runPacketLoop(establishedInterface) }

        serviceScope.launch { retentionLoop() }
    }

    private fun stopVpn() {
        running = false
        _isRunning.value = false
        workerThread?.interrupt()
        workerThread = null
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

    private fun runPacketLoop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buffer = ByteArray(32767)

        while (running) {
            val length = try {
                input.read(buffer)
            } catch (e: Exception) {
                if (running) continue else break
            }
            if (length <= 0) continue

            val packetCopy = buffer.copyOf(length)
            serviceScope.launch { handleOutgoingPacket(packetCopy, output) }
        }
    }

    private suspend fun handleOutgoingPacket(packet: ByteArray, output: FileOutputStream) {
        val udp = PacketUtils.parseIpv4Udp(packet, packet.size) ?: return
        if (udp.destPort != 53) return
        if (!DnsMessage.isQuery(udp.payload)) return
        val domain = DnsMessage.parseQuestionName(udp.payload) ?: return

        val uid = resolveOwnerUid(udp.sourceAddress, udp.sourcePort, udp.destAddress, udp.destPort)
        val app = appInfoResolver.resolve(uid)
        val repository = (application as TrafficLoggerApp).trafficRepository

        val entry = TrafficEntry(
            appPackageName = app.packageName,
            appLabel = app.label,
            domain = domain,
            destIp = udp.destAddress.hostAddress ?: "",
            destPort = udp.destPort,
            protocol = Protocol.UDP,
            bytesUp = packet.size.toLong(),
            bytesDown = 0,
            timestamp = System.currentTimeMillis(),
            direction = Direction.OUT
        )
        val entryId = repository.insert(entry)

        val response = forwardDnsQuery(udp.destAddress, udp.destPort, udp.payload) ?: return
        repository.update(entry.copy(id = entryId, bytesDown = response.size.toLong()))

        val responsePacket = PacketUtils.buildIpv4UdpPacket(
            srcAddress = udp.destAddress,
            srcPort = udp.destPort,
            dstAddress = udp.sourceAddress,
            dstPort = udp.sourcePort,
            payload = response
        )
        synchronized(output) {
            try {
                output.write(responsePacket)
            } catch (e: Exception) {
                // TUN closed mid-flight (VPN stopped); nothing to recover.
            }
        }
    }

    private fun forwardDnsQuery(serverAddress: Inet4Address, serverPort: Int, query: ByteArray): ByteArray? {
        return try {
            DatagramSocket().use { socket ->
                protect(socket)
                socket.soTimeout = DNS_FORWARD_TIMEOUT_MS
                socket.send(DatagramPacket(query, query.size, serverAddress, serverPort))

                val responseBuffer = ByteArray(4096)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)
                responseBuffer.copyOf(responsePacket.length)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveOwnerUid(
        srcAddress: Inet4Address,
        srcPort: Int,
        dstAddress: Inet4Address,
        dstPort: Int
    ): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1
        return try {
            val connectivityManager = getSystemService(ConnectivityManager::class.java)
            connectivityManager.getConnectionOwnerUid(
                OsConstants.IPPROTO_UDP,
                InetSocketAddress(srcAddress, srcPort),
                InetSocketAddress(dstAddress, dstPort)
            )
        } catch (e: Exception) {
            -1
        }
    }

    private fun activeDnsServers(): List<String> {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val network = connectivityManager.activeNetwork ?: return emptyList()
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return emptyList()
        return linkProperties.dnsServers
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
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
