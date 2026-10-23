package com.localplayer.music.ui

import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.onSizeChanged
import com.localplayer.music.ui.components.LocalBottomBarInset
import com.localplayer.music.ui.theme.ArtworkGradient
import com.localplayer.music.ui.theme.Motion
import com.localplayer.music.ui.theme.artworkVerticalBrush
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import com.localplayer.music.ui.components.FloatingEdgeHighlight
import com.localplayer.music.ui.player.rememberPlayerSheetState
import com.localplayer.music.ui.theme.LocalReduceMotion
import com.localplayer.music.ui.theme.rememberHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.localplayer.music.ui.theme.SheetMorph
import com.localplayer.music.ui.screens.DownloadScreen
import com.localplayer.music.ui.screens.NavigationHubScreen
import com.localplayer.music.ui.screens.TabOrderSettingsScreen
import com.localplayer.music.ui.screens.GesturesSettingsScreen
import com.localplayer.music.ui.screens.ScanSettingsScreen
import com.localplayer.music.ui.screens.ThemeSettingsScreen
import com.localplayer.music.ui.screens.EqualizerSettingsScreen
import com.localplayer.music.ui.screens.ReplayGainSettingsScreen
import com.localplayer.music.ui.screens.MixGeneralSettingsScreen
import com.localplayer.music.ui.screens.MixStrategySettingsScreen
import com.localplayer.music.ui.screens.MixOptionsSettingsScreen
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.localplayer.music.playback.NowPlaying
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
import com.localplayer.music.ui.player.NowPlayingAction
import com.localplayer.music.ui.player.NowPlayingScreen
import com.localplayer.music.ui.theme.rememberArtworkTone
import com.localplayer.music.ui.screens.AddTracksScreen
import com.localplayer.music.ui.screens.AlbumScreen
import com.localplayer.music.ui.screens.ArtistScreen
import com.localplayer.music.ui.screens.EditCoverScreen
import com.localplayer.music.ui.screens.EditMetadataScreen
import com.localplayer.music.ui.screens.HiddenSongsScreen
import com.localplayer.music.ui.screens.ColorsScreen
import com.localplayer.music.ui.screens.FontsScreen
import com.localplayer.music.ui.screens.LibraryFoldersScreen
import com.localplayer.music.ui.screens.GenreScreen
import com.localplayer.music.ui.screens.HomeScreen
import com.localplayer.music.ui.screens.LyricsEditorScreen
import com.localplayer.music.ui.screens.LyricsScreen
import com.localplayer.music.ui.screens.LyricsSearchScreen
import com.localplayer.music.ui.screens.PlaylistScreen
import com.localplayer.music.ui.screens.QueueScreen
import com.localplayer.music.ui.screens.RecentScreen
import com.localplayer.music.ui.screens.SearchScreen
import com.localplayer.music.ui.screens.AboutSettingsScreen
import com.localplayer.music.ui.screens.AudioSettingsScreen
import com.localplayer.music.ui.screens.WidgetsSettingsScreen
import com.localplayer.music.ui.screens.BackupSettingsScreen
import com.localplayer.music.ui.screens.AppearanceSettingsScreen
import com.localplayer.music.ui.screens.LanguageSettingsScreen
import com.localplayer.music.ui.screens.LibrarySettingsScreen
import com.localplayer.music.ui.screens.LyricsSettingsScreen
import com.localplayer.music.ui.screens.SettingsScreen
import com.localplayer.music.ui.screens.TimeUsageScreen
import com.localplayer.music.ui.screens.Top20Screen
import com.localplayer.music.ui.screens.YourMixScreen
import com.localplayer.music.ui.screens.YourMixSettingsScreen
import com.localplayer.music.ui.screens.YearScreen
import com.localplayer.music.util.MediaAccess
import kotlinx.coroutines.delay
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

/** Remembers whether the last navigation was a push (forward) or a pop (back). */
private class NavMotion(var lastScreen: Screen, var lastSize: Int) {
    var forward = true

    /** Set right before a gesture-driven back commits: the screen already animated out under the finger. */
    var pendingSkipExit = false

