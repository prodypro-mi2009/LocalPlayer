package com.localplayer.music.data.metadata

import java.io.File
import java.io.RandomAccessFile

/** Detects the real container from file content so ".aac" and ".m4a" are never confused. */
object AudioSniffer {

    fun sniff(file: File, nameHint: String): AudioContainer {
        val extension = nameHint.substringAfterLast('.', "").lowercase()
        return try {
            RandomAccessFile(file, "r").use { raf -> sniff(raf, extension) }
        } catch (e: Exception) {
            AudioContainer.UNKNOWN
        }
    }

    private fun sniff(raf: RandomAccessFile, extension: String): AudioContainer {
        val length = raf.length()
        var offset = 0L

        // Skip any leading ID3v2 tag(s): MP3, FLAC and raw AAC can all start with one.
        var guard = 0
        while (guard++ < 4) {
            val header = readAt(raf, offset, 10) ?: break
            if (header.size == 10 && header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                val size = ((header[6].toInt() and 0x7F) shl 21) or
                    ((header[7].toInt() and 0x7F) shl 14) or
                    ((header[8].toInt() and 0x7F) shl 7) or
                    (header[9].toInt() and 0x7F)
                val hasFooter = (header[5].toInt() and 0x10) != 0
                offset += 10L + size + if (hasFooter) 10 else 0
                if (offset >= length) return AudioContainer.UNKNOWN
            } else {
                break
            }
        }

        val head = readAt(raf, offset, 12) ?: return AudioContainer.UNKNOWN
        if (head.size >= 4 && head.ascii(0, 4) == "fLaC") return AudioContainer.FLAC
        if (head.size >= 8 && head.ascii(4, 4) == "ftyp") return AudioContainer.MP4

        if (head.size >= 2 && (head[0].toInt() and 0xFF) == 0xFF) {
            val second = head[1].toInt() and 0xFF
            if ((second and 0xE0) == 0xE0) {
                val layer = (second shr 1) and 0x03 // 00 = AAC (ADTS), 01 = Layer III, 10 = II, 11 = I
                return if (layer == 0) AudioContainer.AAC_ADTS else AudioContainer.MP3
            }
        }

        // Unknown signature: trust the extension only for MP3 (frame sync may be preceded by junk).
        return when (extension) {
            "mp3" -> AudioContainer.MP3
            else -> AudioContainer.UNKNOWN
        }
    }

    private fun readAt(raf: RandomAccessFile, offset: Long, count: Int): ByteArray? {
        if (offset >= raf.length()) return null
        raf.seek(offset)
        val buffer = ByteArray(count)
        val read = raf.read(buffer)
        return if (read <= 0) null else buffer.copyOf(read)
    }

    private fun ByteArray.ascii(start: Int, count: Int): String =
        String(this, start, count, Charsets.ISO_8859_1)
}
