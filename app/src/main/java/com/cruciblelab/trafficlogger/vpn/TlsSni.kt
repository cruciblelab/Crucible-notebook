package com.cruciblelab.trafficlogger.vpn

/**
 * TLS ClientHello'dan SNI (Server Name Indication) ayrıştırma - Faz 2: DNS'e hiç
 * bağımlı olmadan (DoH/DoT kullanan ya da hedef IP'yi zaten bilen uygulamalar için de)
 * bağlanılan domain'i tespit etmeyi sağlar.
 *
 * Bir TCP bağlantısının client'tan gelen İLK payload'ı üzerinde çalışır - ClientHello
 * neredeyse her zaman tek bir TCP segmentine sığar (birkaç yüz bayt). Parçalanmış
 * (birden fazla segmente bölünmüş) bir ClientHello'yu segmentler arasında BİRLEŞTİRMEYE
 * çalışmaz; bu durumda sadece SNI tespit edilemez ve bağlantı SNI'siz (mevcut davranış)
 * devam eder - hiçbir şey bozulmaz, hiçbir veri kaybolmaz.
 *
 * Sadece okur/ayrıştırır - hiçbir TLS içeriğini değiştirmez, sertifika/anahtar ile
 * ilgisi yoktur ve trafiği deşifre etmez. ClientHello (SNI dahil) TLS 1.2 ve 1.3'te de
 * her zaman düz metin olarak gönderilir (Encrypted ClientHello / ECH henüz yaygın
 * değil ve burada ele alınmıyor).
 */
object TlsSni {

    private const val CONTENT_TYPE_HANDSHAKE = 0x16
    private const val HANDSHAKE_TYPE_CLIENT_HELLO = 0x01
    private const val EXTENSION_SERVER_NAME = 0x0000
    private const val SNI_TYPE_HOST_NAME = 0x00

    /** Bulursa host adını döner, bulamazsa ya da payload bir TLS ClientHello değilse null. */
    fun parseSni(payload: ByteArray): String? = try {
        parseInternal(payload)
    } catch (e: Exception) {
        // Kasıtlı geniş yakalama: burada atılan HERHANGİ bir istisna (index dışı, vs.)
        // "SNI bulunamadı" anlamına gelir - bağlantı normal akışına devam eder, asla
        // düşürülmez/bozulmaz sadece bu ayrıştırma denemesi yüzünden.
        null
    }

    private fun parseInternal(p: ByteArray): String? {
        var offset = 0
        fun need(n: Int) = offset + n <= p.size
        fun u8(): Int {
            val v = p[offset].toInt() and 0xFF
            offset += 1
            return v
        }
        fun u16(): Int {
            if (!need(2)) error("eof")
            val v = ((p[offset].toInt() and 0xFF) shl 8) or (p[offset + 1].toInt() and 0xFF)
            offset += 2
            return v
        }
        fun u24(): Int {
            if (!need(3)) error("eof")
            val v = ((p[offset].toInt() and 0xFF) shl 16) or
                ((p[offset + 1].toInt() and 0xFF) shl 8) or (p[offset + 2].toInt() and 0xFF)
            offset += 3
            return v
        }

        // TLS kayıt (record) başlığı: type(1) + legacy_version(2) + length(2)
        if (!need(5)) return null
        if (u8() != CONTENT_TYPE_HANDSHAKE) return null
        offset += 2 // legacy_record_version
        val recordLength = u16()
        if (!need(recordLength)) return null // parçalanmış/eksik kayıt - pes ediyoruz

        // Handshake başlığı: msg_type(1) + length(3)
        if (!need(4)) return null
        if (u8() != HANDSHAKE_TYPE_CLIENT_HELLO) return null
        u24() // handshake body length (kontrol etmiyoruz, record sınırına güveniyoruz)

        // client_version(2) + random(32)
        if (!need(2 + 32)) return null
        offset += 2 + 32

        // session_id
        if (!need(1)) return null
        val sessionIdLen = u8()
        if (!need(sessionIdLen)) return null
        offset += sessionIdLen

        // cipher_suites
        if (!need(2)) return null
        val cipherSuitesLen = u16()
        if (!need(cipherSuitesLen)) return null
        offset += cipherSuitesLen

        // compression_methods
        if (!need(1)) return null
        val compressionLen = u8()
        if (!need(compressionLen)) return null
        offset += compressionLen

        // extensions (TLS 1.2/1.3 ClientHello'da her zaman bulunur; yoksa SNI de yok)
        if (!need(2)) return null
        val extensionsLen = u16()
        if (!need(extensionsLen)) return null
        val extensionsEnd = offset + extensionsLen

        while (offset + 4 <= extensionsEnd) {
            val extType = u16()
            val extLen = u16()
            if (offset + extLen > extensionsEnd) return null
            if (extType == EXTENSION_SERVER_NAME) {
                return parseServerNameExtension(p, offset, offset + extLen)
            }
            offset += extLen
        }
        return null
    }

    private fun parseServerNameExtension(p: ByteArray, start: Int, end: Int): String? {
        var o = start
        if (o + 2 > end) return null
        val listLen = ((p[o].toInt() and 0xFF) shl 8) or (p[o + 1].toInt() and 0xFF)
        o += 2
        val listEnd = minOf(o + listLen, end)
        while (o + 3 <= listEnd) {
            val nameType = p[o].toInt() and 0xFF
            val nameLen = ((p[o + 1].toInt() and 0xFF) shl 8) or (p[o + 2].toInt() and 0xFF)
            o += 3
            if (o + nameLen > listEnd) return null
            if (nameType == SNI_TYPE_HOST_NAME) {
                val name = String(p, o, nameLen, Charsets.US_ASCII).trim()
                return name.ifBlank { null }
            }
            o += nameLen
        }
        return null
    }
}
