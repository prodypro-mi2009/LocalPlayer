package com.localplayer.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.localplayer.music.R
import com.localplayer.music.ui.theme.rememberHaptics

/** Fraction of the row width that has to be swiped before the action fires. Easy to tune. */
const val SWIPE_TO_QUEUE_THRESHOLD = 0.7f

/** How the song rows behave: swipe gestures on/off, and the optional "add to queue" button (used when swipe is off). */
data class RowGestures(val swipe: Boolean = true, val queueButton: Boolean = false)

val LocalRowGestures = androidx.compose.runtime.compositionLocalOf { RowGestures() }

/**
 * Two-way queue gesture used by every track row:
 *  - swipe LEFT  -> [onSwipeToEnd]: add the song to the END of the queue;
 *  - swipe RIGHT -> [onSwipeToNext]: add the song as the NEXT song (right after the current one).
 * Either callback may be null, which disables that direction only.
 *
 * The row always springs back (it is never removed from the list); the callbacks perform the queue action.
 * Only horizontal drags are handled, so vertical scrolling is unaffected, and taps / the three-dot menu on
 * the row keep working.
 *
 * The content layer is TRANSPARENT (an opaque layer behind each row once hid the user's custom background as a
 * black block), and the action hint is only drawn while the row is actually being dragged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToQueue(
    onSwipeToEnd: (() -> Unit)?,
    onSwipeToNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
    threshold: Float = SWIPE_TO_QUEUE_THRESHOLD,
    content: @Composable () -> Unit,
) {
    if (onSwipeToEnd == null && onSwipeToNext == null) {
        content()
        return
    }
    val toEnd = rememberUpdatedState(onSwipeToEnd)
    val toNext = rememberUpdatedState(onSwipeToNext)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> toEnd.value?.invoke()
                SwipeToDismissBoxValue.StartToEnd -> toNext.value?.invoke()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false // never settle in the dismissed position: the row stays where it is
        },
        positionalThreshold = { distance -> distance * threshold },
    )
    // One haptic tick when the swipe crosses the commit point (the target flips away from Settled).
    val haptics = rememberHaptics()
    LaunchedEffect(state) {
        snapshotFlow { state.targetValue }.collect { if (it != SwipeToDismissBoxValue.Settled) haptics.threshold() }
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = onSwipeToNext != null,
        enableDismissFromEndToStart = onSwipeToEnd != null,
        backgroundContent = {
            val direction = state.dismissDirection
            if (direction == SwipeToDismissBoxValue.EndToStart || direction == SwipeToDismissBoxValue.StartToEnd) {
                val toTheNext = direction == SwipeToDismissBoxValue.StartToEnd
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (toTheNext) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer,
                        )
                        .padding(horizontal = 24.dp),
                    contentAlignment = if (toTheNext) Alignment.CenterStart else Alignment.CenterEnd,
                ) {
                    Icon(
                        imageVector = if (toTheNext) AppIcons.PlayNext else AppIcons.Queue,
                        contentDescription = stringResource(
                            if (toTheNext) R.string.swipe_play_next else R.string.move_to_end_of_queue,
                        ),
                    )
                }
            }
        },
    ) {
        content()
    }
}
