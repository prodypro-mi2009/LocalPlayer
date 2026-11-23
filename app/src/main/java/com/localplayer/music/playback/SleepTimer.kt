package com.localplayer.music.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SleepTimerState {
    data object Off : SleepTimerState
    /** Ends at [endsAtElapsedMs] (SystemClock.elapsedRealtime). The UI computes the remaining time itself. */
    data class Counting(val endsAtElapsedMs: Long) : SleepTimerState
    data object EndOfTrack : SleepTimerState
    /** Playback stops after the LAST song of the queue (repeat is switched off while this is waiting). */
    data object EndOfQueue : SleepTimerState
}

/**
 * Sleep timer that lives with the playback service (not with any screen), so it survives the Activity
 * being recreated or closed. It uses ONE scheduled callback (no polling). When it fires it fades the volume
 * out, stops playback, stops the service and tells the UI (if it is open) to close the app.
 * All calls must be made on the main thread.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
object SleepTimer {
    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var onFinished: (() -> Unit)? = null
    private var fading = false
    /** The repeat mode the user had before "end of the queue" switched repeat off (null = nothing to restore). */
    private var savedRepeatMode: Int? = null
    /** True while the end-of-timer fade-out runs (ReplayGain must not touch the volume then). */
    val isFading: Boolean get() = fading

    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emitted once when the timer has stopped playback; the Activity (if open) closes the app. */
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    private val fire = Runnable { startFade() }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (_state.value == SleepTimerState.EndOfQueue && playbackState == Player.STATE_ENDED) finish()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (_state.value == SleepTimerState.EndOfTrack && !playWhenReady &&
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
            ) {
                finish()
            }
        }
    }

    fun attach(player: ExoPlayer, onFinished: () -> Unit) {
        this.player = player
        this.onFinished = onFinished
        player.addListener(listener)
    }

    fun detach() {
        cancel()
        player?.removeListener(listener)
        player = null
        onFinished = null
    }

    fun startMinutes(minutes: Int) {
        val p = player ?: return
        if (minutes <= 0) return
        cancelInternal(p)
        val delay = minutes * 60_000L
        _state.value = SleepTimerState.Counting(SystemClock.elapsedRealtime() + delay)
        handler.postDelayed(fire, delay)
    }

    /** Playback pauses by itself at the end of the current song, then everything is stopped. */
    fun startEndOfTrack() {
        val p = player ?: return
        cancelInternal(p)
        p.pauseAtEndOfMediaItems = true
        _state.value = SleepTimerState.EndOfTrack
    }

    /** Playback goes on to the end of the queue; after the last song everything is stopped. */
    fun startEndOfQueue() {
        val p = player ?: return
        cancelInternal(p)
        // With repeat on the queue never ends, so repeat is off until the timer is over or cancelled.
        savedRepeatMode = p.repeatMode
        p.repeatMode = Player.REPEAT_MODE_OFF
        _state.value = SleepTimerState.EndOfQueue
    }

    fun cancel() {
        player?.let { cancelInternal(it) }
    }

    private fun cancelInternal(p: ExoPlayer) {
        handler.removeCallbacks(fire)
        handler.removeCallbacks(fadeStep)
        if (fading) p.volume = PlaybackVolume.base
        fading = false
        p.pauseAtEndOfMediaItems = false
        restoreRepeat(p)
        _state.value = SleepTimerState.Off
    }

    private fun restoreRepeat(p: ExoPlayer) {
        savedRepeatMode?.let { p.repeatMode = it }
        savedRepeatMode = null
    }

    private var fadeStepIndex = 0
    private val fadeStep = object : Runnable {
        override fun run() {
            val p = player ?: return
            fadeStepIndex++
            p.volume = (1f - fadeStepIndex / FADE_STEPS.toFloat()).coerceAtLeast(0f) * PlaybackVolume.base
            if (fadeStepIndex >= FADE_STEPS) finish() else handler.postDelayed(this, FADE_STEP_MS)
        }
    }

    private fun startFade() {
        if (player == null) return
        fading = true
        fadeStepIndex = 0
        handler.post(fadeStep)
    }

    private fun finish() {
        val p = player ?: return
        handler.removeCallbacks(fire)
        handler.removeCallbacks(fadeStep)
        p.pause()
        p.stop()
        p.clearMediaItems()
        p.volume = PlaybackVolume.base
        p.pauseAtEndOfMediaItems = false
        restoreRepeat(p)
        fading = false
        _state.value = SleepTimerState.Off
        _finished.tryEmit(Unit)
        onFinished?.invoke()
    }

    private const val FADE_STEPS = 30
    private const val FADE_STEP_MS = 100L
}
