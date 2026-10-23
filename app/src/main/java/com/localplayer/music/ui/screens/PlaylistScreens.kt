package com.localplayer.music.ui.screens

import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.isActive
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import com.localplayer.music.ui.components.FadingLazyColumn

import com.localplayer.music.ui.components.barInsetBelowKeyboard
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.music.R
import com.localplayer.music.data.library.TextNormalizer
import com.localplayer.music.playback.ArtworkRef
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.ArtworkImage
import com.localplayer.music.ui.components.ConfirmDialog
import com.localplayer.music.ui.components.DetailTopBar
import com.localplayer.music.ui.components.barPadding
import com.localplayer.music.ui.components.EmptyState
import com.localplayer.music.ui.components.NameDialog
import com.localplayer.music.ui.components.SongMenuController
import com.localplayer.music.ui.components.TrackRow
import com.localplayer.music.ui.components.artworkRef
import com.localplayer.music.ui.components.ArtworkCollage
import com.localplayer.music.ui.components.BottomBackButton
import com.localplayer.music.ui.components.DetailHero
import com.localplayer.music.ui.components.songCountText
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.ui.nav.Screen
import com.localplayer.music.util.formatDuration

@Composable
private fun PlaylistCover(artwork: List<ArtworkRef>) {
    if (artwork.size < 4) {
        ArtworkImage(artwork = artwork.firstOrNull(), size = 128.dp, cornerRadius = 8.dp)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (row in 0 until 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (col in 0 until 2) {
                        ArtworkImage(artwork = artwork[row * 2 + col], size = 63.dp, cornerRadius = 4.dp)
                    }
                }
            }
        }
    }
}

