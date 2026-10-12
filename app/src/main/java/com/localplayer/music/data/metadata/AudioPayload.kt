package com.localplayer.music.data.metadata

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Fingerprint of the AUDIO DATA of a file, ignoring every tag area. A tag edit must not change it:
 * if it does, the audio stream was altered and the edit is rejected before the original is touched.
 * Nothing is decoded or re-encoded; only bytes are hashed.
 *
 * Returns null when the layout is not understood. Callers then skip this extra check (the other
 * verifications still apply); it never produces a false "unchanged".
 */
object AudioPayload {

    fun sha256(file: File, container: AudioContainer): String? = try {
        RandomAccessFile(file, "r").use { raf ->
            when (container) {
                AudioContainer.MP3 -> mp3(raf)
                AudioContainer.FLAC -> flac(raf)
                AudioContainer.MP4 -> mp4(raf)
                else -> null
            }
        }
    } catch (e: Exception) {
        null
    }

    /** From the first MPEG frame after the ID3v2 tag up to (excluding) a trailing ID3v1 tag. */
    private fun mp3(raf: RandomAccessFile): String? {
        val length = raf.length()
        var start = 0L
        if (length >= 10) {
            val head = ByteArray(10)
            raf.seek(0)
            raf.readFully(head)
            if (head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) {
                val size = ((head[6].toInt() and 0x7F) shl 21) or
                    ((head[7].toInt() and 0x7F) shl 14) or
                    ((head[8].toInt() and 0x7F) shl 7) or
                    (head[9].toInt() and 0x7F)
                val footer = (head[5].toInt() and 0x10) != 0
                start = 10L + size + (if (footer) 10 else 0)
            }
        }
        var end = length
        if (length - start >= 128) {
            val tag = ByteArray(3)
            raf.seek(length - 128)
            raf.readFully(tag)
            if (tag[0] == 'T'.code.toByte() && tag[1] == 'A'.code.toByte() && tag[2] == 'G'.code.toByte()) end = length - 128
        }
        if (start >= end) return null
        // Skip padding between the tag and the first frame: the writer may normalise it.
        val window = ByteArray(minOf(end - start, 65536L).toInt())
        raf.seek(start)
        raf.readFully(window)
        var offset = -1
        for (i in 0 until window.size - 1) {
            if (window[i] == 0xFF.toByte() && (window[i + 1].toInt() and 0xE0) == 0xE0) {
                offset = i
                break
            }
        }
        if (offset < 0) return null
        return hash(raf, start + offset, end)
    }

