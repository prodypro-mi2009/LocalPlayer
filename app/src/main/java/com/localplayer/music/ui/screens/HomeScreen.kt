package com.localplayer.music.ui.screens

import com.localplayer.music.ui.components.SectionHeader
import com.localplayer.music.ui.theme.artistFont
import com.localplayer.music.ui.theme.albumGenreFont
import com.localplayer.music.ui.theme.headingFont
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.localplayer.music.data.library.ScanFailureKind
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.music.R
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
import com.localplayer.music.ui.components.ThumbScrollbar
import com.localplayer.music.ui.components.thumbAdapter
import com.localplayer.music.ui.components.GenreStyle
import com.localplayer.music.ui.components.LibraryActionRow
import com.localplayer.music.ui.components.scrollEstimate
import com.localplayer.music.ui.components.rememberActionRowProgress
import com.localplayer.music.ui.components.TabFadeHeight
import com.localplayer.music.ui.components.actionRowContentTop
import com.localplayer.music.ui.components.actionRowListInset
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import com.localplayer.music.ui.components.topFade
import com.localplayer.music.ui.components.FadingLazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import com.localplayer.music.ui.components.LibraryTab
import com.localplayer.music.ui.components.NewPlaylistFlow
import com.localplayer.music.ui.components.SongGridCard
import com.localplayer.music.ui.components.SongMenuController
import com.localplayer.music.ui.components.SortOption
import com.localplayer.music.ui.components.TextRow
import com.localplayer.music.ui.components.barPadding
import com.localplayer.music.ui.components.TrackRow
import com.localplayer.music.ui.components.ViewMode
import com.localplayer.music.ui.components.artworkRef
import com.localplayer.music.ui.components.songCountText
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.ui.nav.Screen
import com.localplayer.music.ui.theme.DisplayHeavy
import com.localplayer.music.ui.theme.DisplayHeavyCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** True once the neighbouring tab has been built in this run of the app (so coming back to Home does not wait again). */
private var neighbourTabBuilt = false

/** How long after the first frame the neighbouring tab is built. */
private const val NEIGHBOUR_TAB_DELAY_MS = 1_500L

