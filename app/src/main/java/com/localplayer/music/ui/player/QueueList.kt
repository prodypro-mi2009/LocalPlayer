package com.localplayer.music.ui.player

import com.localplayer.music.ui.theme.artistFont
import com.localplayer.music.ui.theme.songTitleFont
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.localplayer.music.ui.theme.LocalReduceMotion
import com.localplayer.music.ui.theme.rememberHaptics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import com.localplayer.music.ui.theme.Motion
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Stable identity of one queue ENTRY (not of the file): its occurrence id. */
private fun QueueEntry.rowKey(): String = occurrenceId ?: "${mediaId}@$index"

/**
 * The real Media3 playback queue (no copy of it is kept anywhere).
 *
 * Each row: a round "−" button on the left (removes exactly this entry), artwork and text, and a three-line drag
 * handle on the right. Dragging only re-orders a local copy of the list while the finger is down; when it is
 * released ONE `moveMediaItem` is sent to the player, so the playing track is never restarted or seeked and
 * Media3 keeps the current index right even when the moved row is the playing one. Rows are identified by their
 * occurrence id, so the same song queued several times is never mixed up.
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

    // Drag state. [local] is non-null only while a row is being dragged; it is the order shown on screen.
    var local by remember { mutableStateOf<List<QueueEntry>?>(null) }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }

    // Release animation: the row that was just dropped glides from where the finger left it into its slot.
    val scope = rememberCoroutineScope()
    var settlingKey by remember { mutableStateOf<String?>(null) }
    val settle = remember { Animatable(0f) }
    val haptics = rememberHaptics()
    val reduceMotion = LocalReduceMotion.current
    // Finger velocity of the current drag, measured on the ACCUMULATED travel (a moving row's own position is useless).
    val tracker = remember { VelocityTracker() }
    val travelled = remember { floatArrayOf(0f) }

    // The dropped row settles with the finger's speed (a slight bounce only after a fast release).
    fun startSettle(key: String, from: Float, velocity: Float = 0f) {
        if (from == 0f) return
        settlingKey = key
        scope.launch {
            settle.snapTo(from)
            settle.animateTo(0f, Motion.settleSpring(velocity, reduceMotion), initialVelocity = velocity)
            if (settlingKey == key) settlingKey = null
        }
    }

    // When the real queue changes (our own move arriving, or any other change) the preview is dropped.
    LaunchedEffect(queue) {
        local = null
        draggedKey = null
        dragDelta = 0f
    }

    val shown = local ?: queue
    val currentKey = queue.getOrNull(state.queueIndex)?.rowKey()
    // Songs already played sit ABOVE the current one: they are dimmed; the current one gets the accent colour and an
    // equalizer in place of its artwork; the upcoming ones stay normal.
    val currentPos = shown.indexOfFirst { it.rowKey() == currentKey }
    val isPlayingNow = state.isPlaying

    fun swapIfNeeded() {
        val key = draggedKey ?: return
        val list = local ?: return
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.firstOrNull { it.key == key } ?: return
        val center = dragged.offset + dragged.size / 2f + dragDelta
        val target = items.firstOrNull { it.key != key && center >= it.offset && center < it.offset + it.size } ?: return
        val from = list.indexOfFirst { it.rowKey() == key }
        val to = list.indexOfFirst { it.rowKey() == target.key }
        if (from < 0 || to < 0 || from == to) return
        val mutable = list.toMutableList()
        mutable.add(to, mutable.removeAt(from))
        local = mutable
        haptics.tick()
        // The dragged row now sits where the target was; keep it under the finger.
        val newOffset = if (to > from) target.offset + target.size - dragged.size else target.offset
        dragDelta += dragged.offset - newOffset
    }

    // Scrolls the list while a row is held near the top or bottom edge. Driven by display frames (not a timer),
    // so the speed is steady and identical on 60 Hz and 120 Hz screens.
    LaunchedEffect(draggedKey) {
        if (draggedKey == null) return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (isActive) {
            val frame = withFrameNanos { it }
            // The old tick was 16 ms; `ticks` = how many 16 ms ticks this frame covers (about 1.0 at 60 Hz).
            // Clamped so a stalled frame can never cause a big jump.
            val ticks = ((frame - lastFrame) / 16_000_000f).coerceIn(0f, 3f)
            lastFrame = frame
            val key = draggedKey ?: break
            val info = listState.layoutInfo
            val dragged = info.visibleItemsInfo.firstOrNull { it.key == key }
            if (dragged != null) {
                val center = dragged.offset + dragged.size / 2f + dragDelta
                val zone = 96f
                val speed = when {
                    center < info.viewportStartOffset + zone -> -(info.viewportStartOffset + zone - center) / 4f
                    center > info.viewportEndOffset - zone -> (center - (info.viewportEndOffset - zone)) / 4f
                    else -> 0f
                }
                if (speed != 0f) {
                    val consumed = listState.scrollBy(speed * ticks)
                    dragDelta += consumed
                    swapIfNeeded()
                }
            }
        }
    }

    fun finishDrag(velocity: Float) {
        val key = draggedKey
        val list = local
        val residual = dragDelta
        if (key != null && list != null) {
            val from = queue.indexOfFirst { it.rowKey() == key }
            val to = list.indexOfFirst { it.rowKey() == key }
            val occurrence = queue.getOrNull(from)?.occurrenceId
            if (occurrence != null && from >= 0 && to >= 0 && from != to) {
                // The preview stays until the player's queue changes (LaunchedEffect(queue) above).
                viewModel.moveQueueEntry(occurrence, to)
                startSettle(key, residual, velocity)
                draggedKey = null
                dragDelta = 0f
                return
            }
        }
        if (key != null) startSettle(key, residual, velocity)
        local = null
        draggedKey = null
        dragDelta = 0f
    }

    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = barPadding(extra = 8.dp)) {
        itemsIndexed(shown, key = { _, entry -> entry.rowKey() }) { position, entry ->
            val key = entry.rowKey()
            val isCurrent = key == currentKey
            val isDragged = key == draggedKey
            val isSettling = key == settlingKey
            val rowSurface = MaterialTheme.colorScheme.surfaceContainerHigh
            // 0 → 1 while picked up: shadow and background fade in instead of appearing in one frame.
            val lift = animateFloatAsState(
                targetValue = if (isDragged) 1f else 0f,
                animationSpec = tween(Motion.PICKUP_MS, easing = Motion.EaseOut),
                label = "queueRowLift",
            )
            val moveUp = stringResource(R.string.move_up)
            val moveDown = stringResource(R.string.move_down)
            Row(
                modifier = Modifier
                    .then(if (isDragged) Modifier else Modifier.animateItem())
                    .zIndex(if (isDragged || isSettling) 1f else 0f)
                    .graphicsLayer {
                        alpha = if (currentPos >= 0 && position < currentPos && !isDragged) 0.4f else 1f
                        translationY = when {
                            isDragged -> dragDelta
                            isSettling -> settle.value
                            else -> 0f
                        }
                        shadowElevation = 6.dp.toPx() * lift.value
                    }
                    .drawBehind { if (lift.value > 0f) drawRect(rowSurface, alpha = lift.value) }
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable { viewModel.playQueueIndex(queue.indexOfFirst { it.rowKey() == key }) }
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
                    }
                    .padding(start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: round minus button, 48 dp touch target, 24 dp drawing.
                val removeDescription = stringResource(R.string.remove_from_queue, entry.title)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { onRemove(entry) }
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
                if (isCurrent) PlayingBars(playing = isPlayingNow, size = 40.dp) else ArtworkImage(artwork = entry.artwork, size = 40.dp)
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
                // Right: three-line drag handle, 48 dp touch target.
                val reorderDescription = stringResource(R.string.reorder_queue_item, entry.title)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .pointerInput(key) {
                            detectDragGestures(
                                onDragStart = {
                                    tracker.resetTracking()
                                    travelled[0] = 0f
                                    haptics.pickUp()
                                    // Grabbing a row that is still settling: continue from where it visually is.
                                    dragDelta = if (settlingKey == key) settle.value else 0f
                                    settlingKey = null
                                    local = queue
                                    draggedKey = key
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragDelta += amount.y
                                    travelled[0] += amount.y
                                    tracker.addPosition(change.uptimeMillis, Offset(0f, travelled[0]))
                                    swapIfNeeded()
                                },
                                onDragEnd = {
                                    haptics.drop()
                                    finishDrag(tracker.calculateVelocity().y)
                                },
                                onDragCancel = {
                                    draggedKey?.let { startSettle(it, dragDelta) }
                                    local = null
                                    draggedKey = null
                                    dragDelta = 0f
                                },
                            )
                        }
                        .clearAndSetSemantics { contentDescription = reorderDescription },
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
    val transition = rememberInfiniteTransition(label = "playingBars")
    val a by transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(410, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val c by transition.animateFloat(0.2f, 0.9f, infiniteRepeatable(tween(630, easing = LinearEasing), RepeatMode.Reverse), label = "c")
    Canvas(Modifier.size(size)) {
        val barW = this.size.width * 0.16f
        val gap = this.size.width * 0.10f
        val startX = (this.size.width - (barW * 3 + gap * 2)) / 2f
        val bottom = this.size.height * 0.8f
        val heights = if (animate) floatArrayOf(a, b, c) else floatArrayOf(0.55f, 0.9f, 0.4f)
        for (i in 0..2) {
            val h = this.size.height * 0.6f * heights[i]
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + i * (barW + gap), bottom - h),
                size = androidx.compose.ui.geometry.Size(barW, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f),
            )
        }
    }
}
