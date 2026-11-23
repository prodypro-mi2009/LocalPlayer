package com.localplayer.music.util

private val HEX_DIGITS = "0123456789abcdef".toCharArray()

/** Lower-case hex of the bytes (same result as `"%02x".format(b)` for each byte, without the formatter per byte). */
fun ByteArray.toLowerHex(): String {
    val out = CharArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        out[i * 2] = HEX_DIGITS[v ushr 4]
        out[i * 2 + 1] = HEX_DIGITS[v and 0x0F]
    }
    return String(out)
}
