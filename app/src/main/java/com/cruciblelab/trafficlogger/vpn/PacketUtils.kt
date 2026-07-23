package com.cruciblelab.trafficlogger.vpn

import java.net.Inet4Address
import java.net.InetAddress

private const val IPV4_VERSION = 4
private const val PROTO_UDP = 17

/** A parsed IPv4/UDP datagram lifted out of a raw packet read from the TUN device. */
data class ParsedUdpPacket(
    val sourceAddress: Inet4Address,
    val sourcePort: Int,
    val destAddress: Inet4Address,
    val destPort: Int,
    val payload: ByteArray,
    val totalLength: Int
)

/**
 * Minimal, allocation-light IPv4/UDP parsing and re-encoding. Only what's needed to
 * read a UDP/53 request out of the TUN and to synthesize the matching reply packet
 * back into it - no general purpose IP stack.
 */
object PacketUtils {

    fun parseIpv4Udp(buffer: ByteArray, length: Int): ParsedUdpPacket? {
        if (length < 20) return null
        val versionAndIhl = buffer[0].toInt() and 0xFF
        val version = versionAndIhl shr 4
        if (version != IPV4_VERSION) return null
        val ihl = (versionAndIhl and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return null

        val protocol = buffer[9].toInt() and 0xFF
        if (protocol != PROTO_UDP) return null

        val srcAddress = InetAddress.getByAddress(buffer.copyOfRange(12, 16)) as Inet4Address
        val dstAddress = InetAddress.getByAddress(buffer.copyOfRange(16, 20)) as Inet4Address

        val udpOffset = ihl
        val srcPort = readUInt16(buffer, udpOffset)
        val dstPort = readUInt16(buffer, udpOffset + 2)
        val udpLength = readUInt16(buffer, udpOffset + 4)
        val payloadOffset = udpOffset + 8
        val payloadLength = (udpLength - 8).coerceAtLeast(0)
        if (payloadOffset + payloadLength > length) return null

        return ParsedUdpPacket(
            sourceAddress = srcAddress,
            sourcePort = srcPort,
            destAddress = dstAddress,
            destPort = dstPort,
            payload = buffer.copyOfRange(payloadOffset, payloadOffset + payloadLength),
            totalLength = length
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

        packet[0] = ((IPV4_VERSION shl 4) or 5).toByte() // version=4, IHL=5 (no options)
        packet[1] = 0
        writeUInt16(packet, 2, totalLength)
        writeUInt16(packet, 4, 0) // identification
        writeUInt16(packet, 6, 0) // flags/fragment offset
        packet[8] = 64 // TTL
        packet[9] = PROTO_UDP.toByte()
        writeUInt16(packet, 10, 0) // checksum placeholder
        System.arraycopy(srcAddress.address, 0, packet, 12, 4)
        System.arraycopy(dstAddress.address, 0, packet, 16, 4)
        writeUInt16(packet, 10, ipChecksum(packet, 0, 20))

        val udpOffset = 20
        writeUInt16(packet, udpOffset, srcPort)
        writeUInt16(packet, udpOffset + 2, dstPort)
        writeUInt16(packet, udpOffset + 4, udpLength)
        writeUInt16(packet, udpOffset + 6, 0) // UDP checksum optional over IPv4, leave 0 (disabled)
        System.arraycopy(payload, 0, packet, udpOffset + 8, payload.size)

        return packet
    }

    private fun readUInt16(buffer: ByteArray, offset: Int): Int {
        return ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
    }

    private fun writeUInt16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
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
}
