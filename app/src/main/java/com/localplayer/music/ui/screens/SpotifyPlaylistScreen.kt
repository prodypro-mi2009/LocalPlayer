package com.localplayer.music.ui.screens

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.music.R
import com.localplayer.music.data.spotify.FillPhase
import com.localplayer.music.data.spotify.FillProblem
import com.localplayer.music.data.spotify.LibraryEntry
import com.localplayer.music.data.spotify.SpotifyMatch
import com.localplayer.music.data.spotify.SpotifyMatcher
import com.localplayer.music.data.spotify.SpotifyPlaylist
import com.localplayer.music.data.spotify.SpotifyPlaylistReader
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.FilledField
import com.localplayer.music.ui.components.SettingsCustomRow
import com.localplayer.music.ui.components.SettingsGroup
import com.localplayer.music.ui.components.SettingsTopBar
import com.localplayer.music.ui.components.barPadding
import com.localplayer.music.ui.components.topFade
import com.localplayer.music.ui.nav.NavState
import kotlinx.coroutines.launch

private sealed interface SpotifyState {
    data object Idle : SpotifyState

    data object Loading : SpotifyState

    data class Failed(val messageRes: Int) : SpotifyState

    data class Done(val playlist: SpotifyPlaylist, val matches: List<SpotifyMatch>) : SpotifyState
}

/**
 * Playlist from Spotify: reads the song names of a playlist from its share link and shows which of them the library
 * already has. The found songs can become a playlist; the missing ones can be copied as a text list. Nothing is
 * downloaded from Spotify.
 */
