package com.localplayer.music.ui.player

import com.localplayer.music.ui.theme.onColorFor
import com.localplayer.music.ui.theme.controlColor
import com.localplayer.music.data.personalization.ControlColorGroup
import com.localplayer.music.ui.theme.albumGenreFont
import com.localplayer.music.ui.theme.artistFont
import com.localplayer.music.ui.theme.songTitleFont
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import com.localplayer.music.ui.theme.SheetMorph
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import com.localplayer.music.R
import com.localplayer.music.playback.NowPlaying
import com.localplayer.music.playback.SleepTimerState
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.ArtworkImage
import androidx.compose.ui.draw.drawBehind
import com.localplayer.music.ui.theme.AppColors
import com.localplayer.music.ui.theme.LocalReduceMotion
import com.localplayer.music.ui.theme.Motion
import com.localplayer.music.ui.theme.SheetPhysics
import com.localplayer.music.ui.theme.pressScale
import com.localplayer.music.ui.theme.rememberHaptics
import com.localplayer.music.ui.theme.nowPlayingVerticalBrush
import com.localplayer.music.util.formatDuration
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import com.localplayer.music.ui.theme.QueueSheetMotion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.delay

/**
 * Full-screen Now Playing.
 *
 * Why it is stable: the container is a fixed full-screen Box (it never measures to its content), the
 * layout below it has constant heights, and ONLY the artwork + title block is animated on a track change
 * (horizontal slide + cross-fade, size changes snapped). The old bottom sheet wrapped everything in
 * `key(mediaId)`, which threw the whole content away for a frame on every Next/Previous; a wrap-content
 * sheet then shrank and grew back (the downward squeeze and spring).
 */
/** Who owns the current vertical drag on Now Playing: decided by its first movement, kept until release. */
private class GestureOwner {
    var who = NONE

    companion object {
        const val NONE = 0
        const val QUEUE = 1
        const val PLAYER = 2
    }
}

/** Remembers which way the queue index last moved, so the track-change slide can follow it. */
private class TrackMotion {
    var lastIndex = -1
    var forward = true
}

