package com.localplayer.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.music.R
import com.localplayer.music.playback.QueueEntry
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.ArtworkImage
import com.localplayer.music.ui.components.EmptyState
import com.localplayer.music.ui.components.barPadding
import com.localplayer.music.ui.theme.LocalReduceMotion
import com.localplayer.music.ui.theme.Motion
import com.localplayer.music.ui.theme.artistFont
import com.localplayer.music.ui.theme.rememberHaptics
import com.localplayer.music.ui.theme.songTitleFont
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Stable identity of one queue ENTRY (not of the file): its occurrence id. */
private fun QueueEntry.rowKey(): String = occurrenceId ?: "${mediaId}@$index"

private val RowHeight = 64.dp

/** Width of the right-hand strip (the three-line handle) in which a touch starts a drag. */
private val HandleZone = 56.dp

/**
 * The real Media3 playback queue (no copy of it is kept anywhere).
 *
 * Each row: a round "-" button on the left (removes exactly this entry), artwork and text, and a three-line drag
 * handle on the right.
 *
 * Dragging works like this (the same idea as the Music 3.8 app): the touch is followed by ONE gesture that belongs
 * to the whole list, not to a row, so it can never be lost when the list scrolls fast and a row leaves the screen.
 * The picked-up row is drawn as a separate copy above the list and simply follows the finger; the rows between its
 * old and its new place slide by one row. Nothing is reordered while the finger is down. When it is released ONE
 * `moveMediaItem` is sent to the player, so the playing track is never restarted or seeked. Rows are identified by
 * their occurrence id, so the same song queued several times is never mixed up.
 */
