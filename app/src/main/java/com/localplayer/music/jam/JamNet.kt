package com.localplayer.music.jam

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

/** Finds the address other phones can reach this phone at (same Wi-Fi, or this phone's own hotspot). */
object JamNet {
    /** 0 = Wi-Fi / hotspot / cable (best), 1 = unknown, -1 = never (mobile data, VPN, loopback-like). */
    fun rank(interfaceName: String): Int {
        val name = interfaceName.lowercase()
        return when {
            name.startsWith("wlan") || name.startsWith("swlan") || name.startsWith("ap") || name.startsWith("eth") -> 0
            name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("tun") ||
                name.startsWith("dummy") || name.startsWith("lo") || name.startsWith("v4-") -> -1
            else -> 1
        }
    }

    /** Local IPv4 addresses (private ranges only), best first. Empty when there is no Wi-Fi or hotspot. */
    fun localAddresses(): List<String> {
        val found = ArrayList<Pair<Int, String>>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                val rank = rank(nif.name)
                if (rank < 0) continue
                for (address in nif.inetAddresses) {
                    if (address is Inet4Address && address.isSiteLocalAddress) {
                        val text = address.hostAddress ?: continue
                        found += rank to text
                    }
                }
            }
        } catch (e: SocketException) {
            return emptyList()
        }
        return found.sortedBy { it.first }.map { it.second }.distinct()
    }
}