    /** Everything after the last metadata block. */
    private fun flac(raf: RandomAccessFile): String? {
        val length = raf.length()
        if (length < 8) return null
        val magic = ByteArray(4)
        raf.seek(0)
        raf.readFully(magic)
        if (String(magic, Charsets.ISO_8859_1) != "fLaC") return null
        var pos = 4L
        while (true) {
            if (pos + 4 > length) return null
            val header = ByteArray(4)
            raf.seek(pos)
            raf.readFully(header)
            val last = (header[0].toInt() and 0x80) != 0
            val blockLength = ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)
            pos += 4L + blockLength
            if (last) break
        }
        if (pos >= length) return null
        return hash(raf, pos, length)
    }

    /**
     * The encoded AUDIO SAMPLES, located through the sample tables (stsc + stsz + stco/co64) of every track and
     * hashed in track/chunk/sample order together with their sizes. The position of the samples in the file is
     * deliberately NOT part of the fingerprint: when the tag grows, writers move 'moov' in front of 'mdat', turn
     * 'stco' into 'co64' and may rewrite the 'mdat' header, and all of that is a legitimate edit as long as the
     * samples themselves stay byte for byte the same. A changed, dropped, reordered or re-framed sample changes
     * the hash.
     *
     * Files without sample tables (fragmented MP4) fall back to hashing the contents of every top-level 'mdat'.
     */
    private fun mp4(raf: RandomAccessFile): String? {
        val length = raf.length()
        val atoms = ArrayList<Atom>()
        var pos = 0L
        while (pos + 8 <= length) {
            val atom = readAtom(raf, pos, length) ?: return null
            atoms.add(atom)
            pos += atom.size
        }
        val moov = atoms.firstOrNull { it.type == "moov" } ?: return null
        if (moov.size > MAX_MOOV) return null
        val moovBytes = ByteArray((moov.size - moov.header).toInt())
        raf.seek(moov.start + moov.header)
        raf.readFully(moovBytes)

        val digest = MessageDigest.getInstance("SHA-256")
        var samples = 0L
        for (trak in children(moovBytes, 0, moovBytes.size, "trak")) {
            val stbl = path(moovBytes, trak, "mdia", "minf", "stbl") ?: continue
            val table = sampleTable(moovBytes, stbl) ?: return null
            for (sample in table) {
                if (sample.offset < 0 || sample.offset + sample.size > length) return null
                digest.update(byteArrayOf(
                    (sample.size shr 24).toByte(), (sample.size shr 16).toByte(),
                    (sample.size shr 8).toByte(), sample.size.toByte(),
                ))
                feed(raf, digest, sample.offset, sample.offset + sample.size)
                samples++
            }
        }
        if (samples > 0) return hex(digest.digest())

        // No sample tables: hash the media data boxes themselves.
        val fallback = MessageDigest.getInstance("SHA-256")
        var found = false
        for (atom in atoms) {
            if (atom.type == "mdat") {
                feed(raf, fallback, atom.start + atom.header, atom.start + atom.size)
                found = true
            }
        }
        return if (found) hex(fallback.digest()) else null
    }

    private class Atom(val type: String, val start: Long, val header: Int, val size: Long)

    /** A box header at [pos] in the file, or null when it is not valid. */
    private fun readAtom(raf: RandomAccessFile, pos: Long, length: Long): Atom? {
        val header = ByteArray(8)
        raf.seek(pos)
        raf.readFully(header)
        var size = u32(header, 0)
        val type = String(header, 4, 4, Charsets.ISO_8859_1)
        var headerSize = 8
        if (size == 1L) {
            if (pos + 16 > length) return null
            val big = ByteArray(8)
            raf.readFully(big)
            size = 0L
            for (b in big) size = (size shl 8) or (b.toLong() and 0xFF)
            headerSize = 16
        } else if (size == 0L) {
            size = length - pos
        }
        if (size < headerSize || pos + size > length) return null
        return Atom(type, pos, headerSize, size)
    }

    /** A box inside an in-memory buffer: [start] is the offset of its header, [end] the end of the box. */
    private class Box(val type: String, val bodyStart: Int, val end: Int)

    private fun children(buf: ByteArray, from: Int, to: Int, type: String? = null): List<Box> {
        val result = ArrayList<Box>()
        var p = from
        while (p + 8 <= to) {
            var size = u32(buf, p)
            val name = String(buf, p + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) {
                if (p + 16 > to) break
                size = 0L
                for (i in 8 until 16) size = (size shl 8) or (buf[p + i].toLong() and 0xFF)
                header = 16
            } else if (size == 0L) {
                size = (to - p).toLong()
            }
            if (size < header || p + size > to) break
            if (type == null || type == name) result.add(Box(name, p + header, (p + size).toInt()))
            p += size.toInt()
        }
        return result
    }

    private fun path(buf: ByteArray, from: Box, vararg names: String): Box? {
        var current = from
        for (name in names) {
            current = children(buf, current.bodyStart, current.end, name).firstOrNull() ?: return null
        }
        return current
    }

    private class Sample(val offset: Long, val size: Long)

    /** Every sample of one track in file-table order, or null when the tables are inconsistent. */
    private fun sampleTable(buf: ByteArray, stbl: Box): List<Sample>? {
        val stsz = children(buf, stbl.bodyStart, stbl.end, "stsz").firstOrNull() ?: return emptyList()
        val stsc = children(buf, stbl.bodyStart, stbl.end, "stsc").firstOrNull() ?: return null
        val stco = children(buf, stbl.bodyStart, stbl.end, "stco").firstOrNull()
        val co64 = children(buf, stbl.bodyStart, stbl.end, "co64").firstOrNull()
        if (stco == null && co64 == null) return null

        // stsz: version/flags, default size, count, [sizes]
        val defaultSize = u32(buf, stsz.bodyStart + 4)
        val count = u32(buf, stsz.bodyStart + 8).toInt()
        if (count < 0 || (defaultSize == 0L && stsz.bodyStart + 12 + 4L * count > stsz.end)) return null
        fun sizeOf(i: Int) = if (defaultSize != 0L) defaultSize else u32(buf, stsz.bodyStart + 12 + 4 * i)

        val offsets: List<Long> = if (stco != null) {
            val n = u32(buf, stco.bodyStart + 4).toInt()
            if (n < 0 || stco.bodyStart + 8 + 4L * n > stco.end) return null
            List(n) { u32(buf, stco.bodyStart + 8 + 4 * it) }
        } else {
            val box = co64!!
            val n = u32(buf, box.bodyStart + 4).toInt()
            if (n < 0 || box.bodyStart + 8 + 8L * n > box.end) return null
            List(n) { i ->
                var v = 0L
                for (k in 0 until 8) v = (v shl 8) or (buf[box.bodyStart + 8 + 8 * i + k].toLong() and 0xFF)
                v
            }
        }

        // stsc: version/flags, entry count, [first chunk, samples per chunk, description index]
        val entries = u32(buf, stsc.bodyStart + 4).toInt()
        if (entries <= 0 || stsc.bodyStart + 8 + 12L * entries > stsc.end) return null
        val firstChunk = IntArray(entries) { u32(buf, stsc.bodyStart + 8 + 12 * it).toInt() }
        val perChunk = IntArray(entries) { u32(buf, stsc.bodyStart + 12 + 12 * it).toInt() }

        val samples = ArrayList<Sample>(count)
        var next = 0
        for (chunk in 1..offsets.size) {
            var e = entries - 1
            while (e > 0 && firstChunk[e] > chunk) e--
            var offset = offsets[chunk - 1]
            repeat(perChunk[e]) {
                if (next >= count) return null
                val size = sizeOf(next++)
                samples.add(Sample(offset, size))
                offset += size
            }
        }
        return if (next == count) samples else null
    }

    private fun u32(b: ByteArray, p: Int): Long =
        ((b[p].toLong() and 0xFF) shl 24) or ((b[p + 1].toLong() and 0xFF) shl 16) or
            ((b[p + 2].toLong() and 0xFF) shl 8) or (b[p + 3].toLong() and 0xFF)

    private fun hash(raf: RandomAccessFile, start: Long, end: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        feed(raf, digest, start, end)
        return hex(digest.digest())
    }

    private fun feed(raf: RandomAccessFile, digest: MessageDigest, start: Long, end: Long) {
        raf.seek(start)
        val buffer = ByteArray(64 * 1024)
        var remaining = end - start
        while (remaining > 0) {
            val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) break
            digest.update(buffer, 0, read)
            remaining -= read
        }
    }

    private const val MAX_MOOV = 64L * 1024 * 1024

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
