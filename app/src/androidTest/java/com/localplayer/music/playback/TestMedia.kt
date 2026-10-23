package com.localplayer.music.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import com.localplayer.music.data.db.TrackListItem
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Shared helpers of the instrumented queue tests. */
object TestMedia {
    fun track(n: Int, uri: String = "content://test/$n") = TrackListItem(
        id = n.toLong(), uri = uri, title = "T$n", artist = null, primaryArtist = null,
        primaryArtistKey = null, album = null, albumArtist = null, albumGroupKey = null, genre = null, year = null,
        trackNumber = null, discNumber = null, durationMs = 30_000L, hasArtwork = false, lastModified = 1L,
    )

    fun item(n: Int, uri: String = "content://test/$n"): MediaItem = MediaItemFactory.create(track(n, uri), "?", "?")

    /** A valid, silent 8 kHz / 16-bit / mono WAV file, so ExoPlayer can really prepare and play it. */
    fun silentWav(dir: File, name: String, seconds: Int = 30): File {
        val sampleRate = 8_000
        val dataBytes = sampleRate * 2 * seconds
        val buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(dataBytes)
        val file = File(dir, name)
        file.writeBytes(buf.array())
        return file
    }

    fun <T> onMainResult(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: Result<T>? = null
        val done = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            result = runCatching(block)
            done.countDown()
        }
        check(done.await(30, TimeUnit.SECONDS)) { "main thread did not respond" }
        return result!!.getOrThrow()
    }

    fun onMain(block: () -> Unit) = onMainResult(block)

    /** Polls [condition] on the main thread until it holds or [timeoutMs] passes. */
    fun waitUntil(timeoutMs: Long = 15_000L, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (onMainResult(condition)) return true
            Thread.sleep(50)
        }
        return onMainResult(condition)
    }
}
