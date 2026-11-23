package com.localplayer.music.jam

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerCheckTest {
    private fun local(text: String) = PeerCheck.isLocal(InetAddress.getByName(text))

    @Test fun localNetworkAddressesAreAccepted() {
        assertTrue(local("192.168.1.20"))
        assertTrue(local("192.168.43.7")) // a phone hotspot
        assertTrue(local("10.1.2.3"))
        assertTrue(local("172.16.5.5"))
        assertTrue(local("172.31.255.1"))
        assertTrue(local("169.254.10.10"))
        assertTrue(local("127.0.0.1"))
        assertTrue(local("fe80::1"))
        assertTrue(local("fd12:3456::1"))
    }

    @Test fun publicAddressesAreRefused() {
        assertFalse(local("8.8.8.8"))
        assertFalse(local("172.32.0.1"))
        assertFalse(local("172.15.0.1"))
        assertFalse(local("100.64.0.1"))
        assertFalse(local("2001:4860:4860::8888"))
    }

    @Test fun textAddressesFollowTheSameRule() {
        assertTrue(PeerCheck.isLocalIpv4("192.168.1.5"))
        assertTrue(PeerCheck.isLocalIpv4("10.0.0.2"))
        assertFalse(PeerCheck.isLocalIpv4("8.8.8.8"))
        assertFalse(PeerCheck.isLocalIpv4("172.32.0.1"))
        assertFalse(PeerCheck.isLocalIpv4("not-an-ip"))
        assertFalse(PeerCheck.isLocalIpv4("192.168.1"))
        assertFalse(PeerCheck.isLocalIpv4("300.1.1.1"))
    }

    @Test fun aPublicInviteIsNotAccepted() {
        assertNull(JamInvite.parse("lpjam://8.8.8.8:80/123456"))
        assertNull(JamInvite.fromParts("8.8.8.8", "123456"))
        assertEquals("192.168.1.5:8765", JamInvite.fromParts("192.168.1.5:8765", "123456")?.address())
    }
}