    /** True for the transition that follows such a back, so the leaving screen is not animated a second time. */
    var skipExit = false
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
    // The Now Playing sheet is driven by ONE shared state (position, springs, drag); `wasOpen` is only what
    // survives rotation / recreation, so the player is still open afterwards.
    var wasOpen by rememberSaveable { mutableStateOf(false) }
    val sheet = rememberPlayerSheetState(initiallyOpen = wasOpen)
    LaunchedEffect(sheet) { snapshotFlow { sheet.isOpen }.collect { wasOpen = it } }
    val reduceMotion = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val navMotion = remember { NavMotion(nav.current, nav.stack.size) }
    // Top-level library destination (bottom navigation); kept across detail screens and rotation.
    val tabOrderRaw by container.settings.tabOrder.collectAsStateWithLifecycle()
    val tabOrder = remember(tabOrderRaw) { TabOrder.parse(tabOrderRaw) }
    // Starts on the first tab of the user's order; afterwards the selected tab is kept by identity, so
    // reordering in Settings never switches the screen under the user.
    var tabIndex by rememberSaveable { mutableIntStateOf(tabOrder.first().ordinal) }
    val stateHolder = rememberSaveableStateHolder()

    // The tabs are the pages of one pager (swipe between them like the pages of a book). The selected tab is still
    // kept by identity in [tabIndex]: a swipe that settles on a page selects that tab, a tap on a tab (or a new
    // order in Settings) moves the pager. A new order re-positions the pager WITHOUT animating, so the screen the
    // user is looking at never changes.
    val pagerState = rememberPagerState(
        initialPage = tabOrder.indexOf(LibraryTab.fromOrdinal(tabIndex)).coerceAtLeast(0),
    ) { tabOrder.size }
    val tabOrderNow by rememberUpdatedState(tabOrder)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            tabOrderNow.getOrNull(page)?.let { tabIndex = it.ordinal }
        }
    }
    val lastTabOrder = remember { arrayOf(tabOrder) }
    LaunchedEffect(tabIndex, tabOrder) {
        val target = tabOrder.indexOf(LibraryTab.fromOrdinal(tabIndex))
        val reordered = lastTabOrder[0] != tabOrder
        lastTabOrder[0] = tabOrder
        if (target >= 0 && target != pagerState.targetPage) {
            if (reordered) pagerState.scrollToPage(target)
            else pagerState.animateScrollToPage(target, animationSpec = Motion.SettleCritical)
        }
    }

    // The user's own background picture (kept separate from album artwork), dimmed for readability.
    val backgroundVersion by container.settings.backgroundVersion.collectAsStateWithLifecycle()
    val backgroundDim by container.settings.backgroundDim.collectAsStateWithLifecycle()
    val background by produceState<ImageBitmap?>(initialValue = null, backgroundVersion) {
        value = if (backgroundVersion == 0L) null else container.background.load()
    }

    // Predictive back: the leaving screen becomes a "card" (see the content wrapper below) that follows the finger:
    // it shrinks a little, rounds its corners and slides right. Only the leaving screen is transformed (it is
    // remembered in [leavingScreen]); the previous screen is composed by the normal back transition at commit.
    val backProgress = remember { Animatable(0f) }
    var leavingScreen by remember { mutableStateOf<Screen?>(null) }
    PredictiveBackHandler(enabled = nav.stack.size > 1) { progress ->
        leavingScreen = nav.current
        navMotion.forward = false
        try {
            progress.collect { backProgress.snapTo(it.progress) }
            // Committed: the card slides the rest of the way out while the previous screen shows underneath.
            navMotion.pendingSkipExit = false
            nav.pop()
            scope.launch {
                // 1.0 = where the finger can take it (60 % of the width); 1.75 = fully off screen.
                backProgress.animateTo(1.75f, tween(Motion.SCREEN_BACK_MS, easing = Motion.EaseOut))
                delay(120) // let the leaving screen be removed before the transform is reset
                leavingScreen = null
                backProgress.snapTo(0f)
            }
        } catch (e: CancellationException) {
            scope.launch {
                backProgress.animateTo(0f, Motion.SettleCritical)
                leavingScreen = null
            }
            throw e
        }
    }

    // One haptic tick each time a drag-up of the mini player crosses 25 % of the way (either direction).
    LaunchedEffect(sheet) {
        snapshotFlow { sheet.extentPx - sheet.offsetPx.value >= 0.25f * sheet.extentPx }
            .distinctUntilChanged()
            .drop(1)
            .collect { if (sheet.isDragging) haptics.threshold() }
    }

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
        // A popped screen is still on screen while it animates out; dropping its saved state now would let it
        // save itself again when it leaves, so the next visit would not start fresh.
        delay(Motion.SCREEN_CLEANUP_MS.toLong())
        (knownKeys - stackKeys).forEach { stateHolder.removeState(it) }
        knownKeys = stackKeys
    }
    LaunchedEffect(searchOpen) {
        if (searchWasOpen && !searchOpen) viewModel.setSearchQuery("")
        searchWasOpen = searchOpen
    }

    // Accent colour of the current artwork (null = feature off / not available -> theme colours).
    val artworkColors by container.settings.artworkColors.collectAsStateWithLifecycle()
    val pureMode = com.localplayer.music.ui.theme.LocalPureMode.current
    // Pure black / white: no artwork colours and no background picture, just the one uniform colour.
    val tone by rememberArtworkTone(playerState.nowPlaying?.artwork, artworkColors && !pureMode.isOn)
    val backdropBase = animateColorAsState(
        targetValue = tone ?: com.localplayer.music.ui.theme.fallbackTint(),
        animationSpec = Motion.AmbientTint,
        label = "backdropTint",
    )
    val density = LocalDensity.current

    val dismissedText = stringResource(R.string.queue_dismissed)
    val restoredText = stringResource(R.string.queue_restored)
    val undoFailedText = stringResource(R.string.undo_not_possible)
    val undoText = stringResource(R.string.undo)

    // Height of the floating bottom bars; lists use it as bottom content padding (see LocalBottomBarInset).
    var barsHeightPx by remember { mutableIntStateOf(0) }
    // The mini player's rectangle (screen px), measured while the sheet is closed: the sheet grows out of it.
    var miniTopPx by remember { mutableStateOf(0f) }
    var miniBottomPx by remember { mutableStateOf(0f) }
    val barInset = with(density) { barsHeightPx.toDp() }
    val onLyrics = nav.current == Screen.Lyrics

    Box(Modifier.fillMaxSize()) {
        background?.takeIf { !pureMode.isOn }?.let {
            Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = backgroundDim)))
        }
        // ONE continuous tonal backdrop for the whole window, status bar included. Every screen's top
        // controls sit on this same layer, so there is no separate strip behind the Settings button and nothing is
        // re-drawn when the tab changes. Same hue in every stop (see ArtworkGradient): no grey smear.
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(
                    artworkVerticalBrush(
                        tone = backdropBase.value,
                        strength = 0.45f,
                        endY = 420.dp.toPx() / ArtworkGradient.FADE_END,
                    ),
                )
            },
        )

        // The screens use the WHOLE window (behind the status bar and behind the bottom bars).
        val swipeOn by container.settings.swipeActionsEnabled.collectAsStateWithLifecycle()
        val queueButtonOn by container.settings.queueButtonEnabled.collectAsStateWithLifecycle()
        CompositionLocalProvider(
            LocalBottomBarInset provides barInset,
            com.localplayer.music.ui.components.LocalRowGestures provides
                com.localplayer.music.ui.components.RowGestures(swipeOn, queueButtonOn),
        ) {
            // Push = new screen moves in from the right; pop = from the left. replaceTop counts as a push.
            if (nav.current != navMotion.lastScreen) {
                navMotion.forward = nav.stack.size >= navMotion.lastSize
                navMotion.skipExit = navMotion.pendingSkipExit
                navMotion.pendingSkipExit = false
                navMotion.lastScreen = nav.current
                navMotion.lastSize = nav.stack.size
            }
            // One place that draws a screen (with its saved scroll/tab state): used by the content below and by the
            // "previous screen" underlay shown while a back gesture is in progress.
            val renderScreen: @Composable (Screen) -> Unit = { screen ->
                stateHolder.SaveableStateProvider(screen.encode()) {
                    when (screen) {
                        Screen.Home -> HomeScreen(viewModel, nav, menu, tabOrder, pagerState)
                        Screen.Search -> SearchScreen(nav, viewModel, menu)
                        Screen.Recent -> RecentScreen(nav, viewModel, menu)
                        Screen.Settings -> SettingsScreen(nav, viewModel)
                        Screen.Queue -> QueueScreen(nav, viewModel, feedback)
                        Screen.Lyrics -> LyricsScreen(nav, viewModel, tone)
                        is Screen.LyricsEditor -> LyricsEditorScreen(screen.trackId, nav, viewModel, container)
                        is Screen.LyricsSearch -> LyricsSearchScreen(screen.trackId, nav, viewModel, container)
                        is Screen.Artist -> ArtistScreen(screen, nav, viewModel, menu)
                        is Screen.Album -> AlbumScreen(screen, nav, viewModel, menu)
                        is Screen.Genre -> GenreScreen(screen, nav, viewModel, menu)
                        is Screen.Year -> YearScreen(screen, nav, viewModel, menu)
                        is Screen.Playlist -> PlaylistScreen(screen, nav, viewModel, menu)
                        is Screen.AddTracks -> AddTracksScreen(screen, nav, viewModel)
                        is Screen.EditMetadata -> EditMetadataScreen(screen.trackId, nav, container)
                        is Screen.EditCover -> EditCoverScreen(screen.trackId, nav, container)
                        Screen.HiddenSongs -> HiddenSongsScreen(nav, viewModel)
                        Screen.LibraryFolders -> LibraryFoldersScreen(nav, viewModel)
                        Screen.Colors -> ColorsScreen(nav, viewModel)
                        Screen.Fonts -> FontsScreen(nav, viewModel)
                        Screen.SettingsAppearance -> AppearanceSettingsScreen(nav, viewModel)
                        Screen.SettingsLanguage -> LanguageSettingsScreen(nav, viewModel)
                        Screen.SettingsLibrary -> LibrarySettingsScreen(nav, viewModel)
                        Screen.SettingsLyrics -> LyricsSettingsScreen(nav, viewModel)
                        Screen.SettingsYourMix -> YourMixSettingsScreen(nav, viewModel)
                        Screen.SettingsAbout -> AboutSettingsScreen(nav, container)
                        Screen.SettingsBackup -> BackupSettingsScreen(nav, container)
                        Screen.SettingsAudio -> AudioSettingsScreen(nav, viewModel)
                        Screen.SettingsWidgets -> WidgetsSettingsScreen(nav, viewModel)
                        Screen.SettingsDownload -> DownloadScreen(nav, viewModel, container)
                        Screen.SettingsNavigation -> NavigationHubScreen(nav)
                        Screen.SettingsTabOrder -> TabOrderSettingsScreen(nav, viewModel)
                        Screen.SettingsGestures -> GesturesSettingsScreen(nav, viewModel)
                        Screen.SettingsScan -> ScanSettingsScreen(nav, viewModel)
                        Screen.SettingsTheme -> ThemeSettingsScreen(nav, viewModel)
                        Screen.SettingsEqualizer -> EqualizerSettingsScreen(nav, viewModel)
                        Screen.SettingsReplayGain -> ReplayGainSettingsScreen(nav, viewModel)
                        Screen.SettingsMixGeneral -> MixGeneralSettingsScreen(nav, viewModel)
                        Screen.SettingsMixStrategy -> MixStrategySettingsScreen(nav, viewModel)
                        Screen.SettingsMixOptions -> MixOptionsSettingsScreen(nav, viewModel)
                        Screen.YourMix -> YourMixScreen(nav, viewModel, menu)
                        Screen.TimeUsage -> TimeUsageScreen(nav, viewModel)
                        Screen.Top20 -> Top20Screen(nav, viewModel)
                    }
                }
            }
            // During a back gesture the previous screen must already be visible underneath the shrinking card (it used to
            // be composed only at commit, so the user first saw an empty backdrop). It is removed in the same frame in which
            // the normal back transition composes it (same save key, so scroll/tab state is handed over).
            val underlay: Screen? = if (leavingScreen != null && leavingScreen == nav.current && nav.stack.size > 1)
                nav.stack[nav.stack.size - 2] else null
            if (underlay != null) {
                Box(Modifier.fillMaxSize()) { renderScreen(underlay) }
            }
            AnimatedContent(
                targetState = nav.current,
                modifier = Modifier.fillMaxSize(),
                contentKey = { it.encode() },
                transitionSpec = {
                    if (navMotion.forward) {
                        Motion.screenEnter(true, reduceMotion) togetherWith Motion.screenExit(true, reduceMotion)
                    } else {
                        // Back: the previous screen sits fully visible underneath. The leaving screen is kept alive
                        // (alpha stays ~1) while the card animation slides it out; it is removed at the end.
                        // Gesture back: the card transform does the moving. Button back: the leaving screen slides
                        // off to the right itself, opaque, revealing the previous screen as it goes.
                        val exit = if (leavingScreen != null || reduceMotion) {
                            fadeOut(tween(Motion.SCREEN_BACK_MS), targetAlpha = 0.999f)
                        } else {
                            slideOutHorizontally(tween(Motion.SCREEN_BACK_MS, easing = Motion.EaseOut)) { it / 3 } +
                                fadeOut(tween(Motion.SCREEN_BACK_MS, easing = Motion.EaseOut))
                        }
                        (EnterTransition.None togetherWith exit).apply { targetContentZIndex = -1f }
                    }
                },
                label = "screen",
            ) { screen ->
                // Card look for the screen being left by a back gesture (and by the back button): corners, shadow and an
                // opaque base appear as soon as it starts to move; every other screen is untouched.
                val base = MaterialTheme.colorScheme.background
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (screen == leavingScreen && !reduceMotion) {
                                val p = backProgress.value
                                val s = 1f - 0.08f * p.coerceAtMost(1f)
                                scaleX = s
                                scaleY = s
                                translationX = p * size.width * 0.6f
                                val f = (p * 6f).coerceIn(0f, 1f)
                                shape = RoundedCornerShape((28.dp * f).toPx())
                                clip = f > 0f
                                shadowElevation = 12.dp.toPx() * f
                            }
                        }
                        .drawBehind {
                            // The screen being left on a back step is opaque from the very first frame, so the previous
                            // screen (already composed underneath) can never show through it before it moves away.
                            if (screen == leavingScreen || (screen != nav.current && !navMotion.forward)) {
                                drawRect(base)
                                // Same tonal gradient the window backdrop draws, so the card looks exactly like the
                                // page did a moment ago instead of turning plain black.
                                drawRect(
                                    artworkVerticalBrush(
                                        tone = backdropBase.value,
                                        strength = 0.45f,
                                        endY = 420.dp.toPx() / ArtworkGradient.FADE_END,
                                    ),
                                )
                            }
                        },
                ) {

                renderScreen(screen)
                }
            }
        }

        // Floating bottom bars: they stay fixed at the bottom but are translucent and sit on a soft gradient, so the
        // lists keep scrolling visibly underneath them instead of stopping at a solid black rectangle.
        val scrimBase = if (onLyrics && !pureMode.isOn) Color.Black else MaterialTheme.colorScheme.background
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    // The tab bar (with the mini player it carries) is pushed off the screen by the growing sheet, but only
                    // once the sheet's top edge has reached the screen top (SheetMorph.NAV_START). Draw phase only.
                    if (!reduceMotion) {
                        val p = SheetMorph.progress(sheet.offsetPx.value, sheet.extentPx)
                        val mb = miniBottomPx.takeIf { it > 0f } ?: (sheet.extentPx - size.height * 0.5f)
                        translationY = SheetMorph.frame(p, sheet.extentPx, 0f, mb, 0f, 0f).navShift
                    }
                }
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
                // Keep the last track so the bar still has content while it animates out (queue cleared / dismissed).
                var lastMiniNow by remember { mutableStateOf<NowPlaying?>(null) }
                playerState.nowPlaying?.let { lastMiniNow = it }
                AnimatedVisibility(
                    visible = playerState.nowPlaying != null,
                    enter = Motion.barEnter(reduceMotion),
                    exit = Motion.barExit(reduceMotion),
                ) {
                lastMiniNow?.let { now ->
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
                                        // The restore re-checks, on the controller thread, that the queue is still the
                                        // empty one left by this dismissal; a newer queue is never overwritten.
                                        val restored = viewModel.restoreQueue(snapshot)
                                        withTimeoutOrNull(5_000L) {
                                            snackbar.showSnackbar(
                                                if (restored) restoredText else undoFailedText,
                                                duration = SnackbarDuration.Indefinite,
                                            )
                                        }
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        },
                    )
                    // If the queue comes back (Undo) while the bar is still animating out, it must not stay swiped away.
                    LaunchedEffect(playerState.nowPlaying != null) {
                        if (playerState.nowPlaying != null) dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                    }
                    // One haptic tick when the swipe crosses the commit point.
                    LaunchedEffect(dismissState) {
                        snapshotFlow { dismissState.targetValue }.collect {
                            if (it == SwipeToDismissBoxValue.EndToStart) haptics.threshold()
                        }
                    }
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
                            border = if (pureMode.isOn) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            } else {
                                BorderStroke(1.dp, FloatingEdgeHighlight)
                            },
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .onGloballyPositioned {
                                    if (sheet.offsetPx.value >= sheet.extentPx - 0.5f) {
                                        val pos = it.positionInRoot()
                                        miniTopPx = pos.y
                                        miniBottomPx = pos.y + it.size.height
                                    }
                                }
                                // Drag UP anywhere on the bar: the full-screen player rises under the finger, 1:1.
                                // Only upward movement opens it (the offset is clamped at "closed"). The horizontal
                                // swipe-to-dismiss is a different axis; touch slop tells them apart.
                                .draggable(
                                    state = rememberDraggableState { delta -> sheet.dragBy(delta) },
                                    orientation = Orientation.Vertical,
                                    onDragStopped = { velocityY ->
                                        // draggable: velocityY is positive moving DOWN; settle wants the upward speed.
                                        sheet.settle(
                                            openedPx = sheet.extentPx - sheet.currentOffset,
                                            upwardVelocityPxPerSec = -velocityY,
                                        )
                                    },
                                ),
                        ) {
                            MiniPlayer(
                                now = now,
                                isPlaying = playerState.isPlaying,
                                hasNext = playerState.hasNext,
                                durationMs = playerState.durationMs,
                                positionMs = viewModel::currentPositionMs,
                                onOpen = { sheet.open() },
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onNext = viewModel::next,
                                onPrevious = viewModel::previous,
                            )
                        }
                    }
                }
                }
                // Bottom navigation only on the root (library) level; detail screens have their own back. The
                // navigation pads the system navigation area itself; on detail screens a spacer does it, so the
                // inset is applied exactly once either way.
                if (nav.stack.size == 1) {
                    LibraryBottomNav(
                        selected = tabOrder.getOrElse(pagerState.targetPage) { LibraryTab.fromOrdinal(tabIndex) },
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
    // Composed only while any part of it is on screen (nothing is composed or drawn when closed).
    if (sheet.isComposed) {
        // Dims the page while the sheet is pulled down into the bar; lifts as it lands (depth cue).
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val u = (sheet.offsetPx.value / sheet.extentPx).coerceIn(0f, 1f)
                alpha = if (reduceMotion) 0f else 0.32f * (1f - u)
            }.background(Color.Black),
        )
        NowPlayingScreen(
            miniTopPx = { miniTopPx },
            miniBottomPx = { miniBottomPx },
            viewModel = viewModel,
            tone = tone,
            sheet = sheet,
            onDismiss = { sheet.close() },
            // Queue and Lyrics are singleton screens (NavRules): opening them again returns to the existing entry
            // instead of stacking a second copy, so one back press always leaves them.
            onOpenQueue = {
                sheet.close()
                nav.push(Screen.Queue)
            },
            onOpenLyrics = {
                sheet.close()
                nav.push(Screen.Lyrics)
            },
            onMenuAction = { action, now ->
                feedback.scope.launch {
                    val track = now.mediaId.toLongOrNull()?.let { viewModel.trackItem(it) }
                    if (track == null) {
                        feedback.snackbar.showSnackbar(context.getString(R.string.song_not_in_library))
                        return@launch
                    }
                    when (action) {
                        NowPlayingAction.GoToAlbum -> {
                            val key = track.albumGroupKey
                            if (key == null) {
                                feedback.snackbar.showSnackbar(context.getString(R.string.no_album_for_song))
                            } else {
                                sheet.close()
                                nav.push(Screen.Album(key, track.album.orEmpty()))
                            }
                        }
                        NowPlayingAction.GoToArtist -> {
                            sheet.close()
                            nav.push(Screen.Artist(track.primaryArtistKey.orEmpty(), track.primaryArtist.orEmpty()))
                        }
                        NowPlayingAction.EditTags -> {
                            sheet.close()
                            nav.push(Screen.EditMetadata(track.id))
                        }
                        NowPlayingAction.EditLyrics -> {
                            sheet.close()
                            nav.push(Screen.LyricsEditor(track.id))
                        }
                        NowPlayingAction.EditCover -> {
                            sheet.close()
                            nav.push(Screen.EditCover(track.id))
                        }
                        NowPlayingAction.HideSong -> {
                            // The player is closed so the confirmation (with Undo) is visible; playback continues.
                            sheet.close()
                            menu.hideWithUndo(track)
                        }
                    }
                }
            },
        )
    }
}

/** Opacity of the floating bar surfaces (mini player, navigation pill). */
const val BAR_SURFACE_ALPHA = 0.88f