@Composable
fun NowPlayingScreen(
    miniTopPx: () -> Float,
    miniBottomPx: () -> Float,
    viewModel: LibraryViewModel,
    tone: Color?,
    blurMode: Boolean,
    blurred: androidx.compose.ui.graphics.ImageBitmap?,
    sheet: PlayerSheetState,
    onDismiss: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onMenuAction: (NowPlayingAction, NowPlaying) -> Unit,
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val jamProposal by viewModel.jamProposal.collectAsStateWithLifecycle()
    // Keep showing the last track while the screen slides out after the queue was cleared.
    var lastNow by remember { mutableStateOf<NowPlaying?>(null) }
    state.nowPlaying?.let { lastNow = it }
    val now = state.nowPlaying ?: lastNow ?: return

    // The "Next up" queue sheet: a second instance of the same single-float sheet state. Back closes it first.
    var queueWasOpen by rememberSaveable { mutableStateOf(false) }
    val queueSheet = rememberPlayerSheetState(initiallyOpen = queueWasOpen)
    LaunchedEffect(queueSheet) { snapshotFlow { queueSheet.isOpen }.collect { queueWasOpen = it } }
    BackHandler { if (queueSheet.isOpen) queueSheet.close() else onDismiss() }
    LaunchedEffect(state.nowPlaying == null) { if (state.nowPlaying == null) onDismiss() }

    // Full-strength tone; the alpha curve (and the fade to the plain background) is [artworkVerticalBrush].
    val glow = animateColorAsState(
        targetValue = tone ?: com.localplayer.music.ui.theme.fallbackTint(),
        animationSpec = Motion.AmbientTint,
        label = "nowPlayingGlow",
    )
    val position = rememberPlaybackPosition(state.isPlaying, now.mediaId, viewModel::currentPositionMs)
    var scrub by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1L)
    // Coarse (once a second): the time label and the accessibility value. The bar itself uses [smooth].
    val fraction = scrub ?: (position.longValue.toFloat() / duration).coerceIn(0f, 1f)
    // Read only inside the bar's draw scope (see rememberSmoothFraction).
    val smooth = rememberSmoothFraction(state.isPlaying, now.mediaId, state.durationMs, viewModel::currentPositionMs)
    val reduceMotion = LocalReduceMotion.current
    val sleepState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    var sleepDialog by remember { mutableStateOf(false) }
    val hideChip by viewModel.settings.hideFileInfoChip.collectAsStateWithLifecycle()
    val specs by produceState<String?>(initialValue = null, now.uri, hideChip) {
        value = if (hideChip) null else now.uri?.let { viewModel.audioSpecsText(it) }
    }

    // Pull down to dismiss. The sheet follows the finger 1:1 (direct manipulation) through the shared
    // PlayerSheetState; on release it either leaves (decided on the PROJECTED position) or springs back, and the
    // spring continues with the finger's velocity. Works with the inner verticalScroll via nested scroll.
    val density = LocalDensity.current
    val minFlingOffsetPx = with(density) { 48.dp.toPx() }
    // One owner per gesture: the direction of the FIRST movement decides for its whole life. Upward (with the player
    // at rest) -> the queue sheet; downward -> the existing pull-down of the player. A queue sheet that is mid-travel
    // (caught by a finger) always owns the gesture. If the finger reverses, the owner keeps the gesture.
    val owner = remember { GestureOwner() }
    val pullConnection = remember(sheet, queueSheet) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                if (owner.who == GestureOwner.NONE) {
                    owner.who = when {
                        queueSheet.currentOffset < queueSheet.extentPx -> GestureOwner.QUEUE
                        available.y < 0f && sheet.currentOffset <= 0f -> GestureOwner.QUEUE
                        else -> GestureOwner.PLAYER
                    }
                }
                if (owner.who == GestureOwner.QUEUE) {
                    // Finger moving back down while the queue is partly open: give that movement to the sheet first.
                    val current = queueSheet.currentOffset
                    if (available.y > 0f && current < queueSheet.extentPx) {
                        val consumed = minOf(available.y, queueSheet.extentPx - current)
                        queueSheet.dragBy(consumed)
                        return Offset(0f, consumed)
                    }
                    return Offset.Zero
                }
                // Finger moving back up while the sheet is pulled down (or partly open): give that movement to the sheet first.
                val current = sheet.currentOffset
                if (current <= 0f || available.y >= 0f) return Offset.Zero
                val consumed = maxOf(available.y, -current)
                sheet.dragBy(consumed)
                return Offset(0f, consumed)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (owner.who == GestureOwner.QUEUE) {
                    // The inner scroller could not use this upward movement: it moves the queue sheet (1:1).
                    if (available.y >= 0f) return Offset.Zero
                    queueSheet.dragBy(available.y)
                    return Offset(0f, available.y)
                }
                // Content cannot scroll further up: the leftover downward movement pulls the sheet.
                if (available.y <= 0f) return Offset.Zero
                sheet.dragBy(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val who = owner.who
                owner.who = GestureOwner.NONE
                if (who == GestureOwner.QUEUE) {
                    val current = queueSheet.currentOffset
                    if (current >= queueSheet.extentPx) {
                        queueSheet.endDrag()
                        return Velocity.Zero
                    }
                    // Continues from the CURRENT offset with the finger's own velocity (positive y = moving down).
                    queueSheet.settle(
                        openedPx = queueSheet.extentPx - current,
                        upwardVelocityPxPerSec = -available.y,
                        minFlingOpenedPx = minFlingOffsetPx,
                    )
                    return available
                }
                val dragY = sheet.currentOffset
                if (dragY <= 0f) {
                    sheet.endDrag()
                    return Velocity.Zero
                }
                // The animation starts from the CURRENT offset and continues with the finger's velocity.
                if (SheetPhysics.shouldDismiss(dragY, available.y, sheet.extentPx, minFlingOffsetPx)) {
                    sheet.close(available.y)
                } else {
                    sheet.open(available.y)
                }
                return available
            }
        }
    }

    // One light haptic tick when a finger-driven opening crosses 25 %.
    val queueHaptics = rememberHaptics()
    LaunchedEffect(queueSheet) {
        snapshotFlow {
            QueueSheetMotion.crossedHapticPoint(QueueSheetMotion.progress(queueSheet.offsetPx.value, queueSheet.extentPx))
        }.distinctUntilChanged().drop(1).collect { if (queueSheet.isDragging) queueHaptics.threshold() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Draw phase only: no recomposition per frame. The sheet is the mini player grown: its window starts
                // as the mini player's rectangle and expands (see SheetMorph); it fades in over the mini player.
                val h = size.height
                val p = SheetMorph.progress(sheet.offsetPx.value, sheet.extentPx)
                val f = SheetMorph.frame(
                    progress = p,
                    height = h,
                    miniTop = miniTopPx().takeIf { it > 0f } ?: (h - 150.dp.toPx()),
                    miniBottom = miniBottomPx().takeIf { it > 0f } ?: (h - 90.dp.toPx()),
                    insetPx = 8.dp.toPx(),
                    cornerPx = 36.dp.toPx(),
                )
                translationY = f.top
                shape = MorphShape(
                    left = f.inset,
                    right = size.width - f.inset,
                    height = (f.bottom - f.top).coerceAtLeast(0f),
                    radius = f.corner,
                )
                clip = true
                alpha = SheetMorph.fade(p) * sheet.contentAlpha.value
            }
            .background(MaterialTheme.colorScheme.background)
            .drawBehind { if (!blurMode) drawRect(nowPlayingVerticalBrush(glow.value)) }
            // A finger landing on a moving sheet freezes it where it is (no jump); if it lifts without moving the
            // sheet, the sheet settles from there. Initial pass: nothing is consumed. Same for the queue sheet.
            .pointerInput(sheet, queueSheet) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    owner.who = GestureOwner.NONE
                    sheet.grab()
                    queueSheet.grab()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.pressed })
                    sheet.releaseGrab()
                    queueSheet.releaseGrab()
                }
            }
            // Swallow touches so nothing behind the full-screen player can be tapped.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        // "Blurred cover" background (only when the user chose it): the pre-blurred picture of this song under a veil.
        if (blurMode) com.localplayer.music.ui.components.BlurredCoverBackground(
            image = blurred,
            veil = com.localplayer.music.ui.components.NOW_PLAYING_BLUR_VEIL,
        )
        // The player content: the only thing under the pull-down connection. It scales down a touch (depth cue)
        // as the queue sheet rises; read in the draw phase from the queue sheet's single float.
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(pullConnection)
                .graphicsLayer {
                    if (!reduceMotion) {
                        val scale = QueueSheetMotion.contentScale(
                            QueueSheetMotion.progress(queueSheet.offsetPx.value, queueSheet.extentPx),
                        )
                        scaleX = scale
                        scaleY = scale
                    }
                },
        ) {
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val minHeight = maxHeight
            val pageWidth = maxWidth
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top controls: collapse, title, queue. (No global Settings button here.)
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(AppIcons.ArrowDown, contentDescription = stringResource(R.string.collapse_player))
                    }
                    Text(
                        stringResource(R.string.now_playing),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    var outputOpen by remember { mutableStateOf(false) }
                    if (outputOpen) com.localplayer.music.ui.components.OutputSheet(onDismiss = { outputOpen = false })
                    IconButton(
                        onClick = { outputOpen = true },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(AppIcons.Cast, contentDescription = stringResource(R.string.audio_output))
                    }
                    IconButton(onClick = { queueSheet.open() }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            AppIcons.Queue,
                            contentDescription = "${stringResource(R.string.queue)} (${state.queueSize})",
                        )
                    }
                }

                // OPT-IN lyrics line (Settings > Lyrics), one line directly above the progress bar. Its slot must not make the
                // page taller: the title row gives up its spare 12 dp and the artwork the rest (20 dp), so everything below the
                // bar stays exactly where it is and the page never starts to scroll because of it.
                val lyricsInPlayer by viewModel.settings.lyricsInPlayer.collectAsStateWithLifecycle()
                val lyricsSlotHeight = 32.dp
                val titleRowMin = if (lyricsInPlayer) 76.dp else 88.dp
                val artworkWidth = pageWidth - 48.dp - (if (lyricsInPlayer) lyricsSlotHeight - 12.dp else 0.dp)

                // Next = content moves left; Previous = content moves right. Derived from the queue index, which
                // arrives in the same state emission as the new track, so it is correct when the transition starts.
                val trackMotion = remember { TrackMotion() }
                if (state.queueIndex != trackMotion.lastIndex) {
                    trackMotion.forward = state.queueIndex >= trackMotion.lastIndex
                    trackMotion.lastIndex = state.queueIndex
                }
                // Cover carousel: the covers of the whole queue side by side. The cover follows the finger, the song changes
                // when the swipe has settled, and Next / Previous (or a queue tap) slide it with a soft spring.
                Spacer(Modifier.height(8.dp))
                CoverCarousel(
                    viewModel = viewModel,
                    queueIndex = state.queueIndex,
                    queueSize = state.queueSize,
                    fallback = now,
                    width = artworkWidth,
                    reduceMotion = reduceMotion,
                    onClick = onOpenLyrics,
                )
                Spacer(Modifier.height(20.dp))
                // Animated block: the title, artist and buttons of the current track.
                AnimatedContent(
                    targetState = now,
                    contentKey = { it.mediaId },
                    transitionSpec = {
                        Motion.trackEnter(trackMotion.forward, reduceMotion) togetherWith
                            Motion.trackExit(trackMotion.forward, reduceMotion) using
                            SizeTransform(clip = false) { _, _ -> snap() }
                    },
                    label = "nowPlayingTrack",
                ) { track ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth().heightIn(min = titleRowMin), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                val labelColor = controlColor(ControlColorGroup.NOW_PLAYING_TEXT)
                                Text(
                                    track.title,
                                    style = (if (hideChip) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge).songTitleFont(),
                                    color = labelColor ?: Color.Unspecified,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                // A tap on the artist or the album opens it (the player closes first).
                                Text(
                                    track.artist,
                                    style = (if (hideChip) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge).artistFont(),
                                    color = labelColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { onMenuAction(NowPlayingAction.GoToArtist, track) },
                                )
                                Text(
                                    track.album,
                                    style = (if (hideChip) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall).albumGenreFont(),
                                    color = labelColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { onMenuAction(NowPlayingAction.GoToAlbum, track) },
                                )
                            }
                            FavoriteButton(viewModel, track.uri)
                            NowPlayingOverflowMenu(onAction = { onMenuAction(it, track) })
                        }
                    }
                }

                // OPT-IN (Settings > Lyrics): the current line of synchronized lyrics, one line in a fixed-height slot above the progress bar.
                if (lyricsInPlayer) {
                    val lyricsFill by viewModel.settings.lyricsFill.collectAsStateWithLifecycle()
                    com.localplayer.music.ui.components.NowPlayingLyricLine(
                        viewModel = viewModel,
                        trackId = now.mediaId.toLongOrNull(),
                        isPlaying = state.isPlaying,
                        fill = lyricsFill,
                        modifier = Modifier.height(lyricsSlotHeight),
                    )
                }
                WaveSeekBar(
                    fraction = { smooth.floatValue },
                    semanticFraction = fraction,
                    onScrub = { scrub = it },
                    onSeek = { f ->
                        val target = (f * duration).toLong()
                        viewModel.seekTo(target)
                        position.longValue = target
                        // A seek while paused must show immediately (no tick will move the bar).
                        smooth.floatValue = f
                        scrub = null
                    },
                    description = stringResource(R.string.wave_seek_bar),
                    animate = state.isPlaying,
                    activeColor = tone ?: MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                )
                val timeColor = controlColor(ControlColorGroup.NOW_PLAYING_TEXT) ?: Color.Unspecified
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration((fraction * duration).toLong()), style = MaterialTheme.typography.labelMedium, color = timeColor)
                    Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium, color = timeColor)
                }
                // Fixed-height slot, so the chip appearing later never moves the controls. With the chip switched off the
                // slot is not left empty: the text above is bigger and the space goes to the controls instead.
                Box(Modifier.fillMaxWidth().heightIn(min = if (hideChip) 28.dp else 44.dp), contentAlignment = Alignment.Center) {
                    specs?.let {
                        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = controlColor(ControlColorGroup.NOW_PLAYING_TEXT, MaterialTheme.colorScheme.surfaceContainerHigh)
                                    ?: Color.Unspecified,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                // Three pill buttons in one row (previous / play-pause / next). The pressed button widens while its
                // neighbours give way, and play morphs between a rounded rectangle (playing) and a pill (paused).
                TransportButtons(
                    isPlaying = state.isPlaying,
                    hasNext = state.hasNext,
                    onPrevious = viewModel::previous,
                    onPlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::next,
                    previousVotes = jamProposal?.takeIf { it.action == com.localplayer.music.jam.JamAction.PREVIOUS }?.yes?.size ?: 0,
                    nextVotes = jamProposal?.takeIf { it.action == com.localplayer.music.jam.JamAction.NEXT }?.yes?.size ?: 0,
                )
                Spacer(Modifier.height(20.dp))

                // Segmented actions: Shuffle / Repeat / Sleep timer / Lyrics, icon-only segments in one dark pill.
                val segmentCount = 4
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Segment(
                        icon = AppIcons.Shuffle,
                        label = stringResource(R.string.shuffle),
                        active = state.shuffleEnabled,
                        onClick = viewModel::toggleShuffle,
                        position = 0, count = segmentCount,
                        modifier = Modifier.weight(1f),
                    )
                    Segment(
                        icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) AppIcons.RepeatOne else AppIcons.Repeat,
                        label = stringResource(
                            when (state.repeatMode) {
                                Player.REPEAT_MODE_ONE -> R.string.repeat_one
                                Player.REPEAT_MODE_ALL -> R.string.repeat_all
                                else -> R.string.repeat_off
                            },
                        ),
                        active = state.repeatMode != Player.REPEAT_MODE_OFF,
                        onClick = viewModel::cycleRepeat,
                        position = 1, count = segmentCount,
                        modifier = Modifier.weight(1f),
                    )
                    Segment(
                        icon = AppIcons.Timer,
                        label = sleepLabel(sleepState),
                        active = sleepState != SleepTimerState.Off,
                        showLabel = sleepState != SleepTimerState.Off,
                        onClick = { sleepDialog = true },
                        position = 2, count = segmentCount,
                        modifier = Modifier.weight(1f),
                    )
                    Segment(
                        icon = AppIcons.Lyrics,
                        label = stringResource(R.string.lyrics),
                        active = false,
                        onClick = onOpenLyrics,
                        position = 3, count = segmentCount,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        }

        // Scrim over the player (alpha = 0.45 * progress) and the queue sheet itself. Both exist only while any part
        // of the sheet is on screen: closed, they cost nothing.
        if (queueSheet.isComposed) {
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val p = QueueSheetMotion.progress(queueSheet.offsetPx.value, queueSheet.extentPx)
                    drawRect(Color.Black, alpha = QueueSheetMotion.scrimAlpha(p))
                },
            )
            QueueSheetLayer(viewModel = viewModel, queueSheet = queueSheet)
        }
    }

    if (sleepDialog) {
        SleepTimerDialog(viewModel = viewModel, state = sleepState, onDismiss = { sleepDialog = false })
    }
}

