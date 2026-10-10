package com.localplayer.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
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
