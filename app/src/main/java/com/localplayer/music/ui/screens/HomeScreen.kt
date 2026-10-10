package com.localplayer.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.music.R
import com.localplayer.music.data.db.TrackListItem
import com.localplayer.music.data.library.AlbumItem
import com.localplayer.music.data.library.AlbumSort
import com.localplayer.music.data.library.AlphabetIndex
import com.localplayer.music.data.library.ArtistItem
import com.localplayer.music.data.library.GenreItem
import com.localplayer.music.data.library.LibrarySorting
import com.localplayer.music.data.library.NameCountSort
import com.localplayer.music.data.library.PlaylistItem
import com.localplayer.music.data.library.ScanState
import com.localplayer.music.data.library.SongSort
import com.localplayer.music.data.library.TextNormalizer
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.ArtworkCollage
import com.localplayer.music.ui.components.ArtworkImage
import com.localplayer.music.ui.components.ArtworkRow
import com.localplayer.music.ui.components.EmptyState
import com.localplayer.music.ui.components.FastScrollbar
import com.localplayer.music.ui.components.GenreStyle
import com.localplayer.music.ui.components.LibraryActionRow
import com.localplayer.music.ui.components.LibraryTab
import com.localplayer.music.ui.components.NameDialog
import com.localplayer.music.ui.components.NewPlaylistFlow
import com.localplayer.music.ui.components.SongGridCard
import com.localplayer.music.ui.components.SongMenuController
import com.localplayer.music.ui.components.SortOption
import com.localplayer.music.ui.components.TextRow
import com.localplayer.music.ui.components.TopControls
import com.localplayer.music.ui.components.barPadding
import com.localplayer.music.ui.components.TrackRow
import com.localplayer.music.ui.components.ViewMode
import com.localplayer.music.ui.components.artworkRef
import com.localplayer.music.ui.components.songCountText
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.ui.nav.Screen
import com.localplayer.music.ui.theme.DisplayHeavy
import com.localplayer.music.ui.theme.DisplayHeavyCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: LibraryViewModel,
    nav: NavState,
    menu: SongMenuController,
    tab: LibraryTab,
) {
    val scan by viewModel.scanState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        // Compact top controls: no big title; Settings at the top right.
        TopControls(onSettings = { nav.push(Screen.Settings) })
        if (scan.isScanning) ScanBanner(scan)
        if (!scan.isScanning && scan.error != null) ScanErrorBanner(scan, onRetry = viewModel::rescan)

        when (tab) {
            LibraryTab.HOME -> HomeTab(viewModel, nav, menu)
            LibraryTab.SONGS -> SongsTab(viewModel, menu)
            LibraryTab.ALBUMS -> AlbumsTab(viewModel, nav)
            LibraryTab.ARTISTS -> ArtistsTab(viewModel, nav)
            LibraryTab.PLAYLISTS -> PlaylistsTab(viewModel, nav)
            LibraryTab.GENRES -> GenresTab(viewModel, nav)
        }
    }
}

// ---------------------------------------------------------------- Home = Favorites

