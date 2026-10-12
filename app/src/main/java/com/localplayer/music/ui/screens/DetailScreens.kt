package com.localplayer.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.localplayer.music.data.db.TrackListItem
import com.localplayer.music.data.library.AlbumItem
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.BottomBackButton
import com.localplayer.music.ui.components.DetailHero
import com.localplayer.music.ui.components.songCountText
import com.localplayer.music.ui.components.ArtworkImage
import com.localplayer.music.ui.components.ConfirmDialog
import com.localplayer.music.ui.components.DetailTopBar
import com.localplayer.music.ui.components.barPadding
import com.localplayer.music.ui.components.EmptyState
import com.localplayer.music.ui.components.SectionHeader
import com.localplayer.music.ui.components.SongMenuController
import com.localplayer.music.ui.components.TrackRow
import com.localplayer.music.ui.components.artworkRef
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.ui.nav.Screen
import com.localplayer.music.util.formatDuration

@Composable
fun PlayShuffleRow(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
            Icon(AppIcons.Play, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.play))
        }
        OutlinedButton(onClick = onShuffle, modifier = Modifier.weight(1f)) {
            Icon(AppIcons.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.shuffle))
        }
    }
}

/** Shared layout of every "list of tracks" detail screen. */
@Composable
internal fun TrackListDetail(
    title: String,
    nav: NavState,
    viewModel: LibraryViewModel,
    menu: SongMenuController,
    tracks: List<TrackListItem>?,
    emptyText: String,
    topBarActions: @Composable () -> Unit = {},
    header: @Composable () -> Unit = {},
    showNumbers: Boolean = false,
    heroRound: Boolean = false,
    heroSubtitle: String? = null,
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val currentId = playerState.nowPlaying?.mediaId?.toLongOrNull()
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(
            title = title,
            onBack = { nav.pop() },
            actions = topBarActions,
            onSettings = { nav.push(Screen.Settings) },
        )
        val list = tracks
        when {
            list == null -> Unit
            list.isEmpty() -> {
                header()
                EmptyState(emptyText)
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = barPadding(extra = 16.dp)) {
                item(key = "hero") {
                    DetailHero(
                        title = title,
                        subtitle = heroSubtitle ?: songCountText(list.size),
                        artwork = list.firstOrNull { it.hasArtwork }?.artworkRef(),
                        round = heroRound,
                        onPlay = { viewModel.playAll(list, shuffle = false) },
                        onShuffle = { viewModel.playAll(list, shuffle = true) },
                    )
                }
                item(key = "header") { header() }
                items(list.size, key = { list[it].id }) { index ->
                    val song = list[index]
                    TrackRow(
                        song = song,
                        isCurrent = song.id == currentId,
                        onClick = { viewModel.playFrom(list, index) },
                        actions = menu.actionsFor(song),
                        leadingNumber = if (showNumbers) (song.trackNumber ?: (index + 1)) else null,
                    )
                }
            }
        }
    }
    BottomBackButton(onBack = { nav.pop() }, modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 8.dp))
    }
}

@Composable
fun ArtistScreen(screen: Screen.Artist, nav: NavState, viewModel: LibraryViewModel, menu: SongMenuController) {
    val tracks by viewModel.artistTracks(screen.key).collectAsStateWithLifecycle(initialValue = null)
    val albums by viewModel.albumsOfArtist(screen.key).collectAsStateWithLifecycle(initialValue = emptyList())
    val unknown = stringResource(R.string.unknown_artist)
    val name = screen.name.ifEmpty { unknown }
    TrackListDetail(
        title = name,
        nav = nav,
        viewModel = viewModel,
        menu = menu,
        tracks = tracks,
        emptyText = stringResource(R.string.empty_library),
        heroRound = true,
        header = {
            Column {
                if (albums.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.section_albums))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(albums, key = { it.groupKey }) { album ->
                            AlbumCard(album, Modifier.width(128.dp)) {
                                nav.push(Screen.Album(album.groupKey, album.title.orEmpty()))
                            }
                        }
                    }
                    SectionHeader(stringResource(R.string.section_songs))
                }
            }
        },
    )
}

