package com.cruciblelab.trafficlogger.vpn

import android.net.ConnectivityManager
import android.os.Build
import android.system.OsConstants
import com.cruciblelab.trafficlogger.data.TrafficRepository
import com.cruciblelab.trafficlogger.util.AppInfoResolver
import kotlinx.coroutines.CoroutineScope
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Shared dependencies for [UdpNat] and [TcpNat] so the NAT/relay logic doesn't need to
 * know about the VpnService, Android system services, or the database directly.
 */
class RelayContext(
    private val vpnService: TrafficVpnService,
    val repository: TrafficRepository,
    val appInfoResolver: AppInfoResolver,
    val dnsCache: DnsCache,
    val scope: CoroutineScope,
    val ruleMatcher: RuleMatcher,
    private val output: FileOutputStream
) {
    private val outputLock = Any()

    fun isBlocked(appPackageName: String, domain: String?, destIp: String): Boolean =
        ruleMatcher.isBlocked(appPackageName, domain, destIp)

    // (app, hedef, port) başına en son ne zaman bir "engellendi" satırı DB'ye yazıldığını
    // tutar. Engellenen bir uygulama (örn. TikTok gibi arka planda ısrarla yeniden bağlanan
    // bir uygulama) saniyede onlarca kez deneyebilir - özellikle UDP/QUIC'te her deneme yeni
    // bir efemeral porttan geldiği için oturum anahtarı hep farklı olur ve ESKİDEN her tekil
    // deneme kendi TrafficEntry satırını açıyordu (kısa sürede on binlerce satır → hem veritabanı
    // şişiyor hem İstatistikler ekranındaki sayılar anlamsız kocaman rakamlara çıkıyordu).
    // Şimdi aynı (app, hedef, port) için [BLOCKED_LOG_THROTTLE_MS] içinde en fazla BİR satır
    // yazılıyor - engelleme davranışının kendisi (sendRst / bağlantı reddi) bundan etkilenmiyor,
    // sadece "her denemeyi ayrı ayrı DB'ye logla" davranışı "periyodik olarak bir kez logla"
    // haline geliyor.
    private val lastBlockedLogAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun shouldLogBlockedAttempt(appPackageName: String, domain: String?, destIp: String, destPort: Int): Boolean {
        val key = "$appPackageName|${domain ?: destIp}|$destPort"
        val now = System.currentTimeMillis()
        var allowed = false
        lastBlockedLogAt.compute(key) { _, last ->
            if (last == null || now - last >= BLOCKED_LOG_THROTTLE_MS) {
                allowed = true
                now
            } else {
                last
            }
        }
        return allowed
    }

    // --- İzin verilen bağlantılar için "coalesce" (satır birleştirme) ---
    //
    // shouldLogBlockedAttempt yalnızca ENGELLENEN bağlantıları throttle ediyordu; İZİN
    // VERİLEN bağlantılar için hiçbir sınırlama yoktu. TikTok gibi bir CDN üzerinden video
    // akışı yapan uygulamalar aynı domain'e (aynı ASN altında, farklı edge IP'lerinde)
    // saniyeler içinde onlarca/yüzlerce kısa TCP/UDP bağlantısı açabiliyor - her biri kendi
    // TrafficEntry satırını oluşturduğu için kısa sürede on binlerce satır birikiyordu.
    //
    // Çözüm: bir (uygulama, domain) çifti için bir bağlantı kapandığında satırı bir süreliğine
    // "claim edilebilir" olarak işaretle (releaseCoalesceTarget). Aynı çift [CONNECT_COALESCE_
    // WINDOW_MS] içinde yeniden bağlanırsa (claimCoalesceTarget), yeni satır açmak yerine o
    // satırı devralır ve üstüne ekler (bkz. TcpNat/UdpNat - entry.connectionCount artar).
    //
    // Yarış koşulu olmaz: claim edilen anahtar map'ten SİLİNİR, yalnızca sahibi kapanışta
    // release ettiğinde geri konur - yani bir satırı aynı anda en fazla TEK bir canlı oturum
    // yazabilir; ikinci bir eşzamanlı bağlantı (örn. gerçekten paralel iki video parçası)
    // claim bulamaz ve normal şekilde kendi yeni satırını açar.
    private data class CoalesceSlot(val entryId: Long, val closedAt: Long)
    private val coalesceSlots = java.util.concurrent.ConcurrentHashMap<String, CoalesceSlot>()

    fun coalesceKeyFor(appPackageName: String, domain: String, protocol: Int): String =
        "$appPackageName|$domain|$protocol"

    /** Kısa süre önce kapanmış, yeniden kullanılabilir bir satır varsa onun id'sini döndürür. */
    fun claimCoalesceTarget(key: String): Long? {
        val slot = coalesceSlots.remove(key) ?: return null
        val now = System.currentTimeMillis()
        return if (now - slot.closedAt < CONNECT_COALESCE_WINDOW_MS) slot.entryId else null
    }

    /** Bir oturum kapanırken, satırını kısa bir süreliğine yeniden kullanıma açar. */
    fun releaseCoalesceTarget(key: String, entryId: Long) {
        coalesceSlots[key] = CoalesceSlot(entryId, System.currentTimeMillis())
    }

    fun protectDatagram(socket: DatagramSocket): Boolean = vpnService.protect(socket)

    fun protectStream(socket: Socket): Boolean = vpnService.protect(socket)

    fun writeToTun(packet: ByteArray) {
        synchronized(outputLock) {
            try {
                output.write(packet)
            } catch (e: Exception) {
                // TUN closed mid-flight (VPN stopped); nothing to recover.
            }
        }
    }

    /**
     * Looks up which app UID owns a given connection's local 4-tuple via
     * [ConnectivityManager.getConnectionOwnerUid]. This call only succeeds if the
     * kernel's conntrack table already has an entry for the local socket at the
     * moment we ask - right after a client SYN/first-UDP-packet is captured, that
     * entry can occasionally not be visible yet (especially under load, or on some
     * OEM kernels/ROMs that are slower to register it), which previously caused a
     * single failed attempt to permanently mislabel the connection as
     * "Bilinmeyen (UID -1)" for its whole lifetime. We now retry a few times with a
     * short backoff before giving up, which meaningfully improves the app-attribution
     * hit rate without adding noticeable latency (worst case ~12ms, on a background
     * thread, before the socket connect/relay even starts).
     */
    fun resolveOwnerUid(
        protocol: Int,
        srcAddress: Inet4Address,
        srcPort: Int,
        dstAddress: Inet4Address,
        dstPort: Int
    ): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1
        val connectivityManager = vpnService.getSystemService(ConnectivityManager::class.java)
        val osProto = if (protocol == PROTO_TCP) OsConstants.IPPROTO_TCP else OsConstants.IPPROTO_UDP
        val local = InetSocketAddress(srcAddress, srcPort)
        val remote = InetSocketAddress(dstAddress, dstPort)

        repeat(UID_LOOKUP_MAX_ATTEMPTS) { attempt ->
            val uid = try {
                connectivityManager.getConnectionOwnerUid(osProto, local, remote)
            } catch (e: Exception) {
                -1
            }
            if (uid != INVALID_UID) return uid
            if (attempt < UID_LOOKUP_MAX_ATTEMPTS - 1) {
                try {
                    Thread.sleep(UID_LOOKUP_RETRY_DELAY_MS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return INVALID_UID
                }
            }
        }
        return INVALID_UID
    }

    private companion object {
        private const val INVALID_UID = -1
        private const val UID_LOOKUP_MAX_ATTEMPTS = 4
        private const val UID_LOOKUP_RETRY_DELAY_MS = 4L
        private const val BLOCKED_LOG_THROTTLE_MS = 60_000L

        /** Aynı (uygulama, domain) çifti bu süre içinde yeniden bağlanırsa satır birleştirilir. */
        const val CONNECT_COALESCE_WINDOW_MS = 15_000L
    }
}
