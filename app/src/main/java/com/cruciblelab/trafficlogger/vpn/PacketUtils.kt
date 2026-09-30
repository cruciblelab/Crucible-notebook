package com.cruciblelab.trafficlogger.vpn

import java.net.Inet4Address
import java.net.InetAddress

private const val IPV4_VERSION = 4
const val PROTO_TCP = 6
const val PROTO_UDP = 17

const val TCP_FIN = 0x01
const val TCP_SYN = 0x02
const val TCP_RST = 0x04
const val TCP_PSH = 0x08
const val TCP_ACK = 0x10

/** A parsed IPv4 header, protocol-agnostic - tells the caller where the L4 payload starts. */
data class Ipv4Header(
    val protocol: Int,
    val sourceAddress: Inet4Address,
    val destAddress: Inet4Address,
    val headerLength: Int,
    val totalLength: Int
)

/** A parsed IPv4/UDP datagram lifted out of a raw packet read from the TUN device. */
data class ParsedUdpPacket(
    val sourceAddress: Inet4Address,
    val sourcePort: Int,
    val destAddress: Inet4Address,
    val destPort: Int,
    val payload: ByteArray
)

/** A parsed IPv4/TCP segment lifted out of a raw packet read from the TUN device. */
data class ParsedTcpSegment(
    val sourceAddress: Inet4Address,
    val sourcePort: Int,
    val destAddress: Inet4Address,
    val destPort: Int,
    val seq: Long,
    val ack: Long,
    val flags: Int,
    val window: Int,
    val payload: ByteArray
) {
    val isSyn get() = flags and TCP_SYN != 0
    val isAck get() = flags and TCP_ACK != 0
    val isFin get() = flags and TCP_FIN != 0
    val isRst get() = flags and TCP_RST != 0
}

/**
 * Minimal, allocation-light IPv4/UDP/TCP parsing and re-encoding - just enough to run a
 * full NAT-style relay for both protocols (no fragmentation/options handling beyond what's
 * needed to read the header and skip past it).
 */
object PacketUtils {

    fun parseIpv4Header(buffer: ByteArray, length: Int): Ipv4Header? {
        if (length < 20) return null
        val versionAndIhl = buffer[0].toInt() and 0xFF
        val version = versionAndIhl shr 4
        if (version != IPV4_VERSION) return null
        val ihl = (versionAndIhl and 0x0F) * 4
        if (ihl < 20 || length < ihl) return null

        val protocol = buffer[9].toInt() and 0xFF
        val srcAddress = InetAddress.getByAddress(buffer.copyOfRange(12, 16)) as Inet4Address
        val dstAddress = InetAddress.getByAddress(buffer.copyOfRange(16, 20)) as Inet4Address

        return Ipv4Header(
            protocol = protocol,
            sourceAddress = srcAddress,
            destAddress = dstAddress,
            headerLength = ihl,
            totalLength = length
        )
    }

    fun parseUdp(buffer: ByteArray, length: Int, header: Ipv4Header): ParsedUdpPacket? {
        val udpOffset = header.headerLength
        if (length < udpOffset + 8) return null
        val srcPort = readUInt16(buffer, udpOffset)
        val dstPort = readUInt16(buffer, udpOffset + 2)
        val udpLength = readUInt16(buffer, udpOffset + 4)
        val payloadOffset = udpOffset + 8
        val payloadLength = (udpLength - 8).coerceAtLeast(0)
        if (payloadOffset + payloadLength > length) return null

        return ParsedUdpPacket(
            sourceAddress = header.sourceAddress,
            sourcePort = srcPort,
            destAddress = header.destAddress,
            destPort = dstPort,
            payload = buffer.copyOfRange(payloadOffset, payloadOffset + payloadLength)
        )
    }

    fun parseTcp(buffer: ByteArray, length: Int, header: Ipv4Header): ParsedTcpSegment? {
        val tcpOffset = header.headerLength
        if (length < tcpOffset + 20) return null
        val srcPort = readUInt16(buffer, tcpOffset)
        val dstPort = readUInt16(buffer, tcpOffset + 2)
        val seq = readUInt32(buffer, tcpOffset + 4)
        val ack = readUInt32(buffer, tcpOffset + 8)
        val dataOffsetAndFlagsHi = buffer[tcpOffset + 12].toInt() and 0xFF
        val dataOffset = (dataOffsetAndFlagsHi shr 4) * 4
        val flags = buffer[tcpOffset + 13].toInt() and 0xFF
        val window = readUInt16(buffer, tcpOffset + 14)
        val payloadOffset = tcpOffset + dataOffset
        if (dataOffset < 20 || payloadOffset > length) return null

        return ParsedTcpSegment(
            sourceAddress = header.sourceAddress,
            sourcePort = srcPort,
            destAddress = header.destAddress,
            destPort = dstPort,
            seq = seq,
            ack = ack,
            flags = flags,
            window = window,
            payload = buffer.copyOfRange(payloadOffset, length)
        )
    }

    /** Builds a raw IPv4/UDP packet with [srcAddress]:[srcPort] -> [dstAddress]:[dstPort]. */
    fun buildIpv4UdpPacket(
        srcAddress: Inet4Address,
        srcPort: Int,
        dstAddress: Inet4Address,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLength = 8 + payload.size
        val totalLength = 20 + udpLength
        val packet = ByteArray(totalLength)

        writeIpv4Header(packet, PROTO_UDP, srcAddress, dstAddress, totalLength)

        val udpOffset = 20
        writeUInt16(packet, udpOffset, srcPort)
        writeUInt16(packet, udpOffset + 2, dstPort)
        writeUInt16(packet, udpOffset + 4, udpLength)
        writeUInt16(packet, udpOffset + 6, 0) // UDP checksum optional over IPv4, leave 0 (disabled)
        System.arraycopy(payload, 0, packet, udpOffset + 8, payload.size)

        return packet
    }

