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

    fun resolveOwnerUid(
        protocol: Int,
        srcAddress: Inet4Address,
        srcPort: Int,
        dstAddress: Inet4Address,
        dstPort: Int
    ): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1
        return try {
            val connectivityManager = vpnService.getSystemService(ConnectivityManager::class.java)
            val osProto = if (protocol == PROTO_TCP) OsConstants.IPPROTO_TCP else OsConstants.IPPROTO_UDP
            connectivityManager.getConnectionOwnerUid(
                osProto,
                InetSocketAddress(srcAddress, srcPort),
                InetSocketAddress(dstAddress, dstPort)
            )
        } catch (e: Exception) {
            -1
        }
    }
}
