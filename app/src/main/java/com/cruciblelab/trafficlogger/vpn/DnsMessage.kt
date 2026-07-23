package com.cruciblelab.trafficlogger.vpn

/**
 * Reads just enough of a DNS message (RFC 1035) to pull out the first question's
 * QNAME, i.e. the domain being resolved. No answer parsing, no compression pointer
 * following past the question section (queries don't use it).
 */
object DnsMessage {

    fun parseQuestionName(payload: ByteArray): String? {
        if (payload.size < 12) return null
        val questionCount = readUInt16(payload, 4)
        if (questionCount < 1) return null

        val labels = mutableListOf<String>()
        var offset = 12
        while (offset < payload.size) {
            val length = payload[offset].toInt() and 0xFF
            if (length == 0) {
                offset += 1
                break
            }
            if (length and 0xC0 == 0xC0) {
                // Compression pointer shouldn't appear in the question name of a query;
                // bail out rather than guessing.
                return null
            }
            offset += 1
            if (offset + length > payload.size) return null
            labels.add(String(payload, offset, length, Charsets.US_ASCII))
            offset += length
        }
        if (labels.isEmpty()) return null
        return labels.joinToString(".")
    }

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

    private fun readUInt16(buffer: ByteArray, offset: Int): Int {
        return ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
    }
}
