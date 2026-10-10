package com.localplayer.music.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player
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
}

/**
 * Sleep timer that lives with the playback service (not with any screen), so it survives the Activity
 * being recreated or closed. It uses ONE scheduled callback (no polling). When it fires it fades the volume
 * out, stops playback, stops the service and tells the UI (if it is open) to close the app.
 * All calls must be made on the main thread.
 */
object SleepTimer {
    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var onFinished: (() -> Unit)? = null
    private var fading = false

    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emitted once when the timer has stopped playback; the Activity (if open) closes the app. */
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    private val fire = Runnable { startFade() }

    private val listener = object : Player.Listener {
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

    fun cancel() {
        player?.let { cancelInternal(it) }
    }

    private fun cancelInternal(p: ExoPlayer) {
        handler.removeCallbacks(fire)
        handler.removeCallbacks(fadeStep)
        if (fading) p.volume = 1f
        fading = false
        p.pauseAtEndOfMediaItems = false
        _state.value = SleepTimerState.Off
    }

    private var fadeStepIndex = 0
    private val fadeStep = object : Runnable {
        override fun run() {
            val p = player ?: return
            fadeStepIndex++
            p.volume = (1f - fadeStepIndex / FADE_STEPS.toFloat()).coerceAtLeast(0f)
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
        p.volume = 1f
        p.pauseAtEndOfMediaItems = false
        fading = false
        _state.value = SleepTimerState.Off
        _finished.tryEmit(Unit)
        onFinished?.invoke()
    }

    private const val FADE_STEPS = 30
    private const val FADE_STEP_MS = 100L
}