/** Tells the user that the playlist follows a folder, and what the last synchronisation did. */
@Composable
private fun FolderLinkBanner(playlist: com.localplayer.music.data.library.PlaylistItem) {
    val folder = playlist.sourceFolderLabel.orEmpty()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.playlist_linked_to_folder, folder),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            stringResource(R.string.playlist_linked_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (playlist.folderUnavailable) {
            Text(
                stringResource(R.string.playlist_folder_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else if (playlist.folderSyncedAt > 0L) {
            val whenText = android.text.format.DateUtils.getRelativeTimeSpanString(playlist.folderSyncedAt).toString()
            Text(
                stringResource(R.string.playlist_last_sync, playlist.folderSyncAdded, playlist.folderSyncRemoved, whenText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun PlaylistScreen(screen: Screen.Playlist, nav: NavState, viewModel: LibraryViewModel, menu: SongMenuController) {
    val playlist by viewModel.playlist(screen.id).collectAsStateWithLifecycle(initialValue = null)
    val tracks by viewModel.playlistTracks(screen.id).collectAsStateWithLifecycle(initialValue = null)
    val artwork by viewModel.playlistArtwork(screen.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val currentId = playerState.nowPlaying?.mediaId?.toLongOrNull()
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    // System photo picker: no storage permission needed. The picture is copied into the app (see PlaylistCoverStore).
    val coverPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.setPlaylistCover(screen.id, uri) }
    val list = tracks.orEmpty()
    val name = playlist?.name.orEmpty()

    // "Edit order" mode: rows get a drag handle (and a remove button); everything else is as in a normal list.
    var editing by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var local by remember { mutableStateOf<List<com.localplayer.music.data.db.TrackListItem>?>(null) }
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragDelta by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val haptics = com.localplayer.music.ui.theme.rememberHaptics()
    val listNow by androidx.compose.runtime.rememberUpdatedState(list)
    // The stored order changed (our own move arriving, or any other change): the drag preview is dropped.
    androidx.compose.runtime.LaunchedEffect(tracks) { local = null; draggedId = null; dragDelta = 0f }
    val shown = local ?: list

    fun swapIfNeeded() {
        val id = draggedId ?: return
        val current = local ?: return
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.firstOrNull { it.key == id } ?: return
        val center = dragged.offset + dragged.size / 2f + dragDelta
        val target = items.firstOrNull { it.key != id && center >= it.offset && center < it.offset + it.size } ?: return
        val from = current.indexOfFirst { it.id == id }
        val to = current.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0 || from == to) return
        val mutable = current.toMutableList()
        mutable.add(to, mutable.removeAt(from))
        local = mutable
        haptics.tick()
        val newOffset = if (to > from) target.offset + target.size - dragged.size else target.offset
        dragDelta += dragged.offset - newOffset
    }

    fun finishDrag() {
        val id = draggedId
        val preview = local
        if (id != null && preview != null) {
            val from = listNow.indexOfFirst { it.id == id }
            val to = preview.indexOfFirst { it.id == id }
            if (from >= 0 && to >= 0 && from != to) {
                // The preview stays on screen until the stored order arrives (LaunchedEffect(tracks) above).
                viewModel.movePlaylistTrack(screen.id, listNow[from].uri, listNow[to].uri)
            } else {
                local = null
            }
        }
        draggedId = null
        dragDelta = 0f
    }

    // Scrolls while a row is held near the top or bottom edge (steady speed on every refresh rate).
    androidx.compose.runtime.LaunchedEffect(draggedId) {
        if (draggedId == null) return@LaunchedEffect
        var last = androidx.compose.runtime.withFrameNanos { it }
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val frame = androidx.compose.runtime.withFrameNanos { it }
            val ticks = ((frame - last) / 16_000_000f).coerceIn(0f, 3f)
            last = frame
            val id = draggedId ?: break
            val info = listState.layoutInfo
            val dragged = info.visibleItemsInfo.firstOrNull { it.key == id }
            if (dragged != null) {
                val center = dragged.offset + dragged.size / 2f + dragDelta
                val zone = 96f
                val speed = when {
                    center < info.viewportStartOffset + zone -> -(info.viewportStartOffset + zone - center) / 4f
                    center > info.viewportEndOffset - zone -> (center - (info.viewportEndOffset - zone)) / 4f
                    else -> 0f
                }
                if (speed != 0f) {
                    dragDelta += listState.scrollBy(speed * ticks)
                    swapIfNeeded()
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(
            title = name,
            onBack = { nav.pop() },
            onSettings = { nav.push(Screen.Settings) },
            actions = {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(AppIcons.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (list.size > 1 || editing) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (editing) R.string.done_editing_order else R.string.edit_order)) },
                                onClick = { menuOpen = false; editing = !editing },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_playlist_cover)) },
                            onClick = {
                                menuOpen = false
                                coverPicker.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                        )
                        if (playlist?.coverFile != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remove_playlist_cover)) },
                                onClick = { menuOpen = false; viewModel.removePlaylistCover(screen.id) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename)) },
                            onClick = { menuOpen = false; renaming = true },
                        )
                        if (playlist?.isFolderLinked == true) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.unlink_from_folder)) },
                                onClick = { menuOpen = false; viewModel.unlinkPlaylistFromFolder(screen.id) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = { menuOpen = false; deleting = true },
                        )
                    }
                }
            },
        )
        FadingLazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = barPadding(extra = 16.dp)) {
            item(key = "hero") {
                DetailHero(
                    title = name,
                    subtitle = songCountText(playlist?.trackCount ?: 0) + " • " +
                        formatDuration(playlist?.totalDurationMs ?: 0L),
                    artwork = artwork.firstOrNull(),
                    cover = {
                        val own = playlist?.coverFile
                        if (own != null) {
                            com.localplayer.music.ui.components.PlaylistCoverImage(
                                own,
                                Modifier.size(200.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
                            )
                        } else {
                            ArtworkCollage(artwork, Modifier.size(200.dp))
                        }
                    },
                    onPlay = { viewModel.playAll(list, shuffle = false) },
                    onShuffle = { viewModel.playAll(list, shuffle = true) },
                )
            }
            item(key = "buttons") {
                val linked = playlist
                if (editing) {
                    Button(
                        onClick = { editing = false },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    ) { Text(stringResource(R.string.done_editing_order)) }
                } else if (linked != null && linked.isFolderLinked) {
                    // A playlist that mirrors a folder is edited by changing the folder (or by unlinking it).
                    FolderLinkBanner(linked)
                } else {
                    OutlinedButton(
                        onClick = { nav.push(Screen.AddTracks(screen.id)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.add_tracks))
                    }
                }
            }
            if (tracks != null && list.isEmpty()) {
                item(key = "empty") { EmptyState(stringResource(R.string.empty_playlist_tracks)) }
            }
            items(shown.size, key = { shown[it].id }) { index ->
                val song = shown[index]
                if (editing) {
                    val dragged = draggedId == song.id
                    ReorderRow(
                        song = song,
                        dragged = dragged,
                        dragOffset = { dragDelta },
                        modifier = if (dragged) Modifier else Modifier.animateItem(),
                        onRemove = if (playlist?.isFolderLinked == true) {
                            null
                        } else {
                            { viewModel.removeFromPlaylist(screen.id, song.uri) }
                        },
                        removeLabel = stringResource(R.string.remove_from_playlist, name),
                        onDragStart = {
                            haptics.pickUp()
                            dragDelta = 0f
                            local = listNow
                            draggedId = song.id
                        },
                        onDrag = { amount ->
                            dragDelta += amount
                            swapIfNeeded()
                        },
                        onDragEnd = {
                            haptics.drop()
                            finishDrag()
                        },
                        onDragCancel = {
                            local = null
                            draggedId = null
                            dragDelta = 0f
                        },
                    )
                } else {
                    TrackRow(
                        song = song,
                        isCurrent = song.id == currentId,
                        onClick = { viewModel.playFrom(list, index) },
                        actions = menu.actionsFor(song),
                    )
                }
            }
        }
    }
    BottomBackButton(onBack = { nav.pop() }, modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 8.dp))
    }
    if (renaming) {
        NameDialog(
            title = stringResource(R.string.rename),
            initial = name,
            confirmLabel = stringResource(R.string.save),
            onConfirm = {
                viewModel.renamePlaylist(screen.id, it)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = stringResource(R.string.delete_playlist_title),
            body = stringResource(R.string.delete_playlist_body),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                deleting = false
                viewModel.deletePlaylist(screen.id)
                nav.pop()
            },
            onDismiss = { deleting = false },
        )
    }
}

