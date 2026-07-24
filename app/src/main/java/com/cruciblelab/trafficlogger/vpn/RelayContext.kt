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
    }
}