@Composable
private fun HomeTab(viewModel: LibraryViewModel, nav: NavState, menu: SongMenuController) {
    val favorites by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val currentId = playerState.nowPlaying?.mediaId?.toLongOrNull()
    val list = favorites ?: return

    LazyColumn(Modifier.fillMaxSize(), contentPadding = barPadding(extra = 16.dp)) {
        item {
            Text(
                text = stringResource(R.string.favorites),
                style = DisplayHeavy,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.FilledTonalButton(
                    onClick = { viewModel.playAll(list, shuffle = true) },
                    enabled = list.isNotEmpty(),
                ) {
                    Icon(AppIcons.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.shuffle_favorites))
                }
                Spacer(Modifier.weight(1f))
                // Icon-only, round history control (no text inside); 48dp touch target, labelled for TalkBack.
                androidx.compose.material3.FilledTonalIconButton(
                    onClick = { nav.push(Screen.Recent) },
                    modifier = Modifier.size(48.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                ) {
                    Icon(
                        AppIcons.History,
                        contentDescription = stringResource(R.string.recently_played_cd),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        if (list.isEmpty()) {
            item { FavoritesEmpty() }
        } else {
            item { FavoritesCollage(list.mapNotNull { it.artworkRef() }.distinctBy { it.trackId }.take(3)) }
        }
        item { LocalMixSection() }
        itemsIndexed(items = list, key = { _, song -> "fav-${song.id}" }) { index, song ->
            TrackRow(
                song = song,
                isCurrent = song.id == currentId,
                onClick = { viewModel.playFrom(list, index) },
                actions = menu.actionsFor(song),
            )
        }
    }
}

/** Asymmetric collage: one large tile and two stacked small ones, from local artwork only. */
@Composable
private fun FavoritesCollage(artworks: List<com.localplayer.music.playback.ArtworkRef>) {
    if (artworks.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(200.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ArtworkImage(
            artwork = artworks[0],
            size = 200.dp,
            cornerRadius = 32.dp,
            modifier = Modifier.weight(1.4f).fillMaxSize(),
        )
        if (artworks.size > 1) {
            Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ArtworkImage(
                    artwork = artworks[1],
                    size = 96.dp,
                    cornerRadius = 20.dp,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                if (artworks.size > 2) {
                    ArtworkImage(
                        artwork = artworks[2],
                        size = 96.dp,
                        cornerRadius = 44.dp,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FavoritesEmpty() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            AppIcons.HeartOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.empty_favorites),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Empty placeholder only. LocalMix is intentionally NOT implemented. */
@Composable
private fun LocalMixSection() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
    ) {
        Text(
            stringResource(R.string.local_mix_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.local_mix_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- Songs

@Composable
private fun sortLabel(sort: SongSort): String = stringResource(
    when (sort) {
        SongSort.TITLE -> R.string.sort_title
        SongSort.ARTIST -> R.string.sort_artist
        SongSort.ALBUM -> R.string.sort_album
        SongSort.DATE_ADDED -> R.string.sort_date_added
        SongSort.YEAR -> R.string.sort_year
        SongSort.DURATION -> R.string.sort_duration
    },
)

@Composable
private fun SongsTab(viewModel: LibraryViewModel, menu: SongMenuController) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val scan by viewModel.scanState.collectAsStateWithLifecycle()
    val currentId = playerState.nowPlaying?.mediaId?.toLongOrNull()
    var gridMode by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    val list = songs ?: return
    if (list.isEmpty() && !scan.isScanning) {
        EmptyState(stringResource(R.string.empty_library))
        return
    }

    // Letters are only meaningful when the list is ordered by a name.
    val sectionTexts: List<String?>? = when (sort) {
        SongSort.TITLE -> list.map { it.title }
        SongSort.ARTIST -> list.map { it.artist }
        SongSort.ALBUM -> list.map { it.album }
        else -> null
    }
    val sections = remember(list, sort) { sectionTexts?.let { AlphabetIndex.firstIndexBySection(it) } }

    fun jumpTo(index: Int) {
        scope.launch { if (gridMode) gridState.scrollToItem(index) else listState.scrollToItem(index) }
    }

    Column(Modifier.fillMaxSize()) {
        LibraryActionRow(
            onShuffle = { viewModel.playAll(list, shuffle = true) },
            viewMode = if (gridMode) ViewMode.GRID else ViewMode.LIST,
            onToggleView = { gridMode = !gridMode },
            sortOptions = SongSort.entries.map { option ->
                SortOption(sortLabel(option), option == sort) { viewModel.setSort(option) }
            },
            onLocate = {
                val at = list.indexOfFirst { it.id == currentId }
                if (at >= 0) jumpTo(at)
            },
        )
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxSize()) {
                if (gridMode) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = barPadding(extra = 12.dp, horizontal = 12.dp, top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(items = list, key = { _, song -> song.id }) { index, song ->
                            SongGridCard(
                                song = song,
                                isCurrent = song.id == currentId,
                                onClick = { viewModel.playFrom(list, index) },
                                onMore = menu.actionsFor(song).onMore,
                            )
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = barPadding(extra = 8.dp)) {
                        itemsIndexed(
                            items = list,
                            key = { _, song -> song.id },
                            contentType = { _, _ -> "song" },
                        ) { index, song ->
                            // Swipe-left-to-queue lives inside TrackRow, so every list behaves identically.
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
            if (sections != null) {
                FastScrollbar(
                    letters = sections.keys.toList(),
                    onLetter = { letter -> sections[letter]?.let { jumpTo(it) } },
                    modifier = Modifier.padding(end = 2.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Albums

@Composable
private fun AlbumsTab(viewModel: LibraryViewModel, nav: NavState) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    var gridMode by rememberSaveable { mutableStateOf(true) }
    var sortIndex by rememberSaveable { mutableIntStateOf(0) }
    val raw = albums ?: return
    if (raw.isEmpty()) {
        EmptyState(stringResource(R.string.empty_albums))
        return
    }
    val sort = AlbumSort.entries[sortIndex]
    val list = remember(raw, sort) { LibrarySorting.albums(raw, sort) }
    val labels = listOf(
        stringResource(R.string.sort_album_title),
        stringResource(R.string.sort_artist),
        stringResource(R.string.sort_year),
        stringResource(R.string.sort_song_count),
    )
    Column(Modifier.fillMaxSize()) {
        LibraryActionRow(
            onShuffle = {
                val keys = list.map { it.groupKey }.toSet()
                viewModel.playAll(songs.orEmpty().filter { it.albumGroupKey in keys }, shuffle = true)
            },
            viewMode = if (gridMode) ViewMode.GRID else ViewMode.LIST,
            onToggleView = { gridMode = !gridMode },
            sortOptions = AlbumSort.entries.mapIndexed { i, _ -> SortOption(labels[i], i == sortIndex) { sortIndex = i } },
        )
        if (gridMode) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = barPadding(extra = 12.dp, horizontal = 12.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.groupKey }) { album ->
                    AlbumCard(album) { nav.push(Screen.Album(album.groupKey, album.title.orEmpty())) }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = barPadding(extra = 8.dp)) {
                items(list, key = { it.groupKey }) { album ->
                    ArtworkRow(
                        artwork = album.artwork,
                        size = 56.dp,
                        title = album.title ?: stringResource(R.string.unknown_album),
                        subtitle = (album.artist ?: stringResource(R.string.unknown_artist)) + " • " +
                            songCountText(album.trackCount),
                        onClick = { nav.push(Screen.Album(album.groupKey, album.title.orEmpty())) },
                    )
                }
            }
        }
    }
}

/** Tall card: artwork fills it, a fade at the bottom carries title, artist and song count. */
@Composable
fun AlbumCard(album: AlbumItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val unknownAlbum = stringResource(R.string.unknown_album)
    val unknownArtist = stringResource(R.string.unknown_artist)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
    ) {
        ArtworkImage(
            artwork = album.artwork,
            size = 240.dp,
            cornerRadius = 0.dp,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(110.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(
                album.title ?: unknownAlbum,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                album.artist ?: unknownArtist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                songCountText(album.trackCount),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
            )
        }
    }
}

// ---------------------------------------------------------------- Artists

@Composable
private fun ArtistsTab(viewModel: LibraryViewModel, nav: NavState) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    var gridMode by rememberSaveable { mutableStateOf(false) }
    var sortIndex by rememberSaveable { mutableIntStateOf(0) }
    val raw = artists ?: return
    if (raw.isEmpty()) {
        EmptyState(stringResource(R.string.empty_artists))
        return
    }
    val list = remember(raw, sortIndex) { LibrarySorting.artists(raw, NameCountSort.entries[sortIndex]) }
    val unknown = stringResource(R.string.unknown_artist)
    val labels = listOf(stringResource(R.string.sort_name), stringResource(R.string.sort_song_count))
    Column(Modifier.fillMaxSize()) {
        LibraryActionRow(
            onShuffle = {
                val keys = list.map { it.key }.toSet()
                viewModel.playAll(songs.orEmpty().filter { (it.primaryArtistKey ?: "") in keys }, shuffle = true)
            },
            viewMode = if (gridMode) ViewMode.GRID else ViewMode.LIST,
            onToggleView = { gridMode = !gridMode },
            sortOptions = labels.mapIndexed { i, l -> SortOption(l, i == sortIndex) { sortIndex = i } },
        )
        if (gridMode) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = barPadding(extra = 12.dp, horizontal = 12.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.key }) { artist ->
                    Column(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .clickable { nav.push(Screen.Artist(artist.key, artist.name.orEmpty())) }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ArtworkImage(
                            artwork = artist.artwork,
                            size = 140.dp,
                            cornerRadius = 70.dp,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                        Text(
                            artist.name ?: unknown,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            songCountText(artist.trackCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = barPadding(extra = 8.dp)) {
                items(list, key = { it.key }) { artist ->
                    // Circular local artwork (the artist's own tracks only; never remote images).
                    ArtistRow(artist, unknown) { nav.push(Screen.Artist(artist.key, artist.name.orEmpty())) }
                }
            }
        }
    }
}

@Composable
private fun ArtistRow(artist: ArtistItem, unknown: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(artwork = artist.artwork, size = 56.dp, cornerRadius = 28.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                artist.name ?: unknown,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                songCountText(artist.trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------- Genres

@Composable
private fun GenresTab(viewModel: LibraryViewModel, nav: NavState) {
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    var gridMode by rememberSaveable { mutableStateOf(true) }
    var sortIndex by rememberSaveable { mutableIntStateOf(0) }
    val raw = genres ?: return
    if (raw.isEmpty()) {
        EmptyState(stringResource(R.string.empty_genres))
        return
    }
    val list = remember(raw, sortIndex) { LibrarySorting.genres(raw, NameCountSort.entries[sortIndex]) }
    val unknown = stringResource(R.string.unknown_genre)
    val labels = listOf(stringResource(R.string.sort_name), stringResource(R.string.sort_song_count))
    Column(Modifier.fillMaxSize()) {
        LibraryActionRow(
            onShuffle = {
                val keys = list.map { it.key }.toSet()
                viewModel.playAll(
                    songs.orEmpty().filter { (TextNormalizer.groupKey(it.genre) ?: "") in keys },
                    shuffle = true,
                )
            },
            viewMode = if (gridMode) ViewMode.GRID else ViewMode.LIST,
            onToggleView = { gridMode = !gridMode },
            sortOptions = labels.mapIndexed { i, l -> SortOption(l, i == sortIndex) { sortIndex = i } },
        )
        if (gridMode) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = barPadding(extra = 12.dp, horizontal = 12.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.key }) { genre ->
                    GenreCard(genre, unknown) { nav.push(Screen.Genre(genre.key, genre.name.orEmpty())) }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = barPadding(extra = 8.dp)) {
                items(list, key = { it.key }) { genre ->
                    TextRow(
                        title = genre.name ?: unknown,
                        subtitle = songCountText(genre.trackCount),
                        onClick = { nav.push(Screen.Genre(genre.key, genre.name.orEmpty())) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreCard(genre: GenreItem, unknown: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (GenreStyle.tone(genre.name)) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1.25f)
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = GenreStyle.icon(genre.name),
            contentDescription = null,
            tint = content,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(76.dp).alpha(0.22f),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(
                genre.name ?: unknown,
                style = DisplayHeavyCard,
                color = content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(songCountText(genre.trackCount), style = MaterialTheme.typography.labelMedium, color = content)
        }
    }
}

// ---------------------------------------------------------------- Playlists

@Composable
private fun PlaylistsTab(viewModel: LibraryViewModel, nav: NavState) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var gridMode by rememberSaveable { mutableStateOf(true) }
    var sortIndex by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val raw = playlists ?: return
    val list = remember(raw, sortIndex) { LibrarySorting.playlists(raw, NameCountSort.entries[sortIndex]) }
    val labels = listOf(stringResource(R.string.sort_name), stringResource(R.string.sort_song_count))

    Column(Modifier.fillMaxSize()) {
        LibraryActionRow(
            onShuffle = {
                scope.launch {
                    val all = list.flatMap { viewModel.playlistTracks(it.id).first() }.distinctBy { it.id }
                    viewModel.playAll(all, shuffle = true)
                }
            },
            viewMode = if (gridMode) ViewMode.GRID else ViewMode.LIST,
            onToggleView = { gridMode = !gridMode },
            sortOptions = labels.mapIndexed { i, l -> SortOption(l, i == sortIndex) { sortIndex = i } },
        )
        if (gridMode) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = barPadding(extra = 12.dp, horizontal = 12.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "new-playlist") { NewPlaylistCard { creating = true } }
                items(list, key = { it.id }) { playlist ->
                    PlaylistCard(playlist, viewModel) { nav.push(Screen.Playlist(playlist.id)) }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = barPadding(extra = 8.dp)) {
                item(key = "new-playlist") {
                    TextRow(
                        title = stringResource(R.string.new_playlist),
                        subtitle = "",
                        onClick = { creating = true },
                    )
                }
                items(list, key = { it.id }) { playlist ->
                    val art by viewModel.playlistArtwork(playlist.id).collectAsState(initial = emptyList())
                    ArtworkRow(
                        artwork = art.firstOrNull(),
                        size = 56.dp,
                        title = playlist.name,
                        subtitle = songCountText(playlist.trackCount),
                        onClick = { nav.push(Screen.Playlist(playlist.id)) },
                    )
                }
            }
        }
    }
    if (creating) NewPlaylistFlow(viewModel, onDismiss = { creating = false })
}

@Composable
private fun NewPlaylistCard(onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                AppIcons.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Text(
            stringResource(R.string.new_playlist_card),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PlaylistCard(playlist: PlaylistItem, viewModel: LibraryViewModel, onClick: () -> Unit) {
    val art by viewModel.playlistArtwork(playlist.id).collectAsState(initial = emptyList())
    Column(Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick).padding(8.dp)) {
        ArtworkCollage(art, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(
            playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            songCountText(playlist.trackCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ScanBanner(scan: ScanState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (scan.toProcess > 0) {
            Text(
                text = stringResource(R.string.scanning_tags, scan.processed, scan.toProcess),
                style = MaterialTheme.typography.bodySmall,
            )
            LinearProgressIndicator(
                progress = { scan.processed.toFloat() / scan.toProcess },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = stringResource(R.string.scanning_device, scan.discovered),
                style = MaterialTheme.typography.bodySmall,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Shown when the last scan failed: says what happened, that the library is intact, and lets the user retry. */
@Composable
fun ScanErrorBanner(scan: ScanState, onRetry: () -> Unit) {
    val error = scan.error ?: return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                if (error.kind == com.localplayer.music.data.library.ScanFailureKind.QUERY_FAILED) {
                    R.string.scan_failed_query
                } else {
                    R.string.scan_failed_unexpected
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.TextButton(onClick = onRetry) { Text(stringResource(R.string.scan_retry)) }
    }
}