/** Pick songs from the whole library to add to a playlist (a song can only be in a playlist once). */
@Composable
fun AddTracksScreen(screen: Screen.AddTracks, nav: NavState, viewModel: LibraryViewModel) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val inPlaylist by viewModel.playlistTracks(screen.playlistId).collectAsStateWithLifecycle(initialValue = emptyList())
    val playlist by viewModel.playlist(screen.playlistId).collectAsStateWithLifecycle(initialValue = null)
    val existing = remember(inPlaylist) { inPlaylist.map { it.uri }.toSet() }
    val selected = remember { mutableStateListOf<String>() }
    var filter by remember { mutableStateOf("") }
    val tokens = remember(filter) { TextNormalizer.searchTokens(filter) }
    val visible = remember(songs, tokens) {
        songs.orEmpty().filter { song ->
            tokens.isEmpty() || TextNormalizer.fold("${song.title} ${song.artist.orEmpty()} ${song.album.orEmpty()}")
                .let { text -> tokens.all { text.contains(it) } }
        }
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = stringResource(R.string.add_tracks), onBack = { nav.pop() })
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        FadingLazyColumn(Modifier.weight(1f)) {
            items(visible, key = { it.id }) { song ->
                val already = song.uri in existing
                val checked = already || song.uri in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !already) {
                            if (song.uri in selected) selected.remove(song.uri) else selected.add(song.uri)
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = !already)
                    Spacer(Modifier.width(12.dp))
                    ArtworkImage(artwork = song.artworkRef(), size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            song.artist.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Button(
            onClick = {
                val target = playlist
                if (target != null) {
                    viewModel.addToPlaylist(target, selected.toList())
                }
                nav.pop()
            },
            enabled = selected.isNotEmpty() && playlist != null,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + barInsetBelowKeyboard()),
        ) {
            Text(stringResource(R.string.add_selected, selected.size))
        }
    }
}

/** A playlist row in "Edit order" mode: artwork, name, a remove button and a three-line drag handle. */
@Composable
private fun ReorderRow(
    song: com.localplayer.music.data.db.TrackListItem,
    dragged: Boolean,
    dragOffset: () -> Float,
    modifier: Modifier,
    onRemove: (() -> Unit)?,
    removeLabel: String,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val unknownArtist = stringResource(R.string.unknown_artist)
    val reorderDescription = stringResource(R.string.reorder_playlist_item, song.title)
    Row(
        modifier = modifier
            .zIndex(if (dragged) 1f else 0f)
            .graphicsLayer { translationY = if (dragged) dragOffset() else 0f; shadowElevation = if (dragged) 6.dp.toPx() else 0f }
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .background(if (dragged) surface else MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(artwork = song.artworkRef(), size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                song.artist ?: unknownArtist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(AppIcons.Close, contentDescription = removeLabel) }
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .pointerInput(song.id) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            onDrag(amount.y)
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragCancel() },
                    )
                }
                .semantics { contentDescription = reorderDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
    }
}

