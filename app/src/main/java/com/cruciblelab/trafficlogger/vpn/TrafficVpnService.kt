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
import com.cruciblelab.trafficlogger.data.AppUsage
import com.cruciblelab.trafficlogger.util.AppInfoResolver
import com.cruciblelab.trafficlogger.util.formatBytes
import com.cruciblelab.trafficlogger.util.startOfDayMillis
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
        private const val DATA_LIMIT_CHANNEL_ID = "data_limit_alerts"
        private const val DATA_LIMIT_CHECK_INTERVAL_MS = 5 * 60_000L

        /**
         * Number of parallel packet-processing workers. The TUN reader thread stays
         * single-threaded (reading the fd itself is cheap and must stay in order), but
         * the actual per-packet work - NAT lookups, socket writes for TCP/UDP relay -
         * fans out across these so one slow/laggy connection (e.g. a background app
         * with a bad link) can't hold up packets from an unrelated one (e.g. a game).
         * Each packet is routed to a worker by hashing its 4-tuple, so all packets of
         * the same connection always land on the same worker and stay strictly ordered
         * relative to each other (important for the simplified TCP state machine).
         * 3-5 is a good range for a phone CPU; raise it if the device has more cores
         * and you're running many simultaneous connections, lower it to save battery.
         */
        /**
         * Paket işleme worker havuzu artık SABİT değil, UYARLANABİLİR (adaptive): pil
         * tüketimini en aza indirmek için normalde tek thread'de (MIN_PACKET_WORKERS)
         * çalışır - telefonun tipik kullanımında (birkaç eşzamanlı bağlantı) bu, işi tek
         * bir CPU çekirdeğinde toplayarak diğer çekirdeklerin derin uykuda kalmasını sağlar.
         *
         * Sadece gerçek bir yük birikmesi (backlog) tespit edilirse - örn. aynı anda çok
         * sayıda yeni bağlantı açan bir uygulama - [scaleWorkersIfNeeded] devreye girip
         * kapasiteyi kademeli olarak MAX_PACKET_WORKERS'a kadar artırır; yük geçince tekrar
         * MIN_PACKET_WORKERS'a geri iner. Havuzdaki thread'ler baştan (idle-blocked halde)
         * oluşturulur - kullanılmayan bir ExecutorService.execute() beklemesi CPU harcamaz,
         * bu yüzden "kaç tanesi var" değil "kaçına aktif iş veriliyor" pil açısından önemli
         * olan.
         *
         * GÜVENLİK NOTU: Bir bağlantının paketleri her zaman İLK atandığı worker'da kalır
         * (bkz. [connectionWorkerIndex]) - kapasite sonradan artsa/azalsa bile o bağlantı
         * asla farklı bir thread'e geçmez. Aksi halde TcpSession/UdpSession'daki mutable
         * alanlara iki thread'in aynı anda dokunması veri yarışına yol açardı.
         */
        private const val MIN_PACKET_WORKERS = 1
        private const val MAX_PACKET_WORKERS = 4
        /** Bu kadar bekleyen (henüz tamamlanmamış) paket görevi birikirse bir worker daha açılır. */
        private const val SCALE_UP_BACKLOG_THRESHOLD = 24
        /** Bağlantı->worker eşlemesi, bu süre boyunca hiç paket görmezse haritadan temizlenir. */
        private const val CONNECTION_WORKER_TTL_MS = 4 * 60_000L

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var readerThread: Thread? = null
    @Volatile private var running = false
    private var packetWorkers: List<ExecutorService> = emptyList()
    /** Şu anda yeni bağlantılara dağıtılabilecek worker sayısı - MIN..MAX arası, canlı ayarlanır. */
    private val activeWorkerCount = AtomicInteger(MIN_PACKET_WORKERS)
    /** Worker'lara verilmiş ama henüz bitmemiş görev sayısı - basit backlog sinyali. */
    private val pendingPacketTasks = AtomicInteger(0)
    /** Her bağlantı (4-tuple) ilk paketinde bir worker'a "yapışır" - bkz. [assignedWorkerFor]. */
    private data class WorkerAssignment(val workerIndex: Int, @Volatile var lastSeenMs: Long)
    private val connectionWorkerIndex = ConcurrentHashMap<String, WorkerAssignment>()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appInfoResolver: AppInfoResolver
    private lateinit var relayContext: RelayContext
    private lateinit var udpNat: UdpNat
    private lateinit var tcpNat: TcpNat
    private val dnsCache = DnsCache()
    private val ruleMatcher = RuleMatcher()

    // Tracks which app packages have already triggered a daily-limit notification for
    // [notifiedForDay], so we alert once per app per day rather than every check cycle.
    private var notifiedForDay: Long = -1L
    private val notifiedApps = mutableSetOf<String>()

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
            // IPv6'yı da tünele alıyoruz - AMA relay etmiyoruz (bkz. dispatchPacket, IPv6
            // paketleri kasıtlı olarak düşürülüyor). Amaç IPv6'ya tam destek eklemek değil,
            // "IPv6 tünelin tamamen dışından geçip her türlü loglama/engellemeyi atlıyor"
            // sızıntısını kapatmak. Rotayı tünele alınca modern uygulamalar (Happy Eyeballs)
            // IPv6 denemesi başarısız olunca otomatik IPv4'e düşer - ki o zaten tam kapsamlı.
            .addAddress("fd00:cafe:babe::2", 128)
            .addRoute("::", 0)
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

        packetWorkers = List(MAX_PACKET_WORKERS) { index ->
            Executors.newSingleThreadExecutor { r -> Thread(r, "TrafficVpnWorker-$index") }
        }

        readerThread = thread(name = "TrafficVpnReader", start = true) {
            runPacketLoop(establishedInterface)
        }

        serviceScope.launch { retentionLoop() }
        serviceScope.launch { sessionSweepLoop() }
        serviceScope.launch { ruleSyncLoop() }
        serviceScope.launch { profileSyncLoop() }
        serviceScope.launch { dohBlockingSyncLoop() }
        serviceScope.launch { dataLimitLoop() }
    }

    private fun stopVpn() {
        running = false
        _isRunning.value = false
        readerThread?.interrupt()
        readerThread = null
        packetWorkers.forEach { it.shutdownNow() }
        packetWorkers = emptyList()
        connectionWorkerIndex.clear()
        pendingPacketTasks.set(0)
        activeWorkerCount.set(MIN_PACKET_WORKERS)
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
            scaleWorkersIfNeeded()
        }
    }

    /** Keeps the in-memory [ruleMatcher] snapshot in sync with rules added/removed in the UI. */
    private suspend fun ruleSyncLoop() {
        val ruleRepository = (application as TrafficLoggerApp).ruleRepository
        ruleRepository.observeAll().collect { rules -> ruleMatcher.update(rules) }
    }

    /**
     * Keeps [ruleMatcher] in sync with whichever [com.cruciblelab.trafficlogger.data.NetworkProfile]
     * (e.g. "Bankacılık Modu") is active. Unlike manual rule changes, a profile switch also
     * tears down every currently-open TCP/UDP session (see [TcpNat.closeAll]/[UdpNat.closeAll]):
     * a profile is meant to be a hard, immediate restriction (e.g. handing the phone to a
     * child, or locking it down before a bank transfer), so a connection opened under the
     * previous profile shouldn't keep running just because it was established before the
     * switch. Apps that need the connection will simply reconnect and get re-evaluated
     * against the new active profile on their next packet.
     */
    private suspend fun profileSyncLoop() {
        val profileRepository = (application as TrafficLoggerApp).profileRepository
        var first = true
        var lastProfileId: String? = null
        profileRepository.activeProfile.collect { profile ->
            ruleMatcher.updateProfile(profile)
            // İlk emisyon (servis başlarken) mevcut hiçbir bağlantıyı kesmemeli - henüz
            // hiçbir bağlantı bu profilden ÖNCE açılmış olamaz. Sonraki her gerçek
            // değişiklikte (profil açma/kapama/değiştirme) açık oturumları kapatıyoruz.
            if (!first && profile?.id != lastProfileId) {
                if (::tcpNat.isInitialized) tcpNat.closeAll()
                if (::udpNat.isInitialized) udpNat.closeAll()
            }
            first = false
            lastProfileId = profile?.id
        }
    }

    /** Keeps [ruleMatcher]'s "bilinen DoH sunucularını engelle" ayarını Settings ile senkron tutar. */
    private suspend fun dohBlockingSyncLoop() {
        val settingsRepository = (application as TrafficLoggerApp).settingsRepository
        settingsRepository.blockKnownDoh.collect { enabled -> ruleMatcher.updateDohBlocking(enabled) }
    }

    /**
     * Periodically checks each app's usage for the current (local) day against the
     * user-configured per-app limit, firing one notification per app per day the first
     * time it crosses the threshold. A limit of 0 (or less) means the feature is off.
     */
    private suspend fun dataLimitLoop() {
        val settingsRepository = (application as TrafficLoggerApp).settingsRepository
        val repository = (application as TrafficLoggerApp).trafficRepository
        while (running) {
            val limitMb = settingsRepository.dailyLimitMb.first()
            if (limitMb > 0) {
                val dayStart = startOfDayMillis()
                if (dayStart != notifiedForDay) {
                    notifiedForDay = dayStart
                    notifiedApps.clear()
                }
                val limitBytes = limitMb * 1024L * 1024L
                repository.usageSince(dayStart)
                    .filter { it.totalBytes >= limitBytes && notifiedApps.add(it.appPackageName) }
                    .forEach { usage -> sendDataLimitNotification(usage) }
            }
            delay(DATA_LIMIT_CHECK_INTERVAL_MS)
        }
    }

    private fun sendDataLimitNotification(usage: AppUsage) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DATA_LIMIT_CHANNEL_ID,
                getString(R.string.data_limit_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, DATA_LIMIT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(getString(R.string.data_limit_notification_title))
            .setContentText("${usage.appLabel}: ${formatBytes(usage.totalBytes)}")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(usage.appPackageName.hashCode(), notification)
    }

    private fun runPacketLoop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val buffer = ByteArray(32767)

        while (running) {
            val length = try {
                input.read(buffer)
            } catch (e: Exception) {
                if (running) continue else break
            }
            if (length <= 0) continue

            // Copy out of the shared read buffer before handing off, since the next
            // loop iteration overwrites it as soon as a worker has started using it.
            val packetCopy = buffer.copyOf(length)
            dispatchPacket(packetCopy)
        }
    }

    /**
     * Parses just the headers on the (single) reader thread - cheap - then hands the
     * actual NAT/relay work off to one of [packetWorkers]. Which worker is chosen for a
     * given connection is decided ONCE, on that connection's first packet (see
     * [assignedWorkerFor]), and never changes afterwards - this keeps every packet of the
     * same connection strictly ordered even while the pool's active size grows/shrinks in
     * the background.
     */
    private fun dispatchPacket(packet: ByteArray) {
        if (packet.isNotEmpty()) {
            val ipVersion = (packet[0].toInt() and 0xFF) shr 4
            if (ipVersion == 6) {
                // Bilerek relay ETMİYORUZ: tam bir IPv6 NAT/relay motoru burada yok. Amaç
                // IPv6'yı desteklemek değil, tünele girip görünür olmasını sağlamak - bu sayede
                // hiç işlenmeden ağa direkt sızmıyor. Uygulama tarafında bu bir "bağlantı
                // başarısız" gibi görünür, modern uygulamalar (Happy Eyeballs) otomatik IPv4'e
                // düşer - ki IPv4 burada tam kapsamlı loglanıp/engelleniyor.
                return
            }
        }
        val header = PacketUtils.parseIpv4Header(packet, packet.size) ?: return
        when (header.protocol) {
            PROTO_UDP -> {
                val udp = PacketUtils.parseUdp(packet, packet.size, header) ?: return
                val key = connectionKey(header.sourceAddress.hostAddress, udp.sourcePort, header.destAddress.hostAddress, udp.destPort)
                submitToAssignedWorker(key) {
                    try {
                        udpNat.handleClientPacket(udp)
                    } catch (e: Exception) {
                        // isolate failures to this one packet/connection
                    }
                }
            }
            PROTO_TCP -> {
                val tcp = PacketUtils.parseTcp(packet, packet.size, header) ?: return
                val key = connectionKey(header.sourceAddress.hostAddress, tcp.sourcePort, header.destAddress.hostAddress, tcp.destPort)
                submitToAssignedWorker(key) {
                    try {
                        tcpNat.handleClientSegment(tcp)
                    } catch (e: Exception) {
                        // isolate failures to this one packet/connection
                    }
                }
            }
            else -> Unit // other protocols (ICMP etc.) aren't relayed in this stage
        }
    }

    private fun connectionKey(srcAddr: String?, srcPort: Int, dstAddr: String?, dstPort: Int) =
        "$srcAddr:$srcPort>$dstAddr:$dstPort"

    /**
     * Looks up (or creates, on first packet) the sticky worker for this connection, then
     * submits the work and tracks it in [pendingPacketTasks] so [scaleWorkersIfNeeded] has
     * a real backlog signal to react to.
     */
    private fun submitToAssignedWorker(key: String, block: () -> Unit) {
        val workers = packetWorkers
        if (workers.isEmpty()) return
        val now = System.currentTimeMillis()
        val assignment = connectionWorkerIndex.computeIfAbsent(key) {
            // computeIfAbsent is atomic per key, so two packets racing to open the same
            // brand-new connection can never lock in two different workers for it.
            WorkerAssignment(nextWorkerIndex(), now)
        }
        assignment.lastSeenMs = now
        val worker = workers.getOrElse(assignment.workerIndex) { workers[0] }

        pendingPacketTasks.incrementAndGet()
        worker.execute {
            try {
                block()
            } finally {
                pendingPacketTasks.decrementAndGet()
            }
        }
    }

    private val roundRobinCounter = AtomicInteger(0)

    /** Yeni bir bağlantı için, ŞU ANDAKİ aktif kapasiteye göre bir worker seçer (round-robin). */
    private fun nextWorkerIndex(): Int {
        val active = activeWorkerCount.get().coerceIn(MIN_PACKET_WORKERS, MAX_PACKET_WORKERS)
        return Math.floorMod(roundRobinCounter.getAndIncrement(), active)
    }

    /**
     * Backlog varsa kapasiteyi kademeli artırır (bir bağlantı patlaması sırasında
     * gecikme/timeout yaşanmasın diye), backlog eriyince tek thread'e geri döner. Ayrıca
     * artık aktif olmayan bağlantıların worker eşlemesini haritadan temizler.
     */
    private fun scaleWorkersIfNeeded() {
        val backlog = pendingPacketTasks.get()
        val current = activeWorkerCount.get()
        when {
            backlog > SCALE_UP_BACKLOG_THRESHOLD && current < MAX_PACKET_WORKERS ->
                activeWorkerCount.compareAndSet(current, current + 1)
            backlog == 0 && current > MIN_PACKET_WORKERS ->
                activeWorkerCount.compareAndSet(current, current - 1)
        }

        val cutoff = System.currentTimeMillis() - CONNECTION_WORKER_TTL_MS
        connectionWorkerIndex.entries.removeIf { it.value.lastSeenMs < cutoff }
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
