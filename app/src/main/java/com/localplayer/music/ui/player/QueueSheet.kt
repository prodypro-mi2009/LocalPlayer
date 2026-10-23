package com.localplayer.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.localplayer.music.R
import com.localplayer.music.data.personalization.ControlColorGroup
import com.localplayer.music.playback.SleepTimerState
import com.localplayer.music.ui.BAR_SURFACE_ALPHA
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.FloatingEdgeHighlight
import com.localplayer.music.ui.components.LocalBottomBarInset
import com.localplayer.music.ui.components.UiFeedback
import com.localplayer.music.ui.components.songCountText
import com.localplayer.music.ui.theme.LocalReduceMotion
import com.localplayer.music.ui.theme.QueueSheetMotion
import com.localplayer.music.ui.theme.SheetPhysics
import com.localplayer.music.ui.theme.controlColor
import com.localplayer.music.ui.theme.pressScale
import com.localplayer.music.ui.theme.rememberHaptics
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** Height reserved at the bottom of the list for the floating toolbar (without the navigation bar). */
private val ToolbarSpace = 88.dp

/**
 * The "Next up" sheet: ONE full-screen layer that is only translated vertically by [queueSheet]'s single offset
 * float (read in the draw phase; nothing is re-laid-out or recomposed per frame). It follows the finger 1:1 and is
 * released with the finger's velocity (see NowPlayingScreen for the opening gesture). The list inside is the same
 * [QueueList] as the Queue screen; closing works by pulling down on the handle / title, or on the list when it is
 * scrolled to the top (nested scroll). Composed only while any part of it is on screen.
 */
