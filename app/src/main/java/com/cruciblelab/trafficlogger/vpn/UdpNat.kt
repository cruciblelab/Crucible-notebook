package com.cruciblelab.trafficlogger.vpn

import com.cruciblelab.trafficlogger.data.Direction
import com.cruciblelab.trafficlogger.data.Protocol
import com.cruciblelab.trafficlogger.data.TrafficEntry
import com.cruciblelab.trafficlogger.util.ResolvedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

private const val UDP_IDLE_TIMEOUT_MS = 60_000L
private const val UDP_RECV_TIMEOUT_MS = 30_000

/**
 * NAT table for UDP: one real (protected) [DatagramSocket] per client 4-tuple, relaying
 * bytes in both directions and logging total volume + which app/domain it belongs to.
 * DNS (port 53) additionally gets its query/response contents inspected to populate
 * [dnsCache], everything else is logged purely by size - payloads for other ports are
 * never decoded or stored.
 */
class UdpNat(private val context: RelayContext) {

    private val sessions = ConcurrentHashMap<String, UdpSession>()

    fun handleClientPacket(udp: ParsedUdpPacket) {
        val key = "${udp.sourceAddress.hostAddress}:${udp.sourcePort}>${udp.destAddress.hostAddress}:${udp.destPort}"
        val session = sessions.getOrPut(key) {
            val newSession = UdpSession(key, udp.sourceAddress, udp.sourcePort, udp.destAddress, udp.destPort)
            newSession.start()
            newSession
        }
        session.sendToRemote(udp.payload)
    }

    fun sweepIdleSessions() {
        val now = System.currentTimeMillis()
        val idle = sessions.values.filter { now - it.lastActivity > UDP_IDLE_TIMEOUT_MS }
        idle.forEach { it.close() }
    }

    fun closeAll() {
        sessions.values.toList().forEach { it.close() }
    }

