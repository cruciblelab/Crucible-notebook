package com.cruciblelab.trafficlogger.vpn

import com.cruciblelab.trafficlogger.data.Direction
import com.cruciblelab.trafficlogger.data.Protocol
import com.cruciblelab.trafficlogger.data.TrafficEntry
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

private const val TCP_IDLE_TIMEOUT_MS = 4 * 60_000L
private const val TCP_CONNECT_TIMEOUT_MS = 10_000
private const val TCP_MSS = 1400
private const val TCP_WINDOW = 65535
private const val SEQ_MASK = 0xFFFFFFFFL

/**
 * A minimal user-space TCP NAT: tracks just enough state (SYN/ACK/FIN, sequence and ack
 * numbers) per client connection to open a real socket to the destination, relay bytes
 * both ways, and log how much went up/down per app - without ever inspecting payload
 * content (it's usually TLS anyway). Deliberately simple: no retransmission timers, no
 * reordering/SACK, single in-flight segment per direction assumed. That's enough for
 * normal phone traffic (HTTP/HTTPS, background sync) though a lossy link may occasionally
 * stall a connection until the client itself times out and reconnects.
 */
class TcpNat(private val context: RelayContext) {

    private val sessions = ConcurrentHashMap<String, TcpSession>()

    fun handleClientSegment(seg: ParsedTcpSegment) {
        val key = keyFor(seg.sourceAddress, seg.sourcePort, seg.destAddress, seg.destPort)

        if (seg.isRst) {
            sessions.remove(key)?.closeAbrupt()
            return
        }

        val existing = sessions[key]
        if (existing == null) {
            if (seg.isSyn && !seg.isAck) {
                val session = TcpSession(key, seg.sourceAddress, seg.sourcePort, seg.destAddress, seg.destPort, seg.seq)
                sessions[key] = session
                session.beginConnect()
            }
            return
        }
        existing.handleSegment(seg)
    }

    fun sweepIdleSessions() {
        val now = System.currentTimeMillis()
        sessions.values.filter { now - it.lastActivity > TCP_IDLE_TIMEOUT_MS }.forEach { it.closeAbrupt() }
    }

    fun closeAll() {
        sessions.values.toList().forEach { it.closeAbrupt() }
    }

    private fun keyFor(srcAddress: Inet4Address, srcPort: Int, dstAddress: Inet4Address, dstPort: Int) =
        "${srcAddress.hostAddress}:$srcPort>${dstAddress.hostAddress}:$dstPort"

    private enum class State { CONNECTING, SYN_RECEIVED, ESTABLISHED, CLOSING, CLOSED }

    private inner class TcpSession(
        private val key: String,
        private val clientAddress: Inet4Address,
        private val clientPort: Int,
        private val remoteAddress: Inet4Address,
        private val remotePort: Int,
        private val clientIsn: Long
    ) {
        @Volatile var lastActivity = System.currentTimeMillis()
        @Volatile private var state = State.CONNECTING
        @Volatile private var clientNext = (clientIsn + 1) and SEQ_MASK
        @Volatile private var ourIsn = 0L
        @Volatile private var ourSeq = 0L
        @Volatile private var bytesUp = 0L
        @Volatile private var bytesDown = 0L
        @Volatile private var clientFinSeen = false
        @Volatile private var localFinSent = false
        @Volatile private var remoteEof = false

        private var socket: Socket? = null
        private var entryId: Long = -1
        private var domain: String? = null
        private var uid = -1
        private var lastPersist = 0L

        fun beginConnect() {
            thread(name = "TcpConnect-$remotePort", start = true) {
                uid = context.resolveOwnerUid(PROTO_TCP, clientAddress, clientPort, remoteAddress, remotePort)
                domain = context.dnsCache.lookup(remoteAddress)

                val s = Socket()
                try {
                    context.protectStream(s)
                    s.connect(InetSocketAddress(remoteAddress, remotePort), TCP_CONNECT_TIMEOUT_MS)
                } catch (e: Exception) {
                    sessions.remove(key)
                    sendRst()
                    return@thread
                }
                socket = s
                ourIsn = (0L..SEQ_MASK).random()
                ourSeq = (ourIsn + 1) and SEQ_MASK
                state = State.SYN_RECEIVED
                lastActivity = System.currentTimeMillis()
                sendSynAck()

                val app = context.appInfoResolver.resolve(uid)
                context.scope.launch {
                    entryId = context.repository.insert(
                        TrafficEntry(
                            appPackageName = app.packageName,
                            appLabel = app.label,
                            domain = domain,
                            destIp = remoteAddress.hostAddress ?: "",
                            destPort = remotePort,
                            protocol = Protocol.TCP,
                            bytesUp = 0,
                            bytesDown = 0,
                            timestamp = System.currentTimeMillis(),
                            direction = Direction.OUT
                        )
                    )
                }

                startReadLoop(s)
            }
        }

        fun handleSegment(seg: ParsedTcpSegment) {
            lastActivity = System.currentTimeMillis()
            when (state) {
                State.CONNECTING -> Unit // client resent SYN while we're still dialing out; ignore
                State.SYN_RECEIVED -> {
                    if (seg.isAck) {
                        state = State.ESTABLISHED
                        if (seg.payload.isNotEmpty()) handleData(seg)
                        if (seg.isFin) handleFin()
                    }
                }
                State.ESTABLISHED, State.CLOSING -> {
                    if (seg.payload.isNotEmpty()) handleData(seg)
                    if (seg.isFin) handleFin()
                }
                State.CLOSED -> Unit
            }
        }

        private fun handleData(seg: ParsedTcpSegment) {
            if (seg.seq != clientNext) {
                // Out-of-order or a retransmit of already-acked bytes: re-ack, don't rewrite.
                sendAckOnly()
                return
            }
            val s = socket ?: return
            try {
                s.getOutputStream().write(seg.payload)
                s.getOutputStream().flush()
            } catch (e: Exception) {
                closeAbrupt()
                return
            }
            clientNext = (clientNext + seg.payload.size) and SEQ_MASK
            bytesUp += seg.payload.size
            sendAckOnly()
            persistThrottled()
        }

        private fun handleFin() {
            if (clientFinSeen) return
            clientFinSeen = true
            clientNext = (clientNext + 1) and SEQ_MASK
            sendAckOnly()
            try {
                socket?.shutdownOutput()
            } catch (e: Exception) {
                // socket may already be gone
            }
            state = State.CLOSING
            maybeFinishClose()
        }

        private fun startReadLoop(s: Socket) {
            thread(name = "TcpRead-$remotePort", start = true) {
                val buffer = ByteArray(4096)
                try {
                    val input = s.getInputStream()
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        var offset = 0
                        while (offset < n) {
                            val chunkSize = minOf(TCP_MSS, n - offset)
                            sendData(buffer.copyOfRange(offset, offset + chunkSize))
                            offset += chunkSize
                        }
                    }
                } catch (e: Exception) {
                    // socket closed/reset - fall through to half-close below
                }
                remoteEof = true
                sendFin()
                maybeFinishClose()
            }
        }

