package com.localplayer.music.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HexTest {
    @Test fun emptyArray() = assertEquals("", ByteArray(0).toLowerHex())

    @Test fun knownBytes() =
        assertEquals("00017f80ff", byteArrayOf(0, 1, 0x7F, 0x80.toByte(), 0xFF.toByte()).toLowerHex())

    @Test fun matchesFormatForEveryByte() {
        val all = ByteArray(256) { it.toByte() }
        assertEquals(all.joinToString("") { "%02x".format(it) }, all.toLowerHex())
    }
}
