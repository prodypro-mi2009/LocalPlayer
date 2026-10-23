package com.localplayer.music.data.effects

import java.io.RandomAccessFile

/**
 * Minimal read-only reader for the iTunes free-form atoms (`----` inside moov/udta/meta/ilst) of an MP4/M4A file.
 * The tag library used by the app only exposes a fixed list of known MP4 atoms, so ReplayGain values (which are
 * free-form atoms such as "replaygain_track_gain") are read here. Bounds-checked: malformed files give an empty map.
 * Returned keys are upper-case atom names, e.g. "REPLAYGAIN_TRACK_GAIN".
 */
object Mp4Freeform {
    private const val MAX_BOX = 64L * 1024 * 1024

    fun read(raf: RandomAccessFile): Map<String, String> = try {
        val result = LinkedHashMap<String, String>()
        val moov = find(raf, 0, raf.length(), "moov") ?: return result
        val udta = find(raf, moov.first, moov.second, "udta") ?: return result
        val meta = find(raf, udta.first, udta.second, "meta") ?: return result
        val ilst = find(raf, meta.first + 4, meta.second, "ilst") ?: return result   // meta is a full box: 4 bytes version/flags
        var pos = ilst.first
        while (pos + 8 <= ilst.second) {
            val (size, type) = header(raf, pos) ?: break
            if (size < 8 || pos + size > ilst.second) break
            if (type == "----") parseFreeform(raf, pos + 8, pos + size)?.let { (k, v) -> if (k !in result) result[k] = v }
            pos += size
        }
        result
    } catch (e: Exception) {
        emptyMap()
    }

    /** Returns (start of payload, end of box) of the first child [name] in [start, end), or null. */
    private fun find(raf: RandomAccessFile, start: Long, end: Long, name: String): Pair<Long, Long>? {
        var pos = start
        while (pos + 8 <= end) {
            val (size, type) = header(raf, pos) ?: return null
            if (size < 8 || pos + size > end) return null
            if (type == name) return (pos + 8) to (pos + size)
            pos += size
        }
        return null
    }

    private fun header(raf: RandomAccessFile, pos: Long): Pair<Long, String>? {
        raf.seek(pos)
        val b = ByteArray(8)
        raf.readFully(b)
        val size = ((b[0].toLong() and 0xFF) shl 24) or ((b[1].toLong() and 0xFF) shl 16) or ((b[2].toLong() and 0xFF) shl 8) or (b[3].toLong() and 0xFF)
        if (size == 0L || size == 1L) return null          // "to end of file" / 64-bit sizes are not used inside ilst
        return size to String(b, 4, 4, Charsets.ISO_8859_1)
    }

    private fun payload(raf: RandomAccessFile, start: Long, end: Long, skip: Int): String? {
        val len = end - start - skip
        if (len < 0 || len > 4096) return null
        val buf = ByteArray(len.toInt())
        raf.seek(start + skip)
        raf.readFully(buf)
        return String(buf, Charsets.UTF_8)
    }

    private fun parseFreeform(raf: RandomAccessFile, start: Long, end: Long): Pair<String, String>? {
        var name: String? = null
        var value: String? = null
        var pos = start
        while (pos + 8 <= end) {
            val (size, type) = header(raf, pos) ?: return null
            if (size < 8 || pos + size > end) return null
            when (type) {
                "name" -> name = payload(raf, pos + 8, pos + size, 4)
                "data" -> if (value == null) value = payload(raf, pos + 8, pos + size, 8)      // 4 bytes type + 4 bytes locale
            }
            pos += size
        }
        return if (name != null && value != null) name.uppercase() to value else null
    }
}
