package com.localplayer.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * Playback position for progress displays. It refreshes once per second, and only while
 * music is playing AND the screen is visible (STARTED) - nothing runs in the background.
 */
@Composable
fun rememberPlaybackPosition(
    isPlaying: Boolean,
    resetKey: Any?,
    positionMs: () -> Long,
): MutableLongState {
    val state = remember(resetKey) { mutableLongStateOf(positionMs()) }
    val currentPositionMs = rememberUpdatedState(positionMs)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(isPlaying, resetKey, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            state.longValue = currentPositionMs.value()
            while (isPlaying) {
                delay(1_000)
                state.longValue = currentPositionMs.value()
            }
        }
    }
    return state
}

/**
 * Playback position as a 0..1 fraction that advances by at most about one pixel per update (see
 * [PlaybackProgress.stepMs]), so a progress bar looks continuous. Nothing runs while paused or in the background.
 *
 * RULE: read the returned value ONLY in draw lambdas (Canvas / `LinearProgressIndicator(progress = { ... })`),
 * never in composition. Then every step redraws just that one bar, with no recomposition and no layout.
 *
 * [durationMs] is a key on purpose: it arrives after the track starts, and a paused bar must still catch up.
 */
@Composable
fun rememberSmoothFraction(
    isPlaying: Boolean,
    resetKey: Any?,
    durationMs: Long,
    positionMs: () -> Long,
): MutableFloatState {
    val currentPosition = rememberUpdatedState(positionMs)
    val currentDuration = rememberUpdatedState(durationMs)
    val state = remember(resetKey) { mutableFloatStateOf(PlaybackProgress.fraction(positionMs(), durationMs)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    // The screen width is an upper bound of the bar width, so the step is never longer than one pixel of travel.
    val barWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    LaunchedEffect(isPlaying, resetKey, durationMs, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            state.floatValue = PlaybackProgress.fraction(currentPosition.value(), currentDuration.value)
            while (isPlaying) {
                delay(PlaybackProgress.stepMs(currentDuration.value, barWidthPx))
                state.floatValue = PlaybackProgress.fraction(currentPosition.value(), currentDuration.value)
            }
        }
    }
    return state
}