@Composable
fun SpotifyPlaylistScreen(nav: NavState, viewModel: LibraryViewModel) {
    val library by viewModel.songs.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var link by rememberSaveable { mutableStateOf("") }
    var playlistName by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<SpotifyState>(SpotifyState.Idle) }

    val fill by viewModel.spotifyFill.collectAsStateWithLifecycle()

    /** Compares the songs of a read playlist with the library. */
    fun matchesFor(playlist: SpotifyPlaylist): List<SpotifyMatch> {
        val entries = library.orEmpty().map { song ->
            LibraryEntry(
                song.uri,
                song.title,
                listOfNotNull(song.artist, song.primaryArtist, song.albumArtist).flatMap { it.split(',', '&') },
                song.durationMs,
            )
        }
        return SpotifyMatcher.match(playlist.tracks, entries)
    }

    fun compare(playlist: SpotifyPlaylist): SpotifyState {
        playlistName = playlist.name
        return SpotifyState.Done(playlist, matchesFor(playlist))
    }

    // After a download from YouTube (or any change of the library) the list is compared again.
    LaunchedEffect(library) {
        val current = state as? SpotifyState.Done
        if (current != null) state = SpotifyState.Done(current.playlist, matchesFor(current.playlist))
    }
    // "Add to an existing playlist": the songs waiting for the playlist choice (null = no question open).
    var pickFor by remember { mutableStateOf<List<String>?>(null) }
    pickFor?.let { uris ->
        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
        val choices = playlists.orEmpty().filter { it.sourceFolderKey == null }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pickFor = null },
            title = { Text(stringResource(R.string.spotify_pick_playlist)) },
            text = {
                if (choices.isEmpty()) {
                    Text(stringResource(R.string.spotify_no_playlists), style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(choices.size, key = { choices[it].id }) { index ->
                            val playlist = choices[index]
                            Text(
                                playlist.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addToPlaylist(playlist, uris)
                                        pickFor = null
                                    }
                                    .padding(horizontal = 4.dp, vertical = 14.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { pickFor = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    fun read() {
        state = SpotifyState.Loading
        scope.launch {
            state = when (val result = SpotifyPlaylistReader.read(link)) {
                is SpotifyPlaylistReader.Result.Success -> compare(result.playlist)
                SpotifyPlaylistReader.Result.BadLink -> SpotifyState.Failed(R.string.spotify_error_link)
                SpotifyPlaylistReader.Result.NotFound -> SpotifyState.Failed(R.string.spotify_error_not_found)
                SpotifyPlaylistReader.Result.Network -> SpotifyState.Failed(R.string.spotify_error_network)
                SpotifyPlaylistReader.Result.Unreadable -> SpotifyState.Failed(R.string.spotify_error_unreadable)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SettingsTopBar(title = stringResource(R.string.spotify_title), onBack = { nav.pop() })
        val listState = rememberLazyListState()
        LazyColumn(
            Modifier.fillMaxSize().topFade(listState),
            state = listState,
            contentPadding = barPadding(extra = 24.dp, horizontal = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "link") {
                SettingsGroup(title = stringResource(R.string.spotify_link_group), note = stringResource(R.string.spotify_how_to)) {
                    item {
                        SettingsCustomRow {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledField(
                                    value = link,
                                    onValueChange = { link = it },
                                    label = stringResource(R.string.spotify_link_hint),
                                    enabled = state != SpotifyState.Loading,
                                    trailing = {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    val pasted = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text
                                                    if (pasted != null) link = pasted.toString().trim()
                                                }
                                            },
                                        ) { Icon(AppIcons.ContentPaste, contentDescription = stringResource(R.string.dl_paste)) }
                                    },
                                )
                                Button(
                                    onClick = { read() },
                                    enabled = state != SpotifyState.Loading && link.isNotBlank() && library != null,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                ) { Text(stringResource(R.string.spotify_read)) }
                                Text(
                                    stringResource(R.string.spotify_own_only_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                when (val current = state) {
                                    SpotifyState.Loading -> Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                        Text(stringResource(R.string.spotify_reading), style = MaterialTheme.typography.bodyMedium)
                                    }
                                    is SpotifyState.Failed -> Text(
                                        stringResource(current.messageRes),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            }
            val done = state as? SpotifyState.Done
            if (done != null) {
                val found = done.matches.filter { it.libraryUri != null }
                val missing = done.matches.filter { it.libraryUri == null }
                item(key = "summary") {
                    SettingsGroup(
                        title = done.playlist.name,
                        note = stringResource(R.string.spotify_limit_note),
                    ) {
                        item {
                            SettingsCustomRow {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        stringResource(R.string.spotify_summary, found.size, done.matches.size),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    FilledField(
                                        value = playlistName,
                                        onValueChange = { playlistName = it },
                                        label = stringResource(R.string.spotify_playlist_name),
                                    )
                                    Button(
                                        onClick = {
                                            viewModel.createPlaylist(playlistName, found.mapNotNull { it.libraryUri })
                                        },
                                        enabled = found.isNotEmpty() && playlistName.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                    ) {
                                        Text(pluralStringResource(R.plurals.spotify_create, found.size, found.size))
                                    }
                                    FilledTonalButton(
                                        onClick = { pickFor = found.mapNotNull { it.libraryUri } },
                                        enabled = found.isNotEmpty(),
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                    ) {
                                        Text(pluralStringResource(R.plurals.spotify_add_existing, found.size, found.size))
                                    }
                                    if (missing.isNotEmpty() || fill.phase != FillPhase.IDLE) {
                                        if (fill.phase == FillPhase.IDLE || (fill.phase == FillPhase.DONE && missing.isNotEmpty())) {
                                            FilledTonalButton(
                                                onClick = { viewModel.findOnYoutube(missing.map { it.track }) },
                                                enabled = missing.isNotEmpty(),
                                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                            ) {
                                                Text(pluralStringResource(R.plurals.spotify_fill, missing.size, missing.size))
                                            }
                                            Text(
                                                stringResource(R.string.spotify_fill_note),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (fill.phase != FillPhase.IDLE) {
                                            FillProgress(
                                                fill,
                                                onStop = { viewModel.stopFill() },
                                                onClear = { viewModel.clearFill() },
                                                onOpenDownload = { nav.push(com.localplayer.music.ui.nav.Screen.SettingsDownload) },
                                            )
                                        }
                                    }
                                    if (missing.isNotEmpty()) {
                                        FilledTonalButton(
                                            onClick = {
                                                val text = missing.joinToString("\n") { "${it.track.artist} - ${it.track.title}" }
                                                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", text))) }
                                            },
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                        ) {
                                            Text(pluralStringResource(R.plurals.spotify_copy_missing, missing.size, missing.size))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                items(done.matches.size, key = { "row$it" }) { index ->
                    val match = done.matches[index]
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                match.track.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                match.track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.padding(start = 8.dp))
                        val inLibrary = match.libraryUri != null
                        Text(
                            stringResource(if (inLibrary) R.string.spotify_in_library else R.string.spotify_missing),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (inLibrary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Progress and result of "find the missing songs on YouTube". */
@Composable
private fun FillProgress(fill: com.localplayer.music.data.spotify.FillState, onStop: () -> Unit, onClear: () -> Unit, onOpenDownload: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (fill.phase == FillPhase.SEARCHING) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Text(
                    stringResource(R.string.spotify_fill_searching, fill.searched, fill.total, fill.found),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.spotify_fill_stop)) }
            return@Column
        }
        val problem = fill.problem
        if (problem != null) {
            Text(
                stringResource(
                    when (problem) {
                        FillProblem.BOT -> R.string.dl_error_bot
                        FillProblem.ENGINE -> R.string.dl_error_engine
                        FillProblem.BUSY -> R.string.spotify_fill_busy
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            fill.detail?.let { androidx.compose.foundation.text.selection.SelectionContainer { Text(it, style = MaterialTheme.typography.bodySmall) } }
        } else if (fill.found > 0) {
            Text(stringResource(R.string.spotify_fill_found, fill.found), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOpenDownload, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.spotify_fill_open))
            }
        } else {
            Text(stringResource(R.string.spotify_fill_none), style = MaterialTheme.typography.bodyMedium)
        }
        if (fill.notFound.isNotEmpty()) {
            Text(stringResource(R.string.spotify_fill_left, fill.notFound.size), style = MaterialTheme.typography.bodyMedium)
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    fill.notFound.joinToString("\n") { "${it.artist} - ${it.title}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = {
                    val text = fill.notFound.joinToString("\n") { "${it.artist} - ${it.title}" }
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", text))) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.spotify_fill_copy_left)) }
        }
        OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.spotify_fill_close)) }
    }
}