@Composable
fun QueueList(
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
    onRemove: (QueueEntry) -> Unit,
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.queueIndex.coerceAtLeast(0))

    if (queue.isEmpty()) {
        EmptyState(stringResource(R.string.queue_empty), modifier)
        return
    }

    val density = LocalDensity.current
    val rowPx = with(density) { RowHeight.toPx() }
    val handlePx = with(density) { HandleZone.toPx() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val reduceMotion = LocalReduceMotion.current

    // Drag state. [draggedKey] is non-null from pick-up until the moved queue arrives (or the drop is a no-op).
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var fromIndex by remember { mutableIntStateOf(-1) }
    var targetIndex by remember { mutableIntStateOf(-1) }
    /** Top of the picked-up row's copy, in the list's own coordinates (follows the finger). */
    var overlayTop by remember { mutableFloatStateOf(0f) }
    var released by remember { mutableStateOf(false) }
    /** True from the drop until the moved queue is on screen: rows must not animate their new places then. */
    var quiet by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val tracker = remember { VelocityTracker() }

    val keys = remember(queue) { queue.map { it.rowKey() } }
    val currentKey = queue.getOrNull(state.queueIndex)?.rowKey()
    val currentPos = keys.indexOf(currentKey)
    val isPlayingNow = state.isPlaying

    fun clearDrag() {
        settleJob?.cancel()
        settleJob = null
        draggedKey = null
        fromIndex = -1
        targetIndex = -1
        released = false
    }

    // The real queue changed (our own move arriving, or any other change): the drag state is dropped and the rows
    // are already in their final places, so for two frames nothing animates.
    LaunchedEffect(keys) {
        clearDrag()
        withFrameNanos { }
        withFrameNanos { }
        quiet = false
    }

    // A move that never arrives must not leave the copy hanging over the list.
    LaunchedEffect(released) {
        if (!released) return@LaunchedEffect
        delay(1500)
        if (released) {
            clearDrag()
            quiet = false
        }
    }

    // The row (slot) under a point of the list, found from the rows that are on screen (all rows are equally tall).
    fun slotAt(y: Float): Int {
        val items = listState.layoutInfo.visibleItemsInfo
        if (items.isEmpty()) return -1
        val hit = items.firstOrNull { y >= it.offset && y < it.offset + it.size }
        return when {
            hit != null -> hit.index
            y < items.first().offset -> items.first().index
            else -> items.last().index
        }
    }

    fun slotTop(index: Int): Float {
        val first = listState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return overlayTop
        return first.offset + (index - first.index) * rowPx
    }

    fun retarget() {
        if (draggedKey == null || released) return
        val slot = slotAt(overlayTop + rowPx / 2f)
        if (slot >= 0 && slot != targetIndex) {
            targetIndex = slot
            haptics.tick()
        }
    }

    fun finishDrag(velocity: Float) {
        val key = draggedKey ?: return
        val from = fromIndex
        val to = targetIndex.coerceIn(0, queue.lastIndex)
        val occurrence = queue.getOrNull(from)?.occurrenceId
        released = true
        val moves = occurrence != null && from >= 0 && from != to
        if (moves) {
            quiet = true
            viewModel.moveQueueEntry(occurrence, to)
        }
        // The copy glides from where the finger left it into its slot.
        val slot = slotTop(if (moves) to else from)
        settleJob?.cancel()
        settleJob = scope.launch {
            Animatable(overlayTop).animateTo(
                slot,
                Motion.settleSpring(velocity, reduceMotion),
                initialVelocity = velocity,
            ) { overlayTop = value }
            if (!moves && draggedKey == key) clearDrag()
        }
    }

    // Scrolls the list while the picked-up row is held near the top or bottom edge. Driven by display frames (not a
    // timer), so the speed is steady and identical on 60 Hz and 120 Hz screens.
    LaunchedEffect(draggedKey) {
        if (draggedKey == null) return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (isActive) {
            val frame = withFrameNanos { it }
            // `ticks` = how many 16 ms ticks this frame covers (about 1.0 at 60 Hz). Clamped against stalled frames.
            val ticks = ((frame - lastFrame) / 16_000_000f).coerceIn(0f, 3f)
            lastFrame = frame
            if (draggedKey == null || released) continue
            val info = listState.layoutInfo
            val center = overlayTop + rowPx / 2f
            val zone = 96f
            val speed = when {
                center < info.viewportStartOffset + zone -> -(info.viewportStartOffset + zone - center) / 4f
                center > info.viewportEndOffset - zone -> (center - (info.viewportEndOffset - zone)) / 4f
                else -> 0f
            }
            if (speed != 0f) {
                listState.scrollBy(speed * ticks)
                retarget()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    // Looked at BEFORE the rows see it, so a drag that starts on a handle never reaches a row's click
                    // or the list's own scrolling. A plain tap is left alone.
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (down.position.x < size.width - handlePx || draggedKey != null) return@awaitEachGesture
                    val row = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { down.position.y >= it.offset && down.position.y < it.offset + it.size }
                        ?: return@awaitEachGesture
                    var moved = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (!change.pressed) return@awaitEachGesture
                        moved += change.position.y - change.previousPosition.y
                        if (abs(moved) > slop) {
                            change.consume()
                            break
                        }
                    }
                    val grab = down.position.y - row.offset
                    tracker.resetTracking()
                    haptics.pickUp()
                    settleJob?.cancel()
                    released = false
                    quiet = false
                    fromIndex = row.index
                    targetIndex = row.index
                    draggedKey = row.key as? String
                    overlayTop = down.position.y + moved - grab
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed || draggedKey == null) {
                            haptics.drop()
                            if (draggedKey != null) finishDrag(tracker.calculateVelocity().y)
                            break
                        }
                        change.consume()
                        val maxTop = (listState.layoutInfo.viewportEndOffset - rowPx).coerceAtLeast(0f)
                        overlayTop = (change.position.y - grab).coerceIn(0f, maxTop)
                        tracker.addPosition(change.uptimeMillis, Offset(0f, overlayTop))
                        retarget()
                    }
                }
            },
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = barPadding(extra = 8.dp)) {
            itemsIndexed(queue, key = { _, entry -> entry.rowKey() }) { position, entry ->
                val key = entry.rowKey()
                val isDragged = key == draggedKey
                // Rows between the old and the new place of the picked-up row slide by one row.
                val shiftTarget = when {
                    draggedKey == null || fromIndex < 0 -> 0f
                    targetIndex > fromIndex && position > fromIndex && position <= targetIndex -> -rowPx
                    targetIndex < fromIndex && position < fromIndex && position >= targetIndex -> rowPx
                    else -> 0f
                }
                val shift by animateFloatAsState(
                    targetValue = shiftTarget,
                    animationSpec = if (draggedKey == null) snap() else spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.9f),
                    label = "queueRowShift",
                )
                val moveUp = stringResource(R.string.move_up)
                val moveDown = stringResource(R.string.move_down)
                Box(
                    modifier = Modifier
                        .then(if (quiet || draggedKey != null) Modifier else Modifier.animateItem())
                        .graphicsLayer {
                            translationY = shift
                            alpha = if (isDragged) 0f else 1f
                        }
                        .semantics {
                            // Accessibility alternative to dragging.
                            customActions = listOf(
                                CustomAccessibilityAction(moveUp) {
                                    val i = queue.indexOfFirst { it.rowKey() == key }
                                    if (i > 0 && entry.occurrenceId != null) viewModel.moveQueueEntry(entry.occurrenceId, i - 1)
                                    i > 0
                                },
                                CustomAccessibilityAction(moveDown) {
                                    val i = queue.indexOfFirst { it.rowKey() == key }
                                    if (i in 0 until queue.lastIndex && entry.occurrenceId != null) {
                                        viewModel.moveQueueEntry(entry.occurrenceId, i + 1)
                                    }
                                    i in 0 until queue.lastIndex
                                },
                            )
                        },
                ) {
                    QueueRow(
                        entry = entry,
                        isCurrent = key == currentKey,
                        dimmed = currentPos >= 0 && position < currentPos,
                        playing = isPlayingNow,
                        onClick = { viewModel.playQueueIndex(queue.indexOfFirst { it.rowKey() == key }) },
                        onRemove = { onRemove(entry) },
                    )
                }
            }
        }
        // The picked-up row: its own copy above the list, so it stays under the finger however far the list scrolls.
        val dragged = draggedKey?.let { k -> queue.firstOrNull { it.rowKey() == k } }
        if (dragged != null) {
            val rowSurface = MaterialTheme.colorScheme.surfaceContainerHigh
            val lift = animateFloatAsState(
                targetValue = 1f,
                animationSpec = tween(Motion.PICKUP_MS, easing = Motion.EaseOut),
                label = "queueRowLift",
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, overlayTop.roundToInt()) }
                    .zIndex(1f)
                    .fillMaxWidth()
                    .graphicsLayer { shadowElevation = 6.dp.toPx() * lift.value }
                    .background(rowSurface.copy(alpha = lift.value)),
            ) {
                QueueRow(
                    entry = dragged,
                    isCurrent = dragged.rowKey() == currentKey,
                    dimmed = false,
                    playing = isPlayingNow,
                    onClick = null,
                    onRemove = null,
                )
            }
        }
    }
}