@Composable
fun AlbumScreen(screen: Screen.Album, nav: NavState, viewModel: LibraryViewModel, menu: SongMenuController) {
    val tracks by viewModel.albumTracks(screen.groupKey).collectAsStateWithLifecycle(initialValue = null)
    val unknownAlbum = stringResource(R.string.unknown_album)
    val unknownArtist = stringResource(R.string.unknown_artist)
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val currentId = playerState.nowPlaying?.mediaId?.toLongOrNull()
    val list = tracks.orEmpty()
    val first = list.firstOrNull()
    val title = first?.album ?: screen.title.ifEmpty { unknownAlbum }
    val artist = first?.albumArtist ?: first?.primaryArtist ?: first?.artist ?: unknownArtist
    val year = list.mapNotNull { it.year }.minOrNull()
    val discs = list.map { it.discNumber ?: 1 }.distinct().sorted()

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = title, onBack = { nav.pop() }, onSettings = { nav.push(Screen.Settings) })
        if (tracks != null && list.isEmpty()) {
            EmptyState(stringResource(R.string.empty_library))
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = barPadding(extra = 16.dp)) {
            item(key = "hero") {
                DetailHero(
                    title = title,
                    subtitle = listOfNotNull(
                        artist,
                        year?.toString(),
                        songCountText(list.size),
                        formatDuration(list.sumOf { it.durationMs }),
                    ).joinToString(" • "),
                    artwork = list.firstOrNull { it.hasArtwork }?.artworkRef(),
                    onPlay = { viewModel.playAll(list, shuffle = false) },
                    onShuffle = { viewModel.playAll(list, shuffle = true) },
                )
            }
            list.forEachIndexed { index, song ->
                val disc = song.discNumber ?: 1
                if (discs.size > 1 && (index == 0 || (list[index - 1].discNumber ?: 1) != disc)) {
                    item(key = "disc-$disc") { SectionHeader(stringResource(R.string.section_disc, disc)) }
                }
                item(key = song.id) {
                    TrackRow(
                        song = song,
                        isCurrent = song.id == currentId,
                        onClick = { viewModel.playFrom(list, index) },
                        actions = menu.actionsFor(song),
                        leadingNumber = song.trackNumber ?: (index + 1),
                    )
                }
            }
        }
    }
    BottomBackButton(onBack = { nav.pop() }, modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 8.dp))
    }
}

@Composable
fun GenreScreen(screen: Screen.Genre, nav: NavState, viewModel: LibraryViewModel, menu: SongMenuController) {
    val tracks by viewModel.genreTracks(screen.key).collectAsStateWithLifecycle(initialValue = null)
    TrackListDetail(
        title = screen.name.ifEmpty { stringResource(R.string.unknown_genre) },
        nav = nav,
        viewModel = viewModel,
        menu = menu,
        tracks = tracks,
        emptyText = stringResource(R.string.empty_library),
    )
}

@Composable
fun YearScreen(screen: Screen.Year, nav: NavState, viewModel: LibraryViewModel, menu: SongMenuController) {
    val tracks by viewModel.yearTracks(screen.year).collectAsStateWithLifecycle(initialValue = null)
    TrackListDetail(
        title = if (screen.year == 0) stringResource(R.string.unknown_year) else screen.year.toString(),
        nav = nav,
        viewModel = viewModel,
        menu = menu,
        tracks = tracks,
        emptyText = stringResource(R.string.empty_library),
    )
}

@Composable
fun RecentScreen(nav: NavState, viewModel: LibraryViewModel, menu: SongMenuController) {
    val tracks by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    TrackListDetail(
        title = stringResource(R.string.recently_played),
        nav = nav,
        viewModel = viewModel,
        menu = menu,
        tracks = tracks,
        emptyText = stringResource(R.string.empty_history),
        header = {
            TextButton(onClick = { nav.push(Screen.TimeUsage) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.stats_open_from_history))
            }
        },
        topBarActions = {
            IconButton(onClick = { confirmClear = true }, enabled = !tracks.isNullOrEmpty()) {
                Icon(AppIcons.Close, contentDescription = stringResource(R.string.clear_history))
            }
        },
    )
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.clear_history_title),
            body = stringResource(R.string.clear_history_body),
            confirmLabel = stringResource(R.string.clear_history),
            onConfirm = {
                viewModel.clearHistory()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
}