        @Synchronized
        private fun sendSynAck() {
            context.writeToTun(
                PacketUtils.buildIpv4TcpPacket(
                    srcAddress = remoteAddress, srcPort = remotePort,
                    dstAddress = clientAddress, dstPort = clientPort,
                    seq = ourIsn, ack = clientNext, flags = TCP_SYN or TCP_ACK,
                    window = TCP_WINDOW, mss = TCP_MSS
                )
            )
        }

        @Synchronized
        private fun sendAckOnly() {
            context.writeToTun(
                PacketUtils.buildIpv4TcpPacket(
                    srcAddress = remoteAddress, srcPort = remotePort,
                    dstAddress = clientAddress, dstPort = clientPort,
                    seq = ourSeq, ack = clientNext, flags = TCP_ACK, window = TCP_WINDOW
                )
            )
        }

        @Synchronized
        private fun sendData(chunk: ByteArray) {
            context.writeToTun(
                PacketUtils.buildIpv4TcpPacket(
                    srcAddress = remoteAddress, srcPort = remotePort,
                    dstAddress = clientAddress, dstPort = clientPort,
                    seq = ourSeq, ack = clientNext, flags = TCP_PSH or TCP_ACK,
                    window = TCP_WINDOW, payload = chunk
                )
            )
            ourSeq = (ourSeq + chunk.size) and SEQ_MASK
            bytesDown += chunk.size
            persistThrottled()
        }

        @Synchronized
        private fun sendFin() {
            if (localFinSent) return
            localFinSent = true
            context.writeToTun(
                PacketUtils.buildIpv4TcpPacket(
                    srcAddress = remoteAddress, srcPort = remotePort,
                    dstAddress = clientAddress, dstPort = clientPort,
                    seq = ourSeq, ack = clientNext, flags = TCP_FIN or TCP_ACK, window = TCP_WINDOW
                )
            )
            ourSeq = (ourSeq + 1) and SEQ_MASK
        }

        private fun sendRst() {
            context.writeToTun(
                PacketUtils.buildIpv4TcpPacket(
                    srcAddress = remoteAddress, srcPort = remotePort,
                    dstAddress = clientAddress, dstPort = clientPort,
                    seq = 0L, ack = (clientIsn + 1) and SEQ_MASK, flags = TCP_RST or TCP_ACK, window = 0
                )
            )
        }

        private fun maybeFinishClose() {
            if (clientFinSeen && remoteEof) close()
        }

        fun closeAbrupt() {
            if (state != State.CLOSED) {
                try {
                    sendRst()
                } catch (e: Exception) {
                    // best-effort only
                }
            }
            close()
        }

        private fun persistThrottled() {
            val now = System.currentTimeMillis()
            if (now - lastPersist < 2000) return
            lastPersist = now
            persist()
        }

        private fun persist() {
            if (entryId <= 0) return
            val app = context.appInfoResolver.resolve(uid)
            val finalBytesUp = bytesUp
            val finalBytesDown = bytesDown
            context.scope.launch {
                context.repository.update(
                    TrafficEntry(
                        id = entryId,
                        appPackageName = app.packageName,
                        appLabel = app.label,
                        domain = domain,
                        destIp = remoteAddress.hostAddress ?: "",
                        destPort = remotePort,
                        protocol = Protocol.TCP,
                        bytesUp = finalBytesUp,
                        bytesDown = finalBytesDown,
                        timestamp = System.currentTimeMillis(),
                        direction = Direction.OUT
                    )
                )
            }
        }

        fun close() {
            if (state == State.CLOSED) return
            state = State.CLOSED
            persist()
            try {
                socket?.close()
            } catch (e: Exception) {
                // already gone
            }
            sessions.remove(key)
        }
    }
}