/** One queue row (64 dp). With [onClick] / [onRemove] null it is the inert copy that follows the finger. */
@Composable
private fun QueueRow(
    entry: QueueEntry,
    isCurrent: Boolean,
    dimmed: Boolean,
    playing: Boolean,
    onClick: (() -> Unit)?,
    onRemove: (() -> Unit)?,
) {
    val reorderDescription = stringResource(R.string.reorder_queue_item, entry.title)
    Row(
        modifier = Modifier
            .graphicsLayer { alpha = if (dimmed) 0.4f else 1f }
            .fillMaxWidth()
            .height(RowHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: round minus button, 48 dp touch target, 24 dp drawing.
        val removeDescription = stringResource(R.string.remove_from_queue, entry.title)
        Box(
            modifier = Modifier
                .size(48.dp)
                .then(
                    if (onRemove != null) {
                        Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { onRemove() }
                    } else {
                        Modifier
                    },
                )
                .semantics { contentDescription = removeDescription },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(
                        1.5.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isCurrent) 0.4f else 1f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(11.dp)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
        }
        if (isCurrent) PlayingBars(playing = playing, size = 40.dp) else ArtworkImage(artwork = entry.artwork, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge.songTitleFont(),
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.artist,
                style = MaterialTheme.typography.bodySmall.artistFont(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Right: three-line drag handle, 48 dp touch target (the drag itself is handled by the whole list).
        Box(
            modifier = Modifier.size(48.dp).clearAndSetSemantics { contentDescription = reorderDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                AppIcons.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * The "now playing" mark of the current queue row: three accent-coloured bars that dance while the song plays and
 * stand still (a fixed shape) when it is paused or when animations are off. Values are read in the draw scope only.
 */
@Composable
private fun PlayingBars(playing: Boolean, size: androidx.compose.ui.unit.Dp) {
    val color = MaterialTheme.colorScheme.primary
    val animate = playing && !LocalReduceMotion.current
    // The infinite transition exists ONLY while the bars dance: when paused (or with animations off) nothing wakes
    // up every frame any more.
    val dance: List<State<Float>>? = if (animate) {
        val transition = rememberInfiniteTransition(label = "playingBars")
        listOf(
            transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "a"),
            transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(410, easing = LinearEasing), RepeatMode.Reverse), label = "b"),
            transition.animateFloat(0.2f, 0.9f, infiniteRepeatable(tween(630, easing = LinearEasing), RepeatMode.Reverse), label = "c"),
        )
    } else {
        null
    }
    Canvas(Modifier.size(size)) {
        val barW = this.size.width * 0.16f
        val gap = this.size.width * 0.10f
        val startX = (this.size.width - (barW * 3 + gap * 2)) / 2f
        val bottom = this.size.height * 0.8f
        for (i in 0..2) {
            val fraction = dance?.get(i)?.value ?: STILL_BARS[i]
            val h = this.size.height * 0.6f * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + i * (barW + gap), bottom - h),
                size = androidx.compose.ui.geometry.Size(barW, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f),
            )
        }
    }
}

private val STILL_BARS = floatArrayOf(0.55f, 0.9f, 0.4f)