/** Pill ends are fully round, the corners between segments are small: one connected group of buttons. */
private fun segmentShape(position: Int, count: Int): RoundedCornerShape {
    val outer = 28.dp
    val inner = 8.dp
    return RoundedCornerShape(
        topStart = if (position == 0) outer else inner,
        bottomStart = if (position == 0) outer else inner,
        topEnd = if (position == count - 1) outer else inner,
        bottomEnd = if (position == count - 1) outer else inner,
    )
}

@Composable
private fun Segment(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    position: Int,
    count: Int,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
) {
    // The user's colour for this button group (if any) is used for the icon; "on" is then shown by a bold
    // label / dot as well, because colour alone must not be the only sign.
    val scheme = MaterialTheme.colorScheme
    // ON is shown three ways at once (never colour alone): a filled tonal container, a rounder "pill" shape and
    // a small dot under the icon. OFF stays a quiet dark segment.
    val reduce = com.localplayer.music.ui.theme.LocalReduceMotion.current
    val container = animateColorAsState(
        if (active) scheme.primaryContainer else scheme.surfaceContainerHigh,
        animationSpec = Motion.AmbientTint,
        label = "segmentContainer",
    ).value
    val segmentColor = scheme.surfaceContainerHigh
    val custom = controlColor(ControlColorGroup.PLAYBACK_OPTIONS, segmentColor)
    val customText = controlColor(ControlColorGroup.NOW_PLAYING_TEXT, segmentColor)
    val tint = custom ?: if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    val labelTint = customText ?: tint
    val outer = 28.dp
    val inner = animateDpAsState(if (active) 28.dp else 8.dp, animationSpec = if (reduce) snap() else androidx.compose.animation.core.spring(dampingRatio = 0.65f, stiffness = 380f), label = "segmentCorner").value
    val shape = RoundedCornerShape(
        topStart = if (position == 0) outer else inner,
        bottomStart = if (position == 0) outer else inner,
        topEnd = if (position == count - 1) outer else inner,
        bottomEnd = if (position == count - 1) outer else inner,
    )
    val source = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .height(56.dp)
            .pressScale(source)
            .clip(shape)
            .background(container)
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = if (showLabel) null else label, tint = tint)
        if (active && !showLabel) {
            Box(Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape).background(tint))
        }
        if (showLabel) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (active && custom != null) androidx.compose.ui.text.font.FontWeight.Bold else null,
                color = labelTint,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Previous / play-pause / next as three pills that share one row. Pressing one makes it wider (about +36 %) while
 * the other two shrink (about -19 %), with the press spring; the total width stays the same. The play button is a
 * rounded rectangle while playing and a full pill while paused (animated corner radius).
 */