@Composable
fun HomeScreen(
    viewModel: LibraryViewModel,
    nav: NavState,
    menu: SongMenuController,
    tabs: List<LibraryTab>,
    pagerState: PagerState,
) {
    val scan by viewModel.scanState.collectAsStateWithLifecycle()
    val stateHolder = rememberSaveableStateHolder()
    // At app start only the tab in view is built, so ITS covers load first; the neighbouring tab is built a moment later.
    var neighbourReady by remember { mutableStateOf(neighbourTabBuilt) }
    LaunchedEffect(Unit) {
        if (!neighbourReady) {
            delay(NEIGHBOUR_TAB_DELAY_MS)
            neighbourTabBuilt = true
            neighbourReady = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        // No top bar: Settings is the round button at the right end of the bottom bar. Only the status bar is cleared.
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        // No progress bar on Home while the (fast, incremental) scan runs at start-up; Settings > Scanning still shows it.
        if (!scan.isScanning && scan.error != null) ScanErrorBanner(scan, onRetry = viewModel::rescan, onRemoveFolder = viewModel::removeLibraryFolder, onRelinkFolder = viewModel::relinkLibraryFolder)

        // One saved-state slot per tab: leaving a tab stores its scroll position, grid/list mode and sort,
        // coming back restores them. The tabs are the pages of a pager: a horizontal swipe drags the whole page 1:1
        // with the finger and settles with the finger's velocity; tapping a tab in the bottom bar animates the same
        // pager. (A swipe that starts on a song row belongs to the row's own swipe-to-queue.)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // The neighbouring tabs are composed BEFORE they slide in (like the tabs of the Music 3.8 app, which keeps its
            // pages mounted): the first swipe to a tab no longer builds a whole list while the finger is moving.
            beyondViewportPageCount = if (neighbourReady) 1 else 0,
            // A page changes after a shorter drag (5% instead of the default 50%): the swipe feels lighter.
            flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                state = pagerState,
                snapPositionalThreshold = 0.05f,
            ),
            key = { page -> tabs.getOrNull(page)?.name ?: page },
        ) { page ->
            val tab = tabs.getOrElse(page) { LibraryTab.HOME }
            stateHolder.SaveableStateProvider(tab.name) {
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
    }
}

// ---------------------------------------------------------------- Home = Favorites

/** Height of the Shuffle / History row inside the Favorites list (the 48 dp button plus 8 dp above and below). */
private val HomeActionRowHeight = 64.dp


@Composable
private fun HomeTab(viewModel: LibraryViewModel, nav: NavState, menu: SongMenuController) {
    val favorites by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val currentId = playerState.nowPlaying?.mediaId?.toLongOrNull()
    val list = favorites ?: return
    val yourMixDismissed by viewModel.settings.yourMixCardDismissed.collectAsStateWithLifecycle()

    val favState = rememberLazyListState()
    // Index of the Shuffle row: after the optional Your Mix card and the title. Once it has scrolled away it lives on as a pill.
    val actionRowIndex = (if (yourMixDismissed) 0 else 1) + 1
    // How far the list has moved the Shuffle row away (px): 0 while the row is still in place, then it grows as the row
    // leaves and the content below it passes. The floating copy at the top fades in while the row leaves, and then the
    // buttons glide together and the pill forms in step with the same scrolling (and back again when scrolling up).
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rowHeightPx = with(density) { HomeActionRowHeight.toPx() }
    val fadeStartPx = with(density) { 24.dp.toPx() }
    val packRangePx = with(density) { com.localplayer.music.ui.components.ActionRowScrollRange.toPx() }
    val away = remember(actionRowIndex, rowHeightPx) {
        derivedStateOf {
            when {
                favState.firstVisibleItemIndex < actionRowIndex -> 0f
                favState.firstVisibleItemIndex == actionRowIndex -> favState.firstVisibleItemScrollOffset.toFloat()
                favState.firstVisibleItemIndex == actionRowIndex + 1 -> rowHeightPx + favState.firstVisibleItemScrollOffset
                else -> rowHeightPx + packRangePx
            }
        }
    }
    val classicRow = com.localplayer.music.ui.components.LocalClassicActionRow.current
    // Classic look: no floating copy, the row just scrolls away with the list.
    val overlayShown by remember(fadeStartPx, classicRow) { derivedStateOf { !classicRow && away.value > fadeStartPx } }
    Box(Modifier.fillMaxSize()) {
    FadingLazyColumn(Modifier.fillMaxSize(), state = favState, fadeHeight = TabFadeHeight, contentPadding = barPadding(extra = 16.dp)) {
        // Your Mix is its own, dynamic collection: a separate card ABOVE the Favorites, never mixed into them.
        if (!yourMixDismissed) item(key = "your-mix") { YourMixCard(onOpen = { nav.push(Screen.YourMix) }, onHide = { viewModel.setYourMixCardDismissed(true) }) }
        item {
            Text(
                text = stringResource(R.string.favorites),
                style = DisplayHeavy.headingFont(),
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
                    colors = com.localplayer.music.ui.theme.tonalButtonColors(),
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
        if (list.isNotEmpty()) item(key = "fav-header") { SectionHeader(stringResource(R.string.favorite_songs_header)) }
        itemsIndexed(items = list, key = { _, song -> "fav-${song.id}" }) { index, song ->
            TrackRow(
                song = song,
                isCurrent = song.id == currentId,
                onClick = { viewModel.playFrom(list, index) },
                actions = menu.actionsFor(song),
            )
        }
    }
    // Once the Shuffle row has scrolled away it stays on top as a pill (see [away] above).
    if (overlayShown) {
        com.localplayer.music.ui.components.ActionRowLayout(
            progress = { ((away.value - rowHeightPx) / packRangePx).coerceIn(0f, 1f) },
            modifier = Modifier.zIndex(1f).graphicsLayer {
                alpha = ((away.value - fadeStartPx) / (rowHeightPx - fadeStartPx)).coerceIn(0f, 1f)
            },
        ) {
            androidx.compose.material3.FilledTonalButton(
                onClick = { viewModel.playAll(list, shuffle = true) },
                enabled = list.isNotEmpty(),
                colors = com.localplayer.music.ui.theme.tonalButtonColors(),
            ) {
                Icon(AppIcons.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.shuffle_favorites))
            }
            androidx.compose.material3.FilledTonalIconButton(
                onClick = { nav.push(Screen.Recent) },
                modifier = Modifier.size(48.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
            ) {
                Icon(AppIcons.History, contentDescription = stringResource(R.string.recently_played_cd), modifier = Modifier.size(28.dp))
            }
        }
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
            maxLoadPx = 512,
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

/**
 * Your Mix: a separate collection built from the library and the local listening data (implemented in a later
 * phase). Compact tonal card with its own title, so it can never be mistaken for the manual Favorites below.
 */
@Composable
private fun YourMixCard(onOpen: () -> Unit, onHide: () -> Unit) {
    // Tap the card to open the Mix; the cross hides it (Settings > Your Mix shows it again).
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpen)
            .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.your_mix_card_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                stringResource(R.string.your_mix_card_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        IconButton(onClick = onHide) {
            Icon(
                AppIcons.Close,
                contentDescription = stringResource(R.string.your_mix_card_hide),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
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
    var gridMode by rememberSaveable { mutableStateOf(viewModel.settings.gridMode("songs", false)) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val rowProgress = rememberActionRowProgress { if (gridMode) gridState.scrollEstimate() else listState.scrollEstimate() }

    val list = songs ?: return
    if (list.isEmpty() && !scan.isScanning) {
        EmptyState(stringResource(R.string.empty_library))
        return
    }

    // Letters are only meaningful when the list is ordered by a name.
    // Computed once per (list, sort), not on every recomposition (player state / scan progress also recompose this tab).
    val sections = remember(list, sort) {
        val sectionTexts: List<String?>? = when (sort) {
            SongSort.TITLE -> list.map { it.title }
            SongSort.ARTIST -> list.map { it.artist }
            SongSort.ALBUM -> list.map { it.album }
            else -> null
        }
        sectionTexts?.let { AlphabetIndex.firstIndexBySection(it) }
    }

    fun jumpTo(index: Int) {
        scope.launch { if (gridMode) gridState.scrollToItem(index) else listState.scrollToItem(index) }
    }

    Box(Modifier.fillMaxSize()) {
        LibraryActionRow(
            modifier = Modifier.zIndex(1f),
            progress = rowProgress,
            onShuffle = { viewModel.playAll(list, shuffle = true) },
            viewMode = if (gridMode) ViewMode.GRID else ViewMode.LIST,
            onToggleView = { gridMode = !gridMode; viewModel.settings.setGridMode("songs", gridMode) },
            sortOptions = SongSort.entries.map { option ->
                SortOption(sortLabel(option), option == sort) { viewModel.setSort(option) }
            },
            onLocate = {
                val at = list.indexOfFirst { it.id == currentId }
                if (at >= 0) jumpTo(at)
            },
        )
        // Scroll bar: an overlay on top of the list, which uses the whole width (the slim thumb sits in the right margin).
        val scrollAdapter = remember(gridMode, listState, gridState) {
            if (gridMode) gridState.thumbAdapter(columns = 2) else listState.thumbAdapter()
        }
        // The letter in the bubble only when the list is ordered by a name; for other sorts it is a plain scroll bar.
        val letterOf: ((Int) -> String?)? = remember(sections) {
            sections?.let { map ->
                val sectionAt = AlphabetIndex.sectionAtIndex(map)
                val lookup: (Int) -> String? = { index -> sectionAt(index)?.toString() }
                lookup
            }
        }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                if (gridMode) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        modifier = Modifier.actionRowListInset().fillMaxSize().topFade(gridState, TabFadeHeight),
                        contentPadding = barPadding(extra = 12.dp, horizontal = 12.dp, top = actionRowContentTop(8.dp)),
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
                    LazyColumn(Modifier.actionRowListInset().fillMaxSize().topFade(listState, TabFadeHeight), state = listState, contentPadding = barPadding(extra = 8.dp, top = actionRowContentTop())) {
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
        ThumbScrollbar(
            adapter = scrollAdapter,
            letterAt = letterOf,
            modifier = Modifier.fillMaxSize(),
            bottomInset = com.localplayer.music.ui.components.LocalBottomBarInset.current,
        )
        }
    }
}

// ---------------------------------------------------------------- Albums

@Composable
private fun AlbumsTab(viewModel: LibraryViewModel, nav: NavState) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    var gridMode by rememberSaveable { mutableStateOf(viewModel.settings.gridMode("albums", true)) }
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
    val fadeGrid = rememberLazyGridState()
    val fadeList = rememberLazyListState()
    val rowProgress = rememberActionRowProgress { if (gridMode) fadeGrid.scrollEstimate() else fadeList.scrollEstimate() }
    Box(Modifier.fillMaxSize()) {
        LibraryActionRow(
            modifier = Modifier.zIndex(1f),
            progress = rowProgress,
            onShuffle = {
                val keys = list.map { it.groupKey }.toSet()
                viewModel.shuffleSongs { it.albumGroupKey in keys }
            },
            viewMode = if (gridMode) ViewMode.GRID else ViewMode.LIST,
            onToggleView = { gridMode = !gridMode; viewModel.settings.setGridMode("albums", gridMode) },
            sortOptions = AlbumSort.entries.mapIndexed { i, _ -> SortOption(labels[i], i == sortIndex) { sortIndex = i } },
        )
        if (gridMode) {
            LazyVerticalGrid(
                state = fadeGrid,
                columns = GridCells.Fixed(2),
                modifier = Modifier.actionRowListInset().fillMaxSize().topFade(fadeGrid, TabFadeHeight),
                contentPadding = barPadding(extra = 12.dp, horizontal = 12.dp, top = actionRowContentTop(8.dp)),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.groupKey }) { album ->
                    AlbumCard(album) { nav.push(Screen.Album(album.groupKey, album.title.orEmpty())) }
                }
            }
        } else {
            LazyColumn(Modifier.actionRowListInset().fillMaxSize().topFade(fadeList, TabFadeHeight), state = fadeList, contentPadding = barPadding(extra = 8.dp, top = actionRowContentTop())) {
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
                style = MaterialTheme.typography.titleSmall.albumGenreFont(),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                album.artist ?: unknownArtist,
                style = MaterialTheme.typography.bodySmall.artistFont(),
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
    var gridMode by rememberSaveable { mutableStateOf(viewModel.settings.gridMode("artists", false)) }
    var sortIndex by rememberSaveable { mutableIntStateOf(0) }
    val raw = artists ?: return
    if (raw.isEmpty()) {
        EmptyState(stringResource(R.string.empty_artists))
        return
    }
    val list = remember(raw, sortIndex) { LibrarySorting.artists(raw, NameCountSort.entries[sortIndex]) }
    val unknown = stringResource(R.string.unknown_artist)
    val labels = listOf(stringResource(R.string.sort_name), stringResource(R.string.sort_song_count))
    val fadeGrid = rememberLazyGridState()
    val fadeList = rememberLazyListState()
    val rowProgress = rememberActionRowProgress { if (gridMode) fadeGrid.scrollEstimate() else fadeList.scrollEstimate() }
    Box(Modifier.fillMaxSize()) {
        LibraryActionRow(
            modifier = Modifier.zIndex(1f),
            progress = rowProgress,
            onShuffle = {
                val keys = list.map { it.key }.toSet()
                viewModel.shuffleSongs { (it.primaryArtistKey ?: "") in keys }
            },
            viewMode = if (gridMode) ViewMode.GRID else ViewMode.LIST,
            onToggleView = { gridMode = !gridMode; viewModel.settings.setGridMode("artists", gridMode) },
            sortOptions = labels.mapIndexed { i, l -> SortOption(l, i == sortIndex) { sortIndex = i } },
        )
        if (gridMode) {
            LazyVerticalGrid(
                state = fadeGrid,
                columns = GridCells.Fixed(2),
                modifier = Modifier.actionRowListInset().fillMaxSize().topFade(fadeGrid, TabFadeHeight),
                contentPadding = barPadding(extra = 12.dp, horizontal = 12.dp, top = actionRowContentTop(8.dp)),
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
                            style = MaterialTheme.typography.bodyMedium.artistFont(),
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
            LazyColumn(Modifier.actionRowListInset().fillMaxSize().topFade(fadeList, TabFadeHeight), state = fadeList, contentPadding = barPadding(extra = 8.dp, top = actionRowContentTop())) {
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
    var gridMode by rememberSaveable { mutableStateOf(viewModel.settings.gridMode("genres", true)) }
    var sortIndex by rememberSaveable { mutableIntStateOf(0) }
    val raw = genres ?: return
    if (raw.isEmpty()) {
        EmptyState(stringResource(R.string.empty_genres))
        return
    }
    val list = remember(raw, sortIndex) { LibrarySorting.genres(raw, NameCountSort.entries[sortIndex]) }
    val unknown = stringResource(R.string.unknown_genre)
    val labels = listOf(stringResource(R.string.sort_name), stringResource(R.string.sort_song_count))
    val fadeGrid = rememberLazyGridState()
    val fadeList = rememberLazyListState()
    val rowProgress = rememberActionRowProgress { if (gridMode) fadeGrid.scrollEstimate() else fadeList.scrollEstimate() }
    Box(Modifier.fillMaxSize()) {
        LibraryActionRow(
            modifier = Modifier.zIndex(1f),
            progress = rowProgress,
            onShuffle = {
                val keys = list.map { it.key }.toSet()
                viewModel.shuffleSongs { (TextNormalizer.groupKey(it.genre) ?: "") in keys }
            },
            viewMode = if (gridMode) ViewMode.GRID else ViewMode.LIST,
            onToggleView = { gridMode = !gridMode; viewModel.settings.setGridMode("genres", gridMode) },
            sortOptions = labels.mapIndexed { i, l -> SortOption(l, i == sortIndex) { sortIndex = i } },
        )
        if (gridMode) {
            LazyVerticalGrid(
                state = fadeGrid,
                columns = GridCells.Fixed(2),
                modifier = Modifier.actionRowListInset().fillMaxSize().topFade(fadeGrid, TabFadeHeight),
                contentPadding = barPadding(extra = 12.dp, horizontal = 12.dp, top = actionRowContentTop(8.dp)),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.key }) { genre ->
                    GenreCard(genre, unknown) { nav.push(Screen.Genre(genre.key, genre.name.orEmpty())) }
                }
            }
        } else {
            LazyColumn(Modifier.actionRowListInset().fillMaxSize().topFade(fadeList, TabFadeHeight), state = fadeList, contentPadding = barPadding(extra = 8.dp, top = actionRowContentTop())) {
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
                style = DisplayHeavyCard.albumGenreFont(),
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
    var gridMode by rememberSaveable { mutableStateOf(viewModel.settings.gridMode("playlists", true)) }
    var sortIndex by rememberSaveable { mutableIntStateOf(0) }
    val raw = playlists ?: return
    val list = remember(raw, sortIndex) { LibrarySorting.playlists(raw, NameCountSort.entries[sortIndex]) }
    val labels = listOf(stringResource(R.string.sort_name), stringResource(R.string.sort_song_count))

    val fadeGrid = rememberLazyGridState()
    val fadeList = rememberLazyListState()
    val rowProgress = rememberActionRowProgress { if (gridMode) fadeGrid.scrollEstimate() else fadeList.scrollEstimate() }
    Box(Modifier.fillMaxSize()) {
        LibraryActionRow(
            modifier = Modifier.zIndex(1f),
            progress = rowProgress,
            onShuffle = {
                viewModel.shufflePlaylists(list.map { it.id })
            },
            viewMode = if (gridMode) ViewMode.GRID else ViewMode.LIST,
            onToggleView = { gridMode = !gridMode; viewModel.settings.setGridMode("playlists", gridMode) },
            sortOptions = labels.mapIndexed { i, l -> SortOption(l, i == sortIndex) { sortIndex = i } },
        )
        if (gridMode) {
            LazyVerticalGrid(
                state = fadeGrid,
                columns = GridCells.Fixed(2),
                modifier = Modifier.actionRowListInset().fillMaxSize().topFade(fadeGrid, TabFadeHeight),
                contentPadding = barPadding(extra = 12.dp, horizontal = 12.dp, top = actionRowContentTop(8.dp)),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "new-playlist") { NewPlaylistCard { creating = true } }
                items(list, key = { it.id }) { playlist ->
                    PlaylistCard(playlist, viewModel) { nav.push(Screen.Playlist(playlist.id)) }
                }
            }
        } else {
            LazyColumn(Modifier.actionRowListInset().fillMaxSize().topFade(fadeList, TabFadeHeight), state = fadeList, contentPadding = barPadding(extra = 8.dp, top = actionRowContentTop())) {
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
                        coverFile = playlist.coverFile,
                    )
                }
            }
        }
    }
    if (creating) {
        NewPlaylistFlow(
            viewModel,
            onDismiss = { creating = false },
            onOpenDownload = {
                creating = false
                nav.push(Screen.SettingsDownload)
            },
            onOpenSpotify = {
                creating = false
                nav.push(Screen.SettingsSpotify)
            },
        )
    }
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
        if (playlist.coverFile != null) {
            com.localplayer.music.ui.components.PlaylistCoverImage(
                playlist.coverFile,
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
            )
        } else {
            ArtworkCollage(art, Modifier.fillMaxWidth().aspectRatio(1f))
        }
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
com.localplayer.music.ui.components.ChunkyProgress(scan.processed.toFloat() / scan.toProcess, thickness = 8.dp)
        } else {
            Text(
                text = pluralStringResource(R.plurals.scanning_device, scan.discovered, scan.discovered),
                style = MaterialTheme.typography.bodySmall,
            )
com.localplayer.music.ui.components.ChunkyProgress(null, thickness = 8.dp)
        }
    }
}

/** Shown when the last scan failed: says what happened, that the library is intact, and lets the user retry. */
@Composable
fun ScanErrorBanner(
    scan: ScanState,
    onRetry: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRelinkFolder: (String, android.net.Uri) -> Unit,
) {
    val error = scan.error ?: return
    val failedTree = error.treeUri
    val repick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { picked -> if (picked != null && failedTree != null) onRelinkFolder(failedTree, picked) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = when (error.kind) {
                    ScanFailureKind.QUERY_FAILED -> stringResource(R.string.scan_failed_query)
                    ScanFailureKind.FOLDER_FAILED -> stringResource(R.string.scan_failed_folder, error.detail.orEmpty())
                    else -> stringResource(R.string.scan_failed_unexpected)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            // The system's own reason (for example a missing permission), so the cause can be found.
            if (error.kind != ScanFailureKind.FOLDER_FAILED) error.detail?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // A folder that can no longer be read: one tap removes it from the library (the files are never touched).
            error.treeUri?.let { uri ->
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { repick.launch(null) },
                        colors = com.localplayer.music.ui.theme.tonalButtonColors(),
                    ) { Text(stringResource(R.string.scan_relink_folder), maxLines = 1) }
                    androidx.compose.material3.TextButton(onClick = { onRemoveFolder(uri) }) {
                        Text(stringResource(R.string.scan_remove_folder_short), maxLines = 1)
                    }
                }
            }
        }
        androidx.compose.material3.TextButton(onClick = onRetry) { Text(stringResource(R.string.scan_retry)) }
    }
}
