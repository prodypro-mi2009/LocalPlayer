package com.localplayer.music.playback.session

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.CRC32

/** Compact binary format with a version and a CRC, so a damaged file is detected and ignored, never half-read. */
object SessionCodec {
    private const val QUEUE_MAGIC = 0x4C505131 // "LPQ1"
    private const val STATE_MAGIC = 0x4C505331 // "LPS1"
    private const val FORMAT = 1
    /** writeUTF allows 65535 bytes (up to 3 per char). Longer values are not real URIs: skip them. */
    private const val MAX_CHARS = 20_000

    fun encodeQueue(queue: SavedQueue): ByteArray {
        val entries = queue.entries.filter { it.trackUri.length <= MAX_CHARS && it.occurrenceId.length <= MAX_CHARS }
        return seal { out ->
            out.writeInt(QUEUE_MAGIC)
            out.writeInt(FORMAT)
            out.writeLong(queue.generation)
            out.writeInt(entries.size)
            for (e in entries) {
                out.writeUTF(e.occurrenceId)
                out.writeUTF(e.trackUri)
                out.writeBoolean(e.playNext)
                out.writeInt(e.rank)
            }
        }
    }

    fun decodeQueue(bytes: ByteArray): SavedQueue? = open(bytes) { input ->
        if (input.readInt() != QUEUE_MAGIC || input.readInt() != FORMAT) return@open null
        val generation = input.readLong()
        val count = input.readInt()
        if (count < 0 || count > 5_000_000) return@open null
        val entries = ArrayList<SavedEntry>(minOf(count, 100_000))
        repeat(count) {
            entries += SavedEntry(input.readUTF(), input.readUTF(), input.readBoolean(), input.readInt())
        }
        SavedQueue(generation, entries)
    }

    fun encodeState(state: SavedState): ByteArray = seal { out ->
        out.writeInt(STATE_MAGIC)
        out.writeInt(FORMAT)
        out.writeLong(state.queueGeneration)
        out.writeBoolean(state.currentOccurrenceId != null)
        if (state.currentOccurrenceId != null) out.writeUTF(state.currentOccurrenceId)
        out.writeInt(state.currentIndex)
        out.writeLong(state.positionMs)
        out.writeBoolean(state.shuffle)
        out.writeInt(state.repeatMode)
        out.writeLong(state.writtenAtMs)
    }

    fun decodeState(bytes: ByteArray): SavedState? = open(bytes) { input ->
        if (input.readInt() != STATE_MAGIC || input.readInt() != FORMAT) return@open null
        val generation = input.readLong()
        val occ = if (input.readBoolean()) input.readUTF() else null
        SavedState(generation, occ, input.readInt(), input.readLong(), input.readBoolean(), input.readInt(), input.readLong())
    }

    private fun seal(write: (DataOutputStream) -> Unit): ByteArray {
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { write(it) }
        val bytes = body.toByteArray()
        val crc = CRC32().apply { update(bytes) }.value
        val full = ByteArrayOutputStream(bytes.size + 8)
        DataOutputStream(full).use { it.write(bytes); it.writeLong(crc) }
        return full.toByteArray()
    }

    private fun <T> open(bytes: ByteArray, read: (DataInputStream) -> T?): T? {
        if (bytes.size < 8) return null
        val bodyLen = bytes.size - 8
        val stored = DataInputStream(ByteArrayInputStream(bytes, bodyLen, 8)).readLong()
        val actual = CRC32().apply { update(bytes, 0, bodyLen) }.value
        if (stored != actual) return null
        return try {
            DataInputStream(ByteArrayInputStream(bytes, 0, bodyLen)).use(read)
        } catch (_: java.io.IOException) {
            null
        }
    }
}
