package com.localplayer.music.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/**
 * What the scroll bar needs from a list or a grid, all in terms of a fraction 0..1 of the whole content.
 * [progress] is read in the layout phase only (the bar never recomposes while the list scrolls).
 */
class ThumbAdapter(
    val progress: () -> Float,
    val indexAt: (Float) -> Int,
    val jump: suspend (Float) -> Unit,
    val isScrolling: () -> Boolean,
)

/** Fraction of the list (position of the first visible item, including how far it is scrolled). */
fun LazyListState.thumbAdapter(): ThumbAdapter {
    val state = this
    fun maxFirst(): Int = (state.layoutInfo.totalItemsCount - state.layoutInfo.visibleItemsInfo.size).coerceAtLeast(1)
    return ThumbAdapter(
        progress = {
            val first = state.layoutInfo.visibleItemsInfo.firstOrNull()
            if (first == null) 0f
            else ((first.index + state.firstVisibleItemScrollOffset.toFloat() / first.size.coerceAtLeast(1)) / maxFirst()).coerceIn(0f, 1f)
        },
        indexAt = { f -> (f.coerceIn(0f, 1f) * maxFirst()).toInt().coerceIn(0, (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) },
        jump = { f ->
            val info = state.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()
            if (first != null && info.totalItemsCount > 0) {
                val target = f.coerceIn(0f, 1f) * maxFirst()
                val index = target.toInt().coerceIn(0, info.totalItemsCount - 1)
                // The fraction of the item is applied as an offset, so the movement is continuous instead of stepping item by item.
                state.scrollToItem(index, ((target - index) * first.size).toInt())
            }
        },
        isScrolling = { state.isScrollInProgress },
    )
}

/** The same for a grid with [columns] columns (positions are counted in rows). */
fun LazyGridState.thumbAdapter(columns: Int): ThumbAdapter {
    val state = this
    fun totalRows(): Int = (state.layoutInfo.totalItemsCount + columns - 1) / columns
    fun visibleRows(): Int {
        val vis = state.layoutInfo.visibleItemsInfo
        return if (vis.isEmpty()) 0 else vis.maxOf { it.row } - vis.minOf { it.row } + 1
    }
    fun maxFirstRow(): Int = (totalRows() - visibleRows()).coerceAtLeast(1)
    return ThumbAdapter(
        progress = {
            val first = state.layoutInfo.visibleItemsInfo.firstOrNull()
            if (first == null) 0f
            else ((first.row + state.firstVisibleItemScrollOffset.toFloat() / first.size.height.coerceAtLeast(1)) / maxFirstRow()).coerceIn(0f, 1f)
        },
        indexAt = { f ->
            ((f.coerceIn(0f, 1f) * maxFirstRow()).toInt() * columns).coerceIn(0, (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        },
        jump = { f ->
            val info = state.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()
            if (first != null && info.totalItemsCount > 0) {
                val target = f.coerceIn(0f, 1f) * maxFirstRow()
                val row = target.toInt()
                val index = (row * columns).coerceIn(0, info.totalItemsCount - 1)
                state.scrollToItem(index, ((target - row) * first.size.height).toInt())
            }
        },
        isScrolling = { state.isScrollInProgress },
    )
}

/**
 * Scroll bar for long lists: a slim thumb at the right edge that follows the list, becomes a thick, draggable one when it is
 * touched and (when [letterAt] is given) shows the letter of the place it points to in a bubble, with a smooth change from
 * letter to letter. Without [letterAt] it is a plain scroll bar.
 *
 * It is an OVERLAY on top of the list, which uses the full width: the slim thumb sits in the list's right margin, and the
 * thick thumb and the bubble are drawn over the rows only while it is being dragged. Only the thumb itself takes touches (a
 * small area around it); everywhere else the list gets them. It does nothing while idle: no timers, and the thumb position is
 * read in the layout phase, so the list is never recomposed because of it.
 */
@Composable
fun ThumbScrollbar(
    adapter: ThumbAdapter,
    letterAt: ((Int) -> String?)?,
    modifier: Modifier = Modifier,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val thumbHeight = 72.dp
    val density = LocalDensity.current
    val thumbPx = with(density) { thumbHeight.toPx() }
    var trackPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var recentlyScrolled by remember { mutableStateOf(false) }
    val currentAdapter by rememberUpdatedState(adapter)

    // Full opacity while the list moves and for a moment after it; faint otherwise (still there to be grabbed).
    LaunchedEffect(adapter) {
        snapshotFlow { adapter.isScrolling() }.collectLatest { scrolling ->
            if (scrolling) {
                recentlyScrolled = true
            } else {
                delay(900)
                recentlyScrolled = false
            }
        }
    }
    // Latest requested position only: a fast drag never queues up scroll jobs.
    val jumps = remember { Channel<Float>(Channel.CONFLATED) }
    LaunchedEffect(adapter) { for (f in jumps) adapter.jump(f) }

    val active = dragging || recentlyScrolled
    val thumbAlpha = animateFloatAsState(if (active) 1f else 0.45f, tween(250), label = "thumbAlpha")
    val thumbWidth by animateDpAsState(if (dragging) 22.dp else 6.dp, tween(160), label = "thumbWidth")
    val colors = MaterialTheme.colorScheme

    val thumbTop: () -> Int = {
        val f = if (dragging) dragFraction else adapter.progress()
        (f * (trackPx - thumbPx).coerceAtLeast(0f)).roundToInt()
    }

    Box(modifier.padding(top = 4.dp, bottom = bottomInset)) {
        // Measures the track (no touch handling: it never takes touches from the list).
        Box(Modifier.fillMaxSize().onSizeChanged { trackPx = it.height })

        // The area that takes touches follows the thumb: 28 dp wide at the right edge, a little taller than the thumb.
        // The drag is tracked by its movement (not by the finger's position inside this moving area), so it never jitters.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbTop() - 8.dp.roundToPx()) }
                .width(28.dp)
                .height(thumbHeight + 16.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragFraction = currentAdapter.progress()
                            dragging = true
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val travel = (trackPx - thumbPx).coerceAtLeast(1f)
                            dragFraction = (dragFraction + dragAmount / travel).coerceIn(0f, 1f)
                            jumps.trySend(dragFraction)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    )
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
                    .width(thumbWidth)
                    .height(thumbHeight)
                    .graphicsLayer { this.alpha = thumbAlpha.value }
                    .background(colors.primary, RoundedCornerShape(50)),
            )
        }

        val letter = if (dragging) letterAt?.invoke(currentAdapter.indexAt(dragFraction)) else null
        val lastLetter = remember { arrayOf("") }
        if (letter != null) lastLetter[0] = letter
        val origin = TransformOrigin(1f, 0.5f)
        AnimatedVisibility(
            visible = letter != null,
            enter = fadeIn(tween(120)) + scaleIn(tween(170), initialScale = 0.6f, transformOrigin = origin),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.6f, transformOrigin = origin),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(-36.dp.roundToPx(), thumbTop() + (thumbPx / 2f).roundToInt() - 32.dp.roundToPx()) },
        ) {
            Box(
                Modifier.size(64.dp).background(colors.primary, RoundedCornerShape(32.dp, 32.dp, 8.dp, 32.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = letter ?: lastLetter[0],
                    transitionSpec = {
                        (fadeIn(tween(120)) + slideInVertically(tween(140)) { it / 3 }) togetherWith
                            (fadeOut(tween(100)) + slideOutVertically(tween(140)) { -it / 3 })
                    },
                    label = "thumbLetter",
                ) { shown ->
                    Text(shown, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = colors.onPrimary)
                }
            }
        }
    }
}