    /**
     * Builds a raw IPv4/TCP segment. [mss] is only meaningful on SYN-ACK segments; pass
     * null for anything else so no options are emitted.
     */
    fun buildIpv4TcpPacket(
        srcAddress: Inet4Address,
        srcPort: Int,
        dstAddress: Inet4Address,
        dstPort: Int,
        seq: Long,
        ack: Long,
        flags: Int,
        window: Int,
        payload: ByteArray = ByteArray(0),
        mss: Int? = null
    ): ByteArray {
        val optionsLength = if (mss != null) 4 else 0
        val tcpHeaderLength = 20 + optionsLength
        val totalLength = 20 + tcpHeaderLength + payload.size
        val packet = ByteArray(totalLength)

        writeIpv4Header(packet, PROTO_TCP, srcAddress, dstAddress, totalLength)

        val tcpOffset = 20
        writeUInt16(packet, tcpOffset, srcPort)
        writeUInt16(packet, tcpOffset + 2, dstPort)
        writeUInt32(packet, tcpOffset + 4, seq)
        writeUInt32(packet, tcpOffset + 8, ack)
        packet[tcpOffset + 12] = (((tcpHeaderLength / 4) shl 4) and 0xF0).toByte()
        packet[tcpOffset + 13] = (flags and 0xFF).toByte()
        writeUInt16(packet, tcpOffset + 14, window)
        writeUInt16(packet, tcpOffset + 16, 0) // checksum placeholder
        writeUInt16(packet, tcpOffset + 18, 0) // urgent pointer
        if (mss != null) {
            packet[tcpOffset + 20] = 2 // kind = MSS
            packet[tcpOffset + 21] = 4 // length = 4
            writeUInt16(packet, tcpOffset + 22, mss)
        }
        System.arraycopy(payload, 0, packet, tcpOffset + tcpHeaderLength, payload.size)

        val checksum = tcpChecksum(packet, tcpOffset, tcpHeaderLength + payload.size, srcAddress, dstAddress)
        writeUInt16(packet, tcpOffset + 16, checksum)

        return packet
    }

    private fun writeIpv4Header(
        packet: ByteArray,
        protocol: Int,
        srcAddress: Inet4Address,
        dstAddress: Inet4Address,
        totalLength: Int
    ) {
        packet[0] = ((IPV4_VERSION shl 4) or 5).toByte() // version=4, IHL=5 (no options)
        packet[1] = 0
        writeUInt16(packet, 2, totalLength)
        writeUInt16(packet, 4, 0) // identification
        writeUInt16(packet, 6, 0x4000) // don't-fragment flag set, offset 0
        packet[8] = 64 // TTL
        packet[9] = protocol.toByte()
        writeUInt16(packet, 10, 0) // checksum placeholder
        System.arraycopy(srcAddress.address, 0, packet, 12, 4)
        System.arraycopy(dstAddress.address, 0, packet, 16, 4)
        writeUInt16(packet, 10, ipChecksum(packet, 0, 20))
    }

    private fun readUInt16(buffer: ByteArray, offset: Int): Int {
        return ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
    }

    private fun readUInt32(buffer: ByteArray, offset: Int): Long {
        return ((buffer[offset].toLong() and 0xFF) shl 24) or
            ((buffer[offset + 1].toLong() and 0xFF) shl 16) or
            ((buffer[offset + 2].toLong() and 0xFF) shl 8) or
            (buffer[offset + 3].toLong() and 0xFF)
    }

    private fun writeUInt16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }

    private fun writeUInt32(buffer: ByteArray, offset: Int, value: Long) {
        buffer[offset] = ((value shr 24) and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 3] = (value and 0xFF).toByte()
    }

    private fun ipChecksum(buffer: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length - 1) {
            sum += readUInt16(buffer, i)
            i += 2
        }
        if (length % 2 == 1) {
            sum += (buffer[offset + length - 1].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }

    /** TCP checksum covers a pseudo-header (src/dst/proto/length) plus the segment itself. */
    private fun tcpChecksum(
        packet: ByteArray,
        tcpOffset: Int,
        tcpLength: Int,
        srcAddress: Inet4Address,
        dstAddress: Inet4Address
    ): Int {
        var sum = 0L
        val src = srcAddress.address
        val dst = dstAddress.address
        sum += ((src[0].toInt() and 0xFF) shl 8) or (src[1].toInt() and 0xFF)
        sum += ((src[2].toInt() and 0xFF) shl 8) or (src[3].toInt() and 0xFF)
        sum += ((dst[0].toInt() and 0xFF) shl 8) or (dst[1].toInt() and 0xFF)
        sum += ((dst[2].toInt() and 0xFF) shl 8) or (dst[3].toInt() and 0xFF)
        sum += PROTO_TCP.toLong()
        sum += tcpLength.toLong()

        var i = tcpOffset
        val end = tcpOffset + tcpLength
        while (i < end - 1) {
            sum += readUInt16(packet, i)
            i += 2
        }
        if (tcpLength % 2 == 1) {
            sum += (packet[end - 1].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }
}