@Composable
private fun TransportButtons(
    isPlaying: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    /** Jam: number of yes votes for "previous" / "next" while such a vote is open (0 = none, nothing is shown). */
    previousVotes: Int = 0,
    nextVotes: Int = 0,
) {
    val reduceMotion = LocalReduceMotion.current
    val previousSource = remember { MutableInteractionSource() }
    val playSource = remember { MutableInteractionSource() }
    val nextSource = remember(hasNext) { MutableInteractionSource() }
    // The "pressed" state of each button is tracked by watching the finger itself (see TransportPill), NOT by the
    // click source: a press that was cut short (sheet drag taking over, button disabled, many quick taps) can leave
    // the click source "pressed" forever and keep a button wide. Here a press always ends when the finger lifts.
    var previousPressed by remember { mutableStateOf(false) }
    var playPressed by remember { mutableStateOf(false) }
    var nextPressedRaw by remember(hasNext) { mutableStateOf(false) }
    val nextPressed = nextPressedRaw && hasNext
    val anyPressed = previousPressed || playPressed || nextPressed

    @Composable
    fun weightFor(pressed: Boolean): Float {
        val target = when {
            reduceMotion || !anyPressed -> 1f
            pressed -> 1.36f
            else -> 0.81f
        }
        return animateFloatAsState(target, Motion.PressSpring, label = "transportWeight").value
    }

    val skipContainer = MaterialTheme.colorScheme.primaryContainer
    val skipTint = controlColor(ControlColorGroup.PREV_NEXT, skipContainer) ?: MaterialTheme.colorScheme.onPrimaryContainer
    val playCustom = controlColor(ControlColorGroup.PLAY_PAUSE)
    val playContainer = playCustom ?: MaterialTheme.colorScheme.tertiaryContainer
    val playTint = if (playCustom != null) onColorFor(playCustom) else MaterialTheme.colorScheme.onTertiaryContainer
    val playCorner = animateFloatAsState(
        targetValue = if (isPlaying) 28f else 50f,
        animationSpec = if (reduceMotion) snap() else Motion.PressSpring,
        label = "playCorner",
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportPill(
            icon = AppIcons.SkipPrevious,
            description = stringResource(R.string.previous_track),
            container = skipContainer,
            tint = skipTint,
            shape = CircleShape,
            enabled = true,
            source = previousSource,
            onPressedChange = { previousPressed = it },
            onClick = onPrevious,
            modifier = Modifier.weight(weightFor(previousPressed)),
            badge = previousVotes,
        )
        TransportPill(
            icon = if (isPlaying) AppIcons.Pause else AppIcons.Play,
            description = stringResource(if (isPlaying) R.string.pause else R.string.play),
            container = playContainer,
            tint = playTint,
            shape = RoundedCornerShape(percent = playCorner.value.roundToInt().coerceIn(0, 50)),
            enabled = true,
            source = playSource,
            onPressedChange = { playPressed = it },
            onClick = onPlayPause,
            modifier = Modifier.weight(weightFor(playPressed)),
            iconSize = 36.dp,
        )
        TransportPill(
            icon = AppIcons.SkipNext,
            description = stringResource(R.string.next_track),
            container = skipContainer,
            tint = skipTint,
            shape = CircleShape,
            enabled = hasNext,
            source = nextSource,
            onPressedChange = { nextPressedRaw = it },
            onClick = onNext,
            modifier = Modifier.weight(weightFor(nextPressed)),
            badge = nextVotes,
        )
    }
}

