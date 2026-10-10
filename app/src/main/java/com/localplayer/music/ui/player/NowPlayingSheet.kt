package com.localplayer.music.ui.player

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.localplayer.music.R
import com.localplayer.music.playback.NowPlaying
import com.localplayer.music.playback.PlayerState
import com.localplayer.music.playback.SleepTimerState
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.ArtworkImage
import com.localplayer.music.ui.theme.AppColors
import com.localplayer.music.ui.theme.artworkVerticalBrush
import com.localplayer.music.util.formatDuration
import android.os.SystemClock
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
@Composable
fun NowPlayingScreen(
    viewModel: LibraryViewModel,
    tone: Color?,
    onDismiss: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onMenuAction: (NowPlayingAction, NowPlaying) -> Unit,
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    // Keep showing the last track while the screen slides out after the queue was cleared.
    var lastNow by remember { mutableStateOf<NowPlaying?>(null) }
    state.nowPlaying?.let { lastNow = it }
    val now = state.nowPlaying ?: lastNow ?: return

    BackHandler(onBack = onDismiss)
    LaunchedEffect(state.nowPlaying == null) { if (state.nowPlaying == null) onDismiss() }

    // Full-strength tone; the alpha curve (and the fade to the plain background) is [artworkVerticalBrush].
    val glow by animateColorAsState(
        targetValue = tone ?: MaterialTheme.colorScheme.primaryContainer,
        animationSpec = tween(durationMillis = 500),
        label = "nowPlayingGlow",
    )
    val position = rememberPlaybackPosition(state.isPlaying, now.mediaId, viewModel::currentPositionMs)
    var scrub by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val fraction = scrub ?: (position.longValue.toFloat() / duration).coerceIn(0f, 1f)
    val sleepState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    var sleepDialog by remember { mutableStateOf(false) }
    val specs by produceState<String?>(initialValue = null, now.uri) {
        value = now.uri?.let { viewModel.audioSpecsText(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(artworkVerticalBrush(glow))
            // Swallow touches so nothing behind the full-screen player can be tapped.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val minHeight = maxHeight
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
                    IconButton(onClick = onOpenQueue, modifier = Modifier.size(48.dp)) {
                        Icon(
                            AppIcons.Queue,
                            contentDescription = "${stringResource(R.string.queue)} (${state.queueSize})",
                        )
                    }
                }

                // The ONLY animated block: artwork + metadata.
                AnimatedContent(
                    targetState = now,
                    contentKey = { it.mediaId },
                    transitionSpec = {
                        (slideInHorizontally(tween(380)) { it / 5 } + fadeIn(tween(380))) togetherWith
                            (slideOutHorizontally(tween(260)) { -it / 5 } + fadeOut(tween(260))) using
                            SizeTransform(clip = false) { _, _ -> snap() }
                    },
                    label = "nowPlayingTrack",
                ) { track ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(8.dp))
                        // Squircle artwork; always square at the same width, so its height never changes.
                        ArtworkImage(
                            artwork = track.artwork,
                            size = 320.dp,
                            cornerRadius = 64.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clickable(onClickLabel = stringResource(R.string.lyrics), onClick = onOpenLyrics),
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(Modifier.fillMaxWidth().height(88.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    track.artist,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    track.album,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            FavoriteButton(viewModel, track.uri)
                            NowPlayingOverflowMenu(onAction = { onMenuAction(it, track) })
                        }
                    }
                }

                WaveSeekBar(
                    fraction = fraction,
                    onScrub = { scrub = it },
                    onSeek = { f ->
                        val target = (f * duration).toLong()
                        viewModel.seekTo(target)
                        position.longValue = target
                        scrub = null
                    },
                    description = stringResource(R.string.wave_seek_bar),
                    activeColor = tone ?: MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration((fraction * duration).toLong()), style = MaterialTheme.typography.labelMedium)
                    Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium)
                }
                // Fixed-height slot, so the chip appearing later never moves the controls.
                Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                    specs?.let {
                        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    IconButton(onClick = viewModel::previous, modifier = Modifier.size(64.dp)) {
                        Icon(AppIcons.SkipPrevious, stringResource(R.string.previous_track), Modifier.size(36.dp))
                    }
                    FilledIconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.size(80.dp)) {
                        Icon(
                            imageVector = if (state.isPlaying) AppIcons.Pause else AppIcons.Play,
                            contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                            modifier = Modifier.size(44.dp),
                        )
                    }
                    IconButton(onClick = viewModel::next, enabled = state.hasNext, modifier = Modifier.size(64.dp)) {
                        Icon(AppIcons.SkipNext, stringResource(R.string.next_track), Modifier.size(36.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))

                // Segmented actions: Shuffle / Repeat / Sleep timer / Lyrics.
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Segment(
                            icon = AppIcons.Shuffle,
                            label = stringResource(R.string.shuffle),
                            active = state.shuffleEnabled,
                            onClick = viewModel::toggleShuffle,
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
                            modifier = Modifier.weight(1f),
                        )
                        Segment(
                            icon = AppIcons.Timer,
                            label = sleepLabel(sleepState),
                            active = sleepState != SleepTimerState.Off,
                            onClick = { sleepDialog = true },
                            modifier = Modifier.weight(1f),
                        )
                        Segment(
                            icon = AppIcons.Lyrics,
                            label = stringResource(R.string.lyrics),
                            active = false,
                            onClick = onOpenLyrics,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (sleepDialog) {
        SleepTimerDialog(viewModel = viewModel, state = sleepState, onDismiss = { sleepDialog = false })
    }
}

@Composable
private fun Segment(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, textAlign = TextAlign.Center)
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
    IconButton(onClick = { viewModel.toggleFavorite(trackUri) }, modifier = Modifier.size(56.dp)) {
        Icon(
            imageVector = if (favorite) AppIcons.Heart else AppIcons.HeartOutline,
            contentDescription = stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite),
            tint = AppColors.FavoriteRed,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun sleepLabel(state: SleepTimerState): String {
    val remaining by produceState(initialValue = 0L, state) {
        if (state is SleepTimerState.Counting) {
            while (true) {
                value = (state.endsAtElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                delay(1_000) // only while this screen is composed
            }
        }
    }
    return when (state) {
        SleepTimerState.Off -> stringResource(R.string.sleep_timer)
        SleepTimerState.EndOfTrack -> stringResource(R.string.sleep_end_of_song)
        is SleepTimerState.Counting -> formatDuration(remaining)
    }
}

@Composable
private fun SleepTimerDialog(viewModel: LibraryViewModel, state: SleepTimerState, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_timer)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                listOf(5, 10, 15, 20, 30, 45, 60).forEach { minutes ->
                    TextButton(
                        onClick = {
                            viewModel.startSleepTimer(minutes)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.sleep_minutes, minutes)) }
                }
                TextButton(
                    onClick = {
                        viewModel.startSleepTimerEndOfTrack()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.sleep_end_of_song)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it.filter(Char::isDigit).take(4) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.sleep_custom_minutes)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            custom.toIntOrNull()?.takeIf { it > 0 }?.let {
                                viewModel.startSleepTimer(it)
                                onDismiss()
                            }
                        },
                        enabled = (custom.toIntOrNull() ?: 0) > 0,
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
enum class NowPlayingAction { GoToAlbum, GoToArtist, HideSong, EditTags, EditLyrics, EditCover }

/** Three vertical dots next to the heart. Every entry is wired to a real screen or action by the caller. */
@Composable
fun NowPlayingOverflowMenu(onAction: (NowPlayingAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp)) {
            Icon(AppIcons.MoreVert, contentDescription = stringResource(R.string.more_actions_cd))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable
            fun Item(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, action: NowPlayingAction) {
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { Icon(icon, contentDescription = null) },
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
            Item(AppIcons.Image, R.string.edit_cover, NowPlayingAction.EditCover)
        }
    }
}