@Composable
internal fun QueueSheetLayer(
    viewModel: LibraryViewModel,
    queueSheet: PlayerSheetState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val minFlingPx = with(density) { 48.dp.toPx() }
    val state by viewModel.playerState.collectAsStateWithLifecycle()

    // The app-level snackbar host sits UNDER the full-screen player, so the sheet has its own for Undo.
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val feedback = remember(snackbar, scope) { UiFeedback(snackbar, scope) }
    val actions = rememberQueueActions(viewModel, feedback)

    // Pulling DOWN anywhere in the list: the list scrolls first; only what it cannot use moves the sheet.
    val closeConnection = remember(queueSheet) {
        object : NestedScrollConnection {
            // Finger moving back up while the sheet is pulled down: the sheet takes that movement first.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val current = queueSheet.currentOffset
                if (source != NestedScrollSource.UserInput || current <= 0f || available.y >= 0f) return Offset.Zero
                val consumed = maxOf(available.y, -current)
                queueSheet.dragBy(consumed)
                return Offset(0f, consumed)
            }

            // The list is at its top: the leftover downward movement pulls the sheet.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                queueSheet.dragBy(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val current = queueSheet.currentOffset
                if (current <= 0f) {
                    queueSheet.endDrag()
                    return Velocity.Zero
                }
                // Same projected-position rule as the player: decide on where the sheet is heading.
                if (SheetPhysics.shouldDismiss(current, available.y, queueSheet.extentPx, minFlingPx)) {
                    queueSheet.close(available.y)
                } else {
                    queueSheet.open(available.y)
                }
                return available
            }
        }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val closeLabel = stringResource(R.string.close_queue_cd)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                // Draw phase only. The bounds keep a spring overshoot from lifting it above the top.
                translationY = queueSheet.offsetPx.value.coerceAtLeast(0f)
                alpha = queueSheet.contentAlpha.value
                shape = SheetShape
                clip = true
            }
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .nestedScroll(closeConnection)
            // Swallow taps so nothing behind the sheet can be pressed through it.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Handle + title: a plain drag area (nothing scrolls here).
            val dragState = rememberDraggableState { delta -> queueSheet.dragBy(delta) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            val current = queueSheet.currentOffset
                            if (SheetPhysics.shouldDismiss(current, velocity, queueSheet.extentPx, minFlingPx)) {
                                queueSheet.close(velocity)
                            } else {
                                queueSheet.open(velocity)
                            }
                        },
                    ),
            ) {
                Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 24.dp)
                            .clickable(role = Role.Button, onClickLabel = closeLabel) { queueSheet.close() }
                            .semantics { contentDescription = closeLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
                        )
                    }
                }
                Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)) {
                    Text(
                        stringResource(R.string.next_up),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        songCountText(state.queueSize),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // The list reserves room for the floating toolbar (and the navigation bar) below its last row.
            CompositionLocalProvider(LocalBottomBarInset provides ToolbarSpace + navBottom) {
                QueueList(viewModel = viewModel, onRemove = actions::remove)
            }
        }

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).padding(bottom = ToolbarSpace + navBottom),
        )

        QueueToolbarHost(
            viewModel = viewModel,
            queueSheet = queueSheet,
            actions = actions,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Phase 2: the floating toolbar over the open queue. It has its OWN small animated value, driven by the sheet's
 * progress crossing [QueueSheetMotion.TOOLBAR_VISIBLE_FROM] (not by a timer): it rises into place with a short
 * spring near the end of the opening and leaves as soon as the sheet starts to close. Reduced motion: plain fade.
 */
@Composable
private fun QueueToolbarHost(
    viewModel: LibraryViewModel,
    queueSheet: PlayerSheetState,
    actions: QueueActions,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val shown = remember { Animatable(0f) }
    LaunchedEffect(queueSheet, reduceMotion) {
        snapshotFlow {
            QueueSheetMotion.toolbarVisible(
                QueueSheetMotion.progress(queueSheet.offsetPx.value, queueSheet.extentPx),
                queueSheet.isOpen,
            )
        }.distinctUntilChanged().collectLatest { visible ->
            if (visible) {
                shown.animateTo(
                    1f,
                    if (reduceMotion) tween(120) else spring(dampingRatio = 0.75f, stiffness = 700f),
                )
            } else {
                shown.animateTo(0f, tween(90))
            }
        }
    }
    // Composed (and touchable) only while it is actually visible: a hidden toolbar must never take a tap.
    val present by remember { derivedStateOf { shown.value > 0.02f } }
    if (!present) return
    val risePx = with(LocalDensity.current) { 96.dp.toPx() }
    Box(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .graphicsLayer {
                alpha = shown.value.coerceIn(0f, 1f)
                translationY = if (reduceMotion) 0f else (1f - shown.value) * risePx
            },
    ) {
        QueueToolbar(viewModel, actions)
    }
}

@Composable
private fun QueueToolbar(viewModel: LibraryViewModel, actions: QueueActions) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val sleepState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    var sleepDialog by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = BAR_SURFACE_ALPHA))
            .border(1.dp, FloatingEdgeHighlight, CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // The same actions and the same state as the segmented row on Now Playing.
        ToolbarButton(
            icon = AppIcons.Shuffle,
            description = stringResource(R.string.shuffle),
            active = state.shuffleEnabled,
            onClick = { haptics.tick(); viewModel.toggleShuffle() },
        )
        ToolbarButton(
            icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) AppIcons.RepeatOne else AppIcons.Repeat,
            description = stringResource(
                when (state.repeatMode) {
                    Player.REPEAT_MODE_ONE -> R.string.repeat_one
                    Player.REPEAT_MODE_ALL -> R.string.repeat_all
                    else -> R.string.repeat_off
                },
            ),
            active = state.repeatMode != Player.REPEAT_MODE_OFF,
            onClick = { haptics.tick(); viewModel.cycleRepeat() },
        )
        ToolbarButton(
            icon = AppIcons.Timer,
            description = sleepLabel(sleepState),
            active = sleepState != SleepTimerState.Off,
            onClick = { haptics.tick(); sleepDialog = true },
        )
        Box {
            ToolbarButton(
                icon = AppIcons.MoreVert,
                description = stringResource(R.string.more_actions_cd),
                active = false,
                onClick = { haptics.tick(); menuOpen = true },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.queue_clear_upcoming)) },
                    onClick = {
                        menuOpen = false
                        actions.clearUpcoming()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.clear_queue)) },
                    onClick = {
                        menuOpen = false
                        actions.clearQueue()
                    },
                )
            }
        }
    }
    if (sleepDialog) {
        SleepTimerDialog(viewModel = viewModel, state = sleepState, onDismiss = { sleepDialog = false })
    }
}

@Composable
private fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val custom = controlColor(ControlColorGroup.PLAYBACK_OPTIONS, MaterialTheme.colorScheme.surfaceContainerHigh)
    val tint = custom ?: if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val source = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        interactionSource = source,
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint),
        modifier = Modifier.size(52.dp).pressScale(source),
    ) {
        Icon(icon, contentDescription = description)
    }
}
