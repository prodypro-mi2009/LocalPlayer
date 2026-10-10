package com.localplayer.music.ui

import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.onSizeChanged
import com.localplayer.music.ui.components.LocalBottomBarInset
import com.localplayer.music.ui.theme.ArtworkGradient
import com.localplayer.music.ui.theme.artworkVerticalBrush
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.music.LocalPlayerApp
import com.localplayer.music.R
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.LibraryBottomNav
import com.localplayer.music.ui.components.LibraryTab
import com.localplayer.music.ui.components.TabOrder
import com.localplayer.music.ui.components.SongMenuController
import com.localplayer.music.ui.components.UiFeedback
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.ui.nav.Screen
import com.localplayer.music.ui.nav.encode
import com.localplayer.music.ui.player.MiniPlayer
import com.localplayer.music.ui.player.NowPlayingScreen
import com.localplayer.music.ui.theme.rememberArtworkTone
import com.localplayer.music.ui.screens.AddTracksScreen
import com.localplayer.music.ui.screens.AlbumScreen
import com.localplayer.music.ui.screens.ArtistScreen
import com.localplayer.music.ui.screens.EditMetadataScreen
import com.localplayer.music.ui.screens.GenreScreen
import com.localplayer.music.ui.screens.HomeScreen
import com.localplayer.music.ui.screens.LyricsEditorScreen
import com.localplayer.music.ui.screens.LyricsScreen
import com.localplayer.music.ui.screens.PlaylistScreen
import com.localplayer.music.ui.screens.QueueScreen
import com.localplayer.music.ui.screens.RecentScreen
import com.localplayer.music.ui.screens.SearchScreen
import com.localplayer.music.ui.screens.SettingsScreen
import com.localplayer.music.ui.screens.YearScreen
import com.localplayer.music.util.MediaAccess
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun AppRoot(viewModel: LibraryViewModel) {
    val context = LocalContext.current
    var audioGranted by remember { mutableStateOf(MediaAccess.audioGranted(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        audioGranted = MediaAccess.audioGranted(context)
        if (audioGranted) viewModel.rescan()
    }

    // Also covers coming back from the system settings screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val audio = MediaAccess.audioGranted(context)
        if (audio != audioGranted) {
            audioGranted = audio
            if (audio) viewModel.rescan()
        }
    }

    // First open: ask for the audio permission (and notifications) in one go; the scan then starts automatically.
    var requestedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!requestedOnce) {
            requestedOnce = true
            if (!MediaAccess.audioGranted(context) || !MediaAccess.notificationsGranted(context)) {
                permissionLauncher.launch(MediaAccess.requiredPermissions())
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            Toast.makeText(
                context,
                context.getString(message.res, *message.args.toTypedArray()),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    if (!audioGranted) {
        PermissionScreen(
            onGrant = { permissionLauncher.launch(MediaAccess.requiredPermissions()) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null)),
                )
            },
        )
    } else {
        LibraryRoot(viewModel)
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = AppIcons.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) { Text(stringResource(R.string.grant_access)) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryRoot(viewModel: LibraryViewModel) {
    val context = LocalContext.current
    val container = (context.applicationContext as LocalPlayerApp).container
    val nav = rememberSaveable(saver = NavState.Saver) { NavState() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val feedback = remember(snackbar, scope) { UiFeedback(snackbar, scope) }
    val menu = remember(viewModel, nav, feedback) {
        SongMenuController(viewModel, nav, feedback) { id -> context.getString(id) }
    }
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    // Top-level library destination (bottom navigation); kept across detail screens and rotation.
    val tabOrderRaw by container.settings.tabOrder.collectAsStateWithLifecycle()
    val tabOrder = remember(tabOrderRaw) { TabOrder.parse(tabOrderRaw) }
    // Starts on the first tab of the user's order; afterwards the selected tab is kept by identity, so
    // reordering in Settings never switches the screen under the user.
    var tabIndex by rememberSaveable { mutableIntStateOf(tabOrder.first().ordinal) }
    val stateHolder = rememberSaveableStateHolder()

    // The user's own background picture (kept separate from album artwork), dimmed for readability.
    val backgroundVersion by container.settings.backgroundVersion.collectAsStateWithLifecycle()
    val backgroundDim by container.settings.backgroundDim.collectAsStateWithLifecycle()
    val background by produceState<ImageBitmap?>(initialValue = null, backgroundVersion) {
        value = if (backgroundVersion == 0L) null else container.background.load()
    }

    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }

    // --- Back stack bookkeeping --------------------------------------------------------------------
    // (1) A screen that left the stack gives up its saved UI state (scroll position, chips, ...), so opening it
    //     again starts fresh, while every screen that is still on the stack keeps its own state.
    // (2) Leaving Search (back to the library) clears the query. Opening a result and coming back does NOT: Search
    //     is still on the stack then. This runs only when the stack changes, never on recomposition.
    val stackKeys = nav.stack.map { it.encode() }.toSet()
    val searchOpen = Screen.Search in nav.stack
    var knownKeys by remember { mutableStateOf(emptySet<String>()) }
    var searchWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(stackKeys) {
        (knownKeys - stackKeys).forEach { stateHolder.removeState(it) }
        knownKeys = stackKeys
    }
    LaunchedEffect(searchOpen) {
        if (searchWasOpen && !searchOpen) viewModel.setSearchQuery("")
        searchWasOpen = searchOpen
    }

    // Accent colour of the current artwork (null = feature off / not available -> theme colours).
    val artworkColors by container.settings.artworkColors.collectAsStateWithLifecycle()
    val tone by rememberArtworkTone(playerState.nowPlaying?.artwork, artworkColors)
    val backdropBase by animateColorAsState(
        targetValue = tone ?: MaterialTheme.colorScheme.primaryContainer,
        animationSpec = tween(durationMillis = 500),
        label = "backdropTint",
    )
    val density = LocalDensity.current

    val dismissedText = stringResource(R.string.queue_dismissed)
    val restoredText = stringResource(R.string.queue_restored)
    val undoText = stringResource(R.string.undo)

    // Height of the floating bottom bars; lists use it as bottom content padding (see LocalBottomBarInset).
    var barsHeightPx by remember { mutableIntStateOf(0) }
    val barInset = with(density) { barsHeightPx.toDp() }
    val onLyrics = nav.current == Screen.Lyrics

    Box(Modifier.fillMaxSize()) {
        background?.let {
            Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = backgroundDim)))
        }
        // ONE continuous tonal backdrop for the whole window, status bar included. Every screen's top
        // controls sit on this same layer, so there is no separate strip behind the Settings button and nothing is
        // re-drawn when the tab changes. Same hue in every stop (see ArtworkGradient): no grey smear.
        Box(
            Modifier.fillMaxSize().background(
                artworkVerticalBrush(
                    tone = backdropBase,
                    strength = 0.45f,
                    endY = with(density) { 420.dp.toPx() } / ArtworkGradient.FADE_END,
                ),
            ),
        )

        // The screens use the WHOLE window (behind the status bar and behind the bottom bars).
        CompositionLocalProvider(LocalBottomBarInset provides barInset) {
            Box(Modifier.fillMaxSize()) {
                val screen = nav.current
                // Each screen keeps its own scroll position / tab while it is on the back stack.
                stateHolder.SaveableStateProvider(screen.encode()) {
                    when (screen) {
                        Screen.Home -> HomeScreen(viewModel, nav, menu, LibraryTab.fromOrdinal(tabIndex))
                        Screen.Search -> SearchScreen(nav, viewModel, menu)
                        Screen.Recent -> RecentScreen(nav, viewModel, menu)
                        Screen.Settings -> SettingsScreen(nav, viewModel)
                        Screen.Queue -> QueueScreen(nav, viewModel, feedback)
                        Screen.Lyrics -> LyricsScreen(nav, viewModel, tone)
                        is Screen.LyricsEditor -> LyricsEditorScreen(screen.trackId, nav, viewModel, container)
                        is Screen.Artist -> ArtistScreen(screen, nav, viewModel, menu)
                        is Screen.Album -> AlbumScreen(screen, nav, viewModel, menu)
                        is Screen.Genre -> GenreScreen(screen, nav, viewModel, menu)
                        is Screen.Year -> YearScreen(screen, nav, viewModel, menu)
                        is Screen.Playlist -> PlaylistScreen(screen, nav, viewModel, menu)
                        is Screen.AddTracks -> AddTracksScreen(screen, nav, viewModel)
                        is Screen.EditMetadata -> EditMetadataScreen(screen.trackId, nav, container)
                    }
                }
            }
        }

        // Floating bottom bars: they stay fixed at the bottom but are translucent and sit on a soft gradient, so the
        // lists keep scrolling visibly underneath them instead of stopping at a solid black rectangle.
        val scrimBase = if (onLyrics) Color.Black else MaterialTheme.colorScheme.background
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { barsHeightPx = it.height },
        ) {
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to scrimBase.copy(alpha = 0f),
                            0.30f to scrimBase.copy(alpha = 0.55f),
                            1f to scrimBase.copy(alpha = 0.82f),
                        ),
                    ),
                ),
            )
            Column(Modifier.padding(top = 20.dp)) {
                playerState.nowPlaying?.let { now ->
                    // Swipe the mini player left: stop, clear the queue, offer Undo (queue + position restored).
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                scope.launch {
                                    val snapshot = viewModel.dismissQueue()
                                    val result = withTimeoutOrNull(5_000L) {
                                        snackbar.showSnackbar(
                                            message = dismissedText,
                                            actionLabel = undoText,
                                            duration = SnackbarDuration.Indefinite,
                                        )
                                    }
                                    if (result == SnackbarResult.ActionPerformed && snapshot != null) {
                                        viewModel.restoreQueue(snapshot)
                                        withTimeoutOrNull(5_000L) {
                                            snackbar.showSnackbar(restoredText, duration = SnackbarDuration.Indefinite)
                                        }
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {},
                    ) {
                        // Rounded, translucent container: readable, but the list shows through a little.
                        Surface(
                            color = (tone?.let { lerp(MaterialTheme.colorScheme.surfaceContainerHigh, it, 0.22f) }
                                ?: MaterialTheme.colorScheme.surfaceContainerHigh).copy(alpha = BAR_SURFACE_ALPHA),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            MiniPlayer(
                                now = now,
                                isPlaying = playerState.isPlaying,
                                hasNext = playerState.hasNext,
                                durationMs = playerState.durationMs,
                                positionMs = viewModel::currentPositionMs,
                                onOpen = { showPlayer = true },
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onNext = viewModel::next,
                                onPrevious = viewModel::previous,
                            )
                        }
                    }
                }
                // Bottom navigation only on the root (library) level; detail screens have their own back. The
                // navigation pads the system navigation area itself; on detail screens a spacer does it, so the
                // inset is applied exactly once either way.
                if (nav.stack.size == 1) {
                    LibraryBottomNav(
                        selected = LibraryTab.fromOrdinal(tabIndex),
                        order = tabOrder,
                        onSelect = { tabIndex = it.ordinal },
                        onSearch = { nav.push(Screen.Search) },
                    )
                } else {
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = barInset))
    }

    menu.Dialogs()
    // Full-screen Now Playing above everything (including the mini player and the bottom navigation).
    AnimatedVisibility(
        visible = showPlayer,
        enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
    ) {
        NowPlayingScreen(
            viewModel = viewModel,
            tone = tone,
            onDismiss = { showPlayer = false },
            // Queue and Lyrics are singleton screens (NavRules): opening them again returns to the existing entry
            // instead of stacking a second copy, so one back press always leaves them.
            onOpenQueue = {
                showPlayer = false
                nav.push(Screen.Queue)
            },
            onOpenLyrics = {
                showPlayer = false
                nav.push(Screen.Lyrics)
            },
        )
    }
}

/** Opacity of the floating bar surfaces (mini player, navigation pill). */
const val BAR_SURFACE_ALPHA = 0.88f
