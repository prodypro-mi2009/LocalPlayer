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

    /** The contents of every top-level 'mdat' box, in file order. */
    private fun mp4(raf: RandomAccessFile): String? {
        val length = raf.length()
        val digest = MessageDigest.getInstance("SHA-256")
        var pos = 0L
        var found = false
        while (pos + 8 <= length) {
            val header = ByteArray(8)
            raf.seek(pos)
            raf.readFully(header)
            var size = ((header[0].toLong() and 0xFF) shl 24) or
                ((header[1].toLong() and 0xFF) shl 16) or
                ((header[2].toLong() and 0xFF) shl 8) or
                (header[3].toLong() and 0xFF)
            val type = String(header, 4, 4, Charsets.ISO_8859_1)
            var headerSize = 8L
            if (size == 1L) {
                if (pos + 16 > length) return null
                val big = ByteArray(8)
                raf.readFully(big)
                size = 0L
                for (b in big) size = (size shl 8) or (b.toLong() and 0xFF)
                headerSize = 16L
            } else if (size == 0L) {
                size = length - pos
            }
            if (size < headerSize || pos + size > length) return null
            if (type == "mdat") {
                feed(raf, digest, pos + headerSize, pos + size)
                found = true
            }
            pos += size
        }
        return if (found) hex(digest.digest()) else null
    }

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

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
