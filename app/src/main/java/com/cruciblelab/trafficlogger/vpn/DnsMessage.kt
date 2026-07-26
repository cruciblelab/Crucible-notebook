package com.cruciblelab.trafficlogger.vpn

import java.net.Inet4Address
import java.net.InetAddress

/**
 * Reads DNS messages (RFC 1035) - question name for queries, and A-record answers
 * (following compression pointers) so we can cache which domain a resolved IP belongs
 * to, and later tag TCP/UDP connections to that IP with the right domain.
 */
object DnsMessage {

    private const val TYPE_A = 1
    private const val CLASS_IN = 1

    fun isQuery(payload: ByteArray): Boolean {
        if (payload.size < 3) return false
        val flags = payload[2].toInt() and 0xFF
        val qr = (flags shr 7) and 0x01
        return qr == 0
    }

    fun transactionId(payload: ByteArray): Int? {
        if (payload.size < 2) return null
        return readUInt16(payload, 0)
    }

    fun parseQuestionName(payload: ByteArray): String? {
        if (payload.size < 12) return null
        val questionCount = readUInt16(payload, 4)
        if (questionCount < 1) return null
        return parseName(payload, 12)?.first
    }

    /** Returns (domain, ip) pairs for every A record in a response's answer section. */
    fun parseAnswerAddresses(payload: ByteArray): List<Pair<String, Inet4Address>> {
        if (payload.size < 12) return emptyList()
        val questionCount = readUInt16(payload, 4)
        val answerCount = readUInt16(payload, 6)
        if (answerCount < 1) return emptyList()

        var offset = 12
        // Skip past the question section first.
        repeat(questionCount) {
            val (_, nextOffset) = parseName(payload, offset) ?: return emptyList()
            offset = nextOffset + 4 // QTYPE(2) + QCLASS(2)
            if (offset > payload.size) return emptyList()
        }

        val results = mutableListOf<Pair<String, Inet4Address>>()
        repeat(answerCount) {
            if (offset >= payload.size) return results
            val (name, afterName) = parseName(payload, offset) ?: return results
            offset = afterName
            if (offset + 10 > payload.size) return results
            val type = readUInt16(payload, offset)
            val recordClass = readUInt16(payload, offset + 2)
            val rdLength = readUInt16(payload, offset + 8)
            val rdataOffset = offset + 10
            if (rdataOffset + rdLength > payload.size) return results

            if (type == TYPE_A && recordClass == CLASS_IN && rdLength == 4) {
                val address = InetAddress.getByAddress(payload.copyOfRange(rdataOffset, rdataOffset + 4)) as Inet4Address
                results.add(name to address)
            }
            offset = rdataOffset + rdLength
        }
        return results
    }

    /**
     * Parses a (possibly compressed) domain name starting at [startOffset]. Returns the
     * dotted name and the offset immediately after it *in the original, uncompressed
     * stream position* (i.e. right after the pointer if one was followed).
     */
    private fun parseName(buffer: ByteArray, startOffset: Int): Pair<String, Int>? {
        val labels = mutableListOf<String>()
        var offset = startOffset
        var jumped = false
        var afterPointerOffset = -1
        var guard = 0

        while (true) {
            guard += 1
            if (guard > 128) return null
            if (offset >= buffer.size) return null
            val lengthByte = buffer[offset].toInt() and 0xFF

            if (lengthByte == 0) {
                offset += 1
                if (!jumped) afterPointerOffset = offset
                break
            }
            if (lengthByte and 0xC0 == 0xC0) {
                if (offset + 1 >= buffer.size) return null
                val pointer = ((lengthByte and 0x3F) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
                if (!jumped) afterPointerOffset = offset + 2
                jumped = true
                offset = pointer
                continue
            }
            offset += 1
            if (offset + lengthByte > buffer.size) return null
            labels.add(String(buffer, offset, lengthByte, Charsets.US_ASCII))
            offset += lengthByte
        }

        if (labels.isEmpty()) return "" to afterPointerOffset
        return labels.joinToString(".") to afterPointerOffset
    }

    private fun readUInt16(buffer: ByteArray, offset: Int): Int {
        return ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
    }
}
