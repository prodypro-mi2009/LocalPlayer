package com.localplayer.music.jam

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/**
 * The guest's own player: it plays the host's current song from a copy already loaded on this phone (nothing is
 * streamed while playing, so the Wi-Fi cannot make it stutter) and keeps in step with the host. [fileFor] gives the
 * loaded copy of a queue entry, or null while it is not complete yet. It has no notification of its own; the Jam notification keeps the app alive.
 * Everything here runs on the main thread. Call [update] when the host's state changes and [tick] about twice a second.
 */
class JamGuestPlayer(context: Context, private val fileFor: (String) -> File?) {
    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    private var state: JamState? = null
    private var hostClockOffsetMs = 0L
    private var enabled = true
    private var loadedId: String? = null
    private var lastSeekAt = 0L
    private var failedAt = 0L
    private var speed = 1f
    private val handler = Handler(Looper.getMainLooper())
    private val tickAgain = Runnable { tick() }

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Load it again a little later (the host may have been busy).
                failedAt = JamClock.nowMs()
                loadedId = null
            }
        })
    }

    /** New news from the host; [offsetMs] = host clock minus this phone's clock; [playHere] = the guest's switch. */
    fun update(newState: JamState?, offsetMs: Long, playHere: Boolean) {
        state = newState
        hostClockOffsetMs = offsetMs
        enabled = playHere
        tick()
    }

    fun tick() {
        val s = state
        val id = s?.queue?.getOrNull(s.currentIndex)?.id
        if (!enabled || s == null || id == null) {
            stopPlaying()
            return
        }
        val now = JamClock.nowMs()
        val hostNow = now + hostClockOffsetMs
        val expected = JamSync.expectedPositionMs(s, hostNow)
        if (loadedId != id) {
            if (now - failedAt < RETRY_MS) return
            val file = fileFor(id)
            if (file == null) {
                // Not loaded on this phone yet: silence until it is (the next tick looks again).
                if (player.mediaItemCount > 0) {
                    player.stop()
                    player.clearMediaItems()
                }
                return
            }
            loadedId = id
            lastSeekAt = now
            speed = 1f
            player.setPlaybackSpeed(1f)
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)), expected.coerceAtLeast(0L))
            player.prepare()
        }
        // The host may have chosen a moment a little ahead for everybody to start together.
        val waitMs = s.anchorMs - hostNow
        val started = s.playing && waitMs <= 0L
        player.playWhenReady = started
        handler.removeCallbacks(tickAgain)
        if (s.playing && !started) {
            handler.postDelayed(tickAgain, waitMs.coerceIn(1L, 600L))
            if (player.playbackState == Player.STATE_READY && kotlin.math.abs(player.currentPosition - s.positionMs) > PAUSED_SLACK_MS) {
                seek(s.positionMs, now)
            }
            return
        }
        if (player.playbackState != Player.STATE_READY || now - lastSeekAt < SETTLE_MS) return
        if (!s.playing) {
            // Paused: only the place matters.
            if (kotlin.math.abs(player.currentPosition - s.positionMs) > PAUSED_SLACK_MS) seek(s.positionMs, now)
            return
        }
        when (val fix = JamSync.fix(player.currentPosition, expected)) {
            is JamSync.Fix.Seek -> seek(fix.positionMs, now)
            is JamSync.Fix.Speed -> if (fix.value != speed) {
                speed = fix.value
                player.setPlaybackSpeed(speed)
            }
        }
    }

    private fun seek(positionMs: Long, now: Long) {
        lastSeekAt = now
        speed = 1f
        player.setPlaybackSpeed(1f)
        player.seekTo(positionMs)
    }

    private fun stopPlaying() {
        handler.removeCallbacks(tickAgain)
        if (loadedId != null || player.mediaItemCount > 0) {
            player.stop()
            player.clearMediaItems()
        }
        loadedId = null
    }

    /** Length of the song being played (0 until it is known). Main thread. */
    fun durationMs(): Long = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L

    fun release() {
        handler.removeCallbacks(tickAgain)
        player.release()
    }

    private companion object {
        const val RETRY_MS = 3_000L
        const val SETTLE_MS = 1_500L
        const val PAUSED_SLACK_MS = 150L
    }
}
