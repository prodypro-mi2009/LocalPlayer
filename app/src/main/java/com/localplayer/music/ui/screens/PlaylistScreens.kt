package com.localplayer.music.ui.screens

import com.localplayer.music.ui.components.barInsetBelowKeyboard
import androidx.compose.foundation.clickable
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
    val list = tracks.orEmpty()
    val name = playlist?.name.orEmpty()

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
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename)) },
                            onClick = { menuOpen = false; renaming = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = { menuOpen = false; deleting = true },
                        )
                    }
                }
            },
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = barPadding(extra = 16.dp)) {
            item(key = "hero") {
                DetailHero(
                    title = name,
                    subtitle = songCountText(playlist?.trackCount ?: 0) + " • " +
                        formatDuration(playlist?.totalDurationMs ?: 0L),
                    artwork = artwork.firstOrNull(),
                    cover = { ArtworkCollage(artwork, Modifier.size(200.dp)) },
                    onPlay = { viewModel.playAll(list, shuffle = false) },
                    onShuffle = { viewModel.playAll(list, shuffle = true) },
                )
            }
            item(key = "buttons") {
                OutlinedButton(
                    onClick = { nav.push(Screen.AddTracks(screen.id)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_tracks))
                }
            }
            if (tracks != null && list.isEmpty()) {
                item(key = "empty") { EmptyState(stringResource(R.string.empty_playlist_tracks)) }
            }
            items(list.size, key = { list[it].id }) { index ->
                val song = list[index]
                TrackRow(
                    song = song,
                    isCurrent = song.id == currentId,
                    onClick = { viewModel.playFrom(list, index) },
                    actions = menu.actionsFor(
                        song,
                        onRemove = { viewModel.removeFromPlaylist(screen.id, song.uri) },
                        removeLabel = stringResource(R.string.remove_from_playlist, name),
                    ),
                    onMoveUp = if (index > 0) {
                        { viewModel.movePlaylistTrack(screen.id, song.uri, list[index - 1].uri) }
                    } else {
                        null
                    },
                    onMoveDown = if (index < list.lastIndex) {
                        { viewModel.movePlaylistTrack(screen.id, song.uri, list[index + 1].uri) }
                    } else {
                        null
                    },
                )
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
        LazyColumn(Modifier.weight(1f)) {
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