@Composable
private fun TransportPill(
    icon: ImageVector,
    description: String,
    container: Color,
    tint: Color,
    shape: androidx.compose.ui.graphics.Shape,
    enabled: Boolean,
    source: MutableInteractionSource,
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 30.dp,
    /** Jam: the number of yes votes shown under the icon (0 = nothing). */
    badge: Int = 0,
) {
    val pressedCallback by androidx.compose.runtime.rememberUpdatedState(onPressedChange)
    Box(
        modifier = modifier
            .height(72.dp)
            // Watches the finger only (never consumes anything). The press always ends: finger up, the gesture
            // being cancelled, the button leaving the screen, or a 3 s safety limit.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    pressedCallback(true)
                    try {
                        withTimeoutOrNull(3_000L) {
                            waitForUpOrCancellation(PointerEventPass.Initial)
                        }
                    } finally {
                        pressedCallback(false)
                    }
                }
            }
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .clip(shape)
            .background(container)
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        if (badge > 0) {
            Text(
                badge.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            )
        }
    }
}

/**
 * Heart. Filled = favourite, outline = not. The colour is ALWAYS [AppColors.FavoriteRed] (#FF1744) in both states:
 * it does not follow the dynamic colours, the artwork tone or any user colour setting. Large icon inside a 56 dp
 * touch target. The state lives in Room (app data), never in the audio file; the Favorites lists observe the same
 * table, so a tap shows up there immediately.
 */