    private inner class UdpSession(
        private val key: String,
        private val clientAddress: Inet4Address,
        private val clientPort: Int,
        private val remoteAddress: Inet4Address,
        private val remotePort: Int
    ) {
        @Volatile var lastActivity = System.currentTimeMillis()
        @Volatile private var closed = false
        @Volatile private var blocked = false
        private var socket: DatagramSocket? = null
        private var entryId: Long = -1
        private var bytesUp = 0L
        private var bytesDown = 0L
        private var domain: String? = null
        private var uid = -1
        private var readerThread: Thread? = null
        // Coalesce (satır birleştirme) - bkz. RelayContext / TcpNat'teki aynı mekanizma.
        private var coalesceKey: String? = null
        private var connectionCount = 1

        fun start() {
            uid = context.resolveOwnerUid(PROTO_UDP, clientAddress, clientPort, remoteAddress, remotePort)
            domain = context.dnsCache.lookup(remoteAddress)
            val app = context.appInfoResolver.resolve(uid)

            // Non-DNS destinations already have a domain if this app resolved it earlier,
            // so we can decide up front whether to even open a socket. DNS queries (port
            // 53) are checked per-query in sendToRemote once the question name is known.
            if (remotePort != 53 && context.isBlocked(app.packageName, domain, remoteAddress.hostAddress ?: "")) {
                blocked = true
                // Her deneme değil, (app, hedef, port) başına aralıklı bir satır - bkz.
                // RelayContext.shouldLogBlockedAttempt. Aksi halde her yeni efemeral UDP
                // portundan (örn. QUIC yeniden bağlanmaları) ayrı bir DB satırı açılırdı.
                if (context.shouldLogBlockedAttempt(app.packageName, domain, remoteAddress.hostAddress ?: "", remotePort)) {
                    logEntry(app)
                }
                return
            }

            val newSocket = try {
                DatagramSocket().also { context.protectDatagram(it) }
            } catch (e: Exception) {
                sessions.remove(key)
                return
            }
            socket = newSocket

            logOrCoalesceEntry(app)

            readerThread = thread(name = "UdpNat-$remotePort", start = true) { readLoop(newSocket) }
        }

        /** Yalnızca ENGELLENEN denemeler için: mevcut throttle davranışı, satır birleştirme yok. */
        private fun logEntry(app: ResolvedApp) {
            context.scope.launch {
                entryId = context.repository.insert(
                    TrafficEntry(
                        appPackageName = app.packageName,
                        appLabel = app.label,
                        domain = domain,
                        destIp = remoteAddress.hostAddress ?: "",
                        destPort = remotePort,
                        protocol = Protocol.UDP,
                        bytesUp = 0,
                        bytesDown = 0,
                        timestamp = System.currentTimeMillis(),
                        direction = Direction.OUT,
                        blocked = blocked
                    )
                )
            }
        }

        /**
         * İZİN VERİLEN bağlantılar için: aynı (app, domain) çifti kısa süre önce kapanmışsa
         * (bkz. RelayContext.CONNECT_COALESCE_WINDOW_MS) yeni satır açmak yerine onu devralır.
         * QUIC/UDP-443 üzerinden çalışan uygulamalar (TikTok video CDN'i gibi) saniyeler
         * içinde birçok efemeral porttan yeni oturum açtığı için bu, DB'de satır patlamasını
         * engeller.
         */
        private fun logOrCoalesceEntry(app: ResolvedApp) {
            val currentDomain = domain
            context.scope.launch {
                val coalesceLookupKey = currentDomain?.let { context.coalesceKeyFor(app.packageName, it, PROTO_UDP) }
                val claimedId = coalesceLookupKey?.let { context.claimCoalesceTarget(it) }
                val existing = claimedId?.let { context.repository.getByIdOnce(it) }

                if (existing != null) {
                    entryId = existing.id
                    bytesUp = existing.bytesUp
                    bytesDown = existing.bytesDown
                    connectionCount = existing.connectionCount + 1
                    coalesceKey = coalesceLookupKey
                    context.repository.update(
                        existing.copy(
                            timestamp = System.currentTimeMillis(),
                            connectionCount = connectionCount
                        )
                    )
                } else {
                    coalesceKey = coalesceLookupKey
                    entryId = context.repository.insert(
                        TrafficEntry(
                            appPackageName = app.packageName,
                            appLabel = app.label,
                            domain = domain,
                            destIp = remoteAddress.hostAddress ?: "",
                            destPort = remotePort,
                            protocol = Protocol.UDP,
                            bytesUp = 0,
                            bytesDown = 0,
                            timestamp = System.currentTimeMillis(),
                            direction = Direction.OUT,
                            blocked = false,
                            connectionCount = connectionCount
                        )
                    )
                }
            }
        }

        fun sendToRemote(payload: ByteArray) {
            lastActivity = System.currentTimeMillis()

            if (remotePort == 53 && DnsMessage.isQuery(payload)) {
                DnsMessage.parseQuestionName(payload)?.let { name ->
                    domain = name
                    val app = context.appInfoResolver.resolve(uid)
                    if (!blocked && context.isBlocked(app.packageName, domain, remoteAddress.hostAddress ?: "")) {
                        // Blacklisted domain: drop the query so it never resolves, instead
                        // of blocking the whole DNS server (that would break every other
                        // lookup this app or others make through the same resolver).
                        blocked = true
                        persist()
                        close()
                        return
                    }
                    persist()
                }
            }

            if (blocked) return
            val s = socket ?: return

            try {
                s.send(DatagramPacket(payload, payload.size, remoteAddress, remotePort))
                bytesUp += payload.size
            } catch (e: Exception) {
                close()
                return
            }
            persistThrottled()
        }

        private fun readLoop(s: DatagramSocket) {
            s.soTimeout = UDP_RECV_TIMEOUT_MS
            val buffer = ByteArray(65535)
            try {
                while (!closed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        s.receive(packet)
                    } catch (e: java.net.SocketTimeoutException) {
                        break
                    }
                    lastActivity = System.currentTimeMillis()
                    val responseBytes = packet.data.copyOfRange(0, packet.length)
                    bytesDown += responseBytes.size

                    if (remotePort == 53 || clientPort == 53) {
                        DnsMessage.parseAnswerAddresses(responseBytes).forEach { (name, ip) ->
                            context.dnsCache.put(ip, name)
                        }
                    }

                    val outPacket = PacketUtils.buildIpv4UdpPacket(
                        srcAddress = remoteAddress,
                        srcPort = remotePort,
                        dstAddress = clientAddress,
                        dstPort = clientPort,
                        payload = responseBytes
                    )
                    context.writeToTun(outPacket)
                    persistThrottled()
                }
            } catch (e: Exception) {
                // socket closed or fatal IO error, fall through to cleanup
            } finally {
                close()
            }
        }

        private var lastPersist = 0L
        private fun persistThrottled() {
            val now = System.currentTimeMillis()
            if (now - lastPersist < 2000) return
            lastPersist = now
            persist()
        }

        private fun persist() {
            if (entryId <= 0) return
            val app = context.appInfoResolver.resolve(uid)
            context.scope.launch {
                context.repository.update(
                    TrafficEntry(
                        id = entryId,
                        appPackageName = app.packageName,
                        appLabel = app.label,
                        domain = domain,
                        destIp = remoteAddress.hostAddress ?: "",
                        destPort = remotePort,
                        protocol = Protocol.UDP,
                        bytesUp = bytesUp,
                        bytesDown = bytesDown,
                        timestamp = System.currentTimeMillis(),
                        direction = Direction.OUT,
                        blocked = blocked,
                        connectionCount = connectionCount
                    )
                )
            }
        }

        fun close() {
            if (closed) return
            closed = true
            persist()
            if (!blocked && entryId > 0) {
                coalesceKey?.let { context.releaseCoalesceTarget(it, entryId) }
            }
            try {
                socket?.close()
            } catch (e: Exception) {
                // already gone
            }
            sessions.remove(key)
        }
    }
}
