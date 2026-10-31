package com.localplayer.music.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * OPT-IN fade between songs: the volume ramps down at the end of a song and up at the start of the next one.
 * (A true overlapping crossfade would need a second player; this is a fade-out followed by a fade-in.)
 *
 * It only exists while the option is on ([start] / [stop]). It uses ONE scheduled callback per song (no polling)
 * and a short 40 ms step loop only during a ramp. A song skipped by the user does not fade. The volume it writes is
 * `PlaybackVolume.base * PlaybackVolume.fade`, so ReplayGain keeps working, and it never touches the volume while the
 * sleep-timer fade-out runs. Main thread only.
 */
class TrackFader(private val player: ExoPlayer, private val fadeMs: () -> Long) : Player.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var rampFrom = 1f
    private var rampTo = 1f
    private var rampStart = 0L
    private var rampLength = 0L
    private var ramping = false

    private val startFadeOut = Runnable { beginRamp(PlaybackVolume.fade, 0f) }

    private val step = object : Runnable {
        override fun run() {
            if (!ramping) return
            val f = FadeMath.factor(rampFrom, rampTo, SystemClock.uptimeMillis() - rampStart, rampLength)
            setFactor(f)
            if (f == rampTo) ramping = false else handler.postDelayed(this, STEP_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        player.addListener(this)
        schedule()
    }

    /** Back to the normal volume and no listener: the option is off (or the service ends). */
    fun stop() {
        if (!running) return
        running = false
        player.removeListener(this)
        handler.removeCallbacks(startFadeOut)
        handler.removeCallbacks(step)
        ramping = false
        setFactor(1f)
    }

    private fun setFactor(f: Float) {
        PlaybackVolume.fade = f
        if (!SleepTimer.isFading) player.volume = PlaybackVolume.base * f
    }

    private fun beginRamp(from: Float, to: Float) {
        handler.removeCallbacks(step)
        val ms = FadeMath.rampMs(from, to, fadeMs())
        rampFrom = from; rampTo = to; rampStart = SystemClock.uptimeMillis(); rampLength = ms; ramping = true
        setFactor(from)
        handler.post(step)
    }

    private fun cancelRamp(toFactor: Float) {
        handler.removeCallbacks(step)
        handler.removeCallbacks(startFadeOut)
        ramping = false
        setFactor(toFactor)
    }

    private fun schedule() {
        handler.removeCallbacks(startFadeOut)
        if (!running || !player.isPlaying || !player.hasNextMediaItem()) return
        val wait = FadeMath.msUntilFadeOut(player.duration.takeIf { it != C.TIME_UNSET } ?: -1L, player.currentPosition, fadeMs()) ?: return
        handler.postDelayed(startFadeOut, wait)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) = schedule()
    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = schedule()
    override fun onRepeatModeChanged(repeatMode: Int) = schedule()

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val natural = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        if (natural && mediaItem != null) {
            // The previous song faded out; this one starts silent and fades in.
            handler.removeCallbacks(startFadeOut)
            beginRamp(0f, 1f)
        } else {
            cancelRamp(1f)
        }
        schedule()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
            cancelRamp(1f)
            schedule()
        }
    }

    private companion object {
        const val STEP_MS = 40L
    }
}