@Composable
fun FavoriteButton(viewModel: LibraryViewModel, trackUri: String?) {
    if (trackUri == null) return
    val favorite by remember(trackUri) { viewModel.isFavorite(trackUri) }.collectAsStateWithLifecycle(initialValue = false)
    val haptics = rememberHaptics()
    IconButton(
        onClick = {
            haptics.toggle(!favorite)
            viewModel.toggleFavorite(trackUri)
        },
        modifier = Modifier.size(56.dp),
    ) {
        Icon(
            imageVector = if (favorite) AppIcons.Heart else AppIcons.HeartOutline,
            contentDescription = stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite),
            tint = AppColors.FavoriteRed,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
internal fun sleepLabel(state: SleepTimerState): String {
    val lifecycleOwner = LocalLifecycleOwner.current
    val remaining by produceState(initialValue = 0L, state, lifecycleOwner) {
        if (state is SleepTimerState.Counting) {
            // Ticks only while the screen is visible (STARTED): nothing wakes up every second in the background.
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    value = (state.endsAtElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                    delay(1_000)
                }
            }
        }
    }
    return when (state) {
        SleepTimerState.Off -> stringResource(R.string.sleep_timer)
        SleepTimerState.EndOfTrack -> stringResource(R.string.sleep_end_of_song)
        SleepTimerState.EndOfQueue -> stringResource(R.string.sleep_end_of_queue)
        is SleepTimerState.Counting -> formatDuration(remaining)
    }
}

@Composable
internal fun SleepTimerDialog(viewModel: LibraryViewModel, state: SleepTimerState, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_timer)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Four quick choices in a 2 x 2 grid; any other length goes through the custom field below.
                listOf(listOf(15, 30), listOf(45, 60)).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { minutes ->
                            FilledTonalButton(
                                onClick = {
                                    viewModel.startSleepTimer(minutes)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text(pluralStringResource(R.plurals.sleep_minutes, minutes, minutes), maxLines = 1) }
                        }
                    }
                }
                FilledTonalButton(
                    onClick = {
                        viewModel.startSleepTimerEndOfTrack()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.sleep_end_of_song)) }
                FilledTonalButton(
                    onClick = {
                        viewModel.startSleepTimerEndOfQueue()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.sleep_end_of_queue)) }
                // Any other length: a small pill field and a small "Start" button on one line.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.weight(1f).height(40.dp),
                    ) {
                        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                            if (custom.isEmpty()) {
                                Text(
                                    stringResource(R.string.sleep_custom_minutes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            BasicTextField(
                                value = custom,
                                onValueChange = { custom = it.filter(Char::isDigit).take(4) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = {
                            custom.toIntOrNull()?.takeIf { it > 0 }?.let {
                                viewModel.startSleepTimer(it)
                                onDismiss()
                            }
                        },
                        enabled = (custom.toIntOrNull() ?: 0) > 0,
                        modifier = Modifier.height(40.dp),
                    ) { Text(stringResource(R.string.start)) }
                }
            }
        },
        confirmButton = {
            if (state != SleepTimerState.Off) {
                TextButton(
                    onClick = {
                        viewModel.cancelSleepTimer()
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.sleep_cancel)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The actions of the Now Playing overflow menu (exactly these six, in this order). */
enum class NowPlayingAction { GoToAlbum, GoToArtist, HideSong, EditTags, EditLyrics, Info }

/** Three vertical dots next to the heart. Every entry is wired to a real screen or action by the caller. */
@Composable
fun NowPlayingOverflowMenu(onAction: (NowPlayingAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp)) {
            Icon(AppIcons.MoreVert, contentDescription = stringResource(R.string.more_actions_cd))
        }
        com.localplayer.music.ui.components.AppDropdownMenu(expanded = open, onDismiss = { open = false }) {
            @Composable
            fun Item(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, action: NowPlayingAction) {
                com.localplayer.music.ui.components.AppMenuItem(
                    icon = icon,
                    label = stringResource(label),
                    onClick = {
                        open = false
                        onAction(action)
                    },
                )
            }
            Item(AppIcons.Album, R.string.go_to_album, NowPlayingAction.GoToAlbum)
            Item(AppIcons.Person, R.string.go_to_artist, NowPlayingAction.GoToArtist)
            Item(AppIcons.VisibilityOff, R.string.hide_song, NowPlayingAction.HideSong)
            Item(AppIcons.Edit, R.string.edit_tag, NowPlayingAction.EditTags)
            Item(AppIcons.Lyrics, R.string.edit_lyrics, NowPlayingAction.EditLyrics)
            Item(AppIcons.Info, R.string.track_info, NowPlayingAction.Info)
        }
    }
}


/** Clip window of the tucking sheet in layer space: x from [left] to [right], y from the layer top down to [height]. */
private class MorphShape(
    private val left: Float,
    private val right: Float,
    private val height: Float,
    private val radius: Float,
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline =
        androidx.compose.ui.graphics.Outline.Rounded(
            androidx.compose.ui.geometry.RoundRect(
                left, 0f, right.coerceAtLeast(left), height.coerceIn(0f, size.height),
                // Never larger than half of the window's own width / height (the closed sheet is only as tall as the mini player).
                androidx.compose.ui.geometry.CornerRadius(
                    radius.coerceAtMost(minOf((right - left) / 2f, height / 2f)).coerceAtLeast(0f),
                ),
            ),
        )
}

/**
 * The cover of the current song as a carousel over the whole queue (idea: a swipeable cover strip). Swiping moves the
 * covers with the finger; when the swipe has settled on another cover, that queue entry is played. When the song
 * changes by itself (Next / Previous / queue tap / end of a song) the strip glides to the new cover with a spring that
 * does not bounce. If the queue is not available yet (or does not match the player), the single current cover is shown.
 */
@Composable
private fun CoverCarousel(
    viewModel: LibraryViewModel,
    queueIndex: Int,
    queueSize: Int,
    fallback: NowPlaying,
    width: androidx.compose.ui.unit.Dp,
    reduceMotion: Boolean,
    onClick: () -> Unit,
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val lyricsLabel = stringResource(R.string.lyrics)
    if (queue.isEmpty() || queue.size != queueSize || queueIndex !in queue.indices) {
        ArtworkImage(
            artwork = fallback.artwork,
            size = 320.dp,
            cornerRadius = 64.dp,
            modifier = Modifier.width(width).aspectRatio(1f).clickable(onClickLabel = lyricsLabel, onClick = onClick),
        )
        return
    }
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = queueIndex) { queue.size }
    var userSwiped by remember { mutableStateOf(false) }
    // A user swipe that settled on another cover plays that entry (only a real finger drag counts, never our own glide).
    LaunchedEffect(pager) {
        pager.interactionSource.interactions.collect { interaction ->
            if (interaction is androidx.compose.foundation.interaction.DragInteraction.Start) userSwiped = true
        }
    }
    LaunchedEffect(pager, queueIndex) {
        snapshotFlow { pager.isScrollInProgress }
            .distinctUntilChanged()
            .filter { !it }
            .collect {
                if (!userSwiped) return@collect
                userSwiped = false
                val settled = pager.currentPage
                if (settled != queueIndex) viewModel.playQueueIndex(settled)
            }
    }
    // The player moved (Next / Previous / queue tap / song ended): glide the strip there.
    LaunchedEffect(queueIndex) {
        if (pager.currentPage == queueIndex || userSwiped) return@LaunchedEffect
        val distance = kotlin.math.abs(pager.currentPage - queueIndex)
        if (reduceMotion || distance > 2) {
            pager.scrollToPage(queueIndex)
        } else {
            pager.animateScrollToPage(
                queueIndex,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                ),
            )
        }
    }
    androidx.compose.foundation.pager.HorizontalPager(
        state = pager,
        modifier = Modifier.width(width),
        pageSpacing = 12.dp,
        key = { index -> queue.getOrNull(index)?.occurrenceId ?: "${queue.getOrNull(index)?.mediaId}#$index" },
    ) { page ->
        ArtworkImage(
            artwork = queue.getOrNull(page)?.artwork,
            size = 320.dp,
            cornerRadius = 64.dp,
            modifier = Modifier.width(width).aspectRatio(1f).clickable(onClickLabel = lyricsLabel, onClick = onClick),
        )
    }
}
