package com.localplayer.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.music.R
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.ArtworkRow
import com.localplayer.music.ui.components.EmptyState
import com.localplayer.music.ui.components.SectionHeader
import com.localplayer.music.ui.components.barPadding
import com.localplayer.music.ui.components.SongMenuController
import com.localplayer.music.ui.components.TextRow
import com.localplayer.music.ui.components.TrackRow
import com.localplayer.music.ui.components.songCountText
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.ui.nav.Screen

/** Which kind of result the filter chips narrow the (already computed) search results to. */
private enum class SearchFilter { ARTISTS, SONGS, ALBUMS }

/**
 * Local search. Layout, top to bottom, starting at the very top of the window:
 * search field, [Artists] [Song] [Album] chips, results. The search itself (FTS, Greek/accent-insensitive,
 * debounce) is the existing ViewModel flow; the chips only choose which of its result groups are shown.
 *
 * Layout notes (root causes of the old problems): the screen now applies the status bar inset itself
 * (nothing else in the window does for this screen), and its scroll state is a plain `remember`, so the
 * saveable-state holder of the navigation can never restore an old scroll position into a new search.
 */
@Composable
fun SearchScreen(nav: NavState, viewModel: LibraryViewModel, menu: SongMenuController) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val currentId = playerState.nowPlaying?.mediaId?.toLongOrNull()
    val focus = remember { FocusRequester() }
    val unknownArtist = stringResource(R.string.unknown_artist)
    val unknownAlbum = stringResource(R.string.unknown_album)
    var filterName by rememberSaveable { mutableStateOf<String?>(null) }
    val filter = filterName?.let { SearchFilter.valueOf(it) }

    // Always opens at the top: a fresh (non-saveable) list state, reset again for every new query/filter.
    val listState = remember { LazyListState() }
    LaunchedEffect(query, filter) { listState.scrollToItem(0) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.pop() }, modifier = Modifier.size(48.dp)) {
                Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Row(
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                TextField(
                    value = query,
                    onValueChange = viewModel::setSearchQuery,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }, modifier = Modifier.size(48.dp)) {
                        Icon(AppIcons.Close, contentDescription = stringResource(R.string.clear_search))
                    }
                }
            }
        }

        // Exactly three filter controls, directly under the field. Tapping the selected one shows everything again.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter == SearchFilter.ARTISTS,
                onClick = { filterName = toggled(filter, SearchFilter.ARTISTS) },
                label = { Text(stringResource(R.string.filter_artists)) },
            )
            FilterChip(
                selected = filter == SearchFilter.SONGS,
                onClick = { filterName = toggled(filter, SearchFilter.SONGS) },
                label = { Text(stringResource(R.string.filter_songs)) },
            )
            FilterChip(
                selected = filter == SearchFilter.ALBUMS,
                onClick = { filterName = toggled(filter, SearchFilter.ALBUMS) },
                label = { Text(stringResource(R.string.filter_albums)) },
            )
        }

        // Results directly under the chips. imePadding only shortens THIS area when the keyboard opens,
        // so the field and chips never move.
        val found = results
        Column(Modifier.weight(1f).fillMaxWidth().imePadding()) {
            val showSongs = filter == null || filter == SearchFilter.SONGS
            val showArtists = filter == null || filter == SearchFilter.ARTISTS
            val showAlbums = filter == null || filter == SearchFilter.ALBUMS
            val showPlaylists = filter == null
            when {
                query.isBlank() -> EmptyState(stringResource(R.string.search_prompt))
                found == null -> Unit
                found.isEmpty -> EmptyState(stringResource(R.string.empty_search))
                else -> LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = barPadding(extra = 8.dp)) {
                    if (showSongs && found.songs.isNotEmpty()) {
                        item(key = "h-songs") { SectionHeader(stringResource(R.string.section_songs)) }
                        items(found.songs.size, key = { "s-${found.songs[it].id}" }) { index ->
                            val song = found.songs[index]
                            TrackRow(
                                song = song,
                                isCurrent = song.id == currentId,
                                onClick = { viewModel.playFrom(found.songs, index) },
                                actions = menu.actionsFor(song),
                            )
                        }
                    }
                    if (showArtists && found.artists.isNotEmpty()) {
                        item(key = "h-artists") { SectionHeader(stringResource(R.string.section_artists)) }
                        items(found.artists, key = { "a-${it.key}" }) { artist ->
                            ArtworkRow(
                                artwork = artist.artwork,
                                size = 48.dp,
                                title = artist.name ?: unknownArtist,
                                subtitle = songCountText(artist.trackCount),
                                onClick = { nav.push(Screen.Artist(artist.key, artist.name.orEmpty())) },
                            )
                        }
                    }
                    if (showAlbums && found.albums.isNotEmpty()) {
                        item(key = "h-albums") { SectionHeader(stringResource(R.string.section_albums)) }
                        items(found.albums, key = { "al-${it.groupKey}" }) { album ->
                            ArtworkRow(
                                artwork = album.artwork,
                                size = 48.dp,
                                title = album.title ?: unknownAlbum,
                                subtitle = album.artist ?: unknownArtist,
                                onClick = { nav.push(Screen.Album(album.groupKey, album.title.orEmpty())) },
                            )
                        }
                    }
                    if (showPlaylists && found.playlists.isNotEmpty()) {
                        item(key = "h-playlists") { SectionHeader(stringResource(R.string.section_playlists)) }
                        items(found.playlists, key = { "p-${it.id}" }) { playlist ->
                            TextRow(
                                title = playlist.name,
                                subtitle = songCountText(playlist.trackCount),
                                onClick = { nav.push(Screen.Playlist(playlist.id)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun toggled(current: SearchFilter?, tapped: SearchFilter): String? =
    if (current == tapped) null else tapped.name
