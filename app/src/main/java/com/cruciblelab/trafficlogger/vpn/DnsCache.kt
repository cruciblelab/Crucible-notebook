package com.cruciblelab.trafficlogger.vpn

import java.net.Inet4Address
import java.util.Collections

/**
 * Small IP -> domain cache filled in as DNS responses pass through the relay, so that
 * later TCP/UDP connections to that same IP (which carry no domain name themselves)
 * can still be labeled with the site the app resolved just before connecting.
 */
class DnsCache(private val maxEntries: Int = 2000) {

    private val map = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                return size > maxEntries
            }
        }
    )

    fun put(address: Inet4Address, domain: String) {
        if (domain.isEmpty()) return
        map[address.hostAddress] = domain
    }

    fun lookup(address: Inet4Address): String? = map[address.hostAddress]
}
