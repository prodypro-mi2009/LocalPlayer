package com.localplayer.music.ui

import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.onSizeChanged
import com.localplayer.music.ui.components.LocalBottomBarInset
import com.localplayer.music.ui.theme.ArtworkGradient
import com.localplayer.music.ui.theme.Motion
import com.localplayer.music.ui.theme.artworkVerticalBrush
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import com.localplayer.music.ui.components.FloatingEdgeHighlight
import com.localplayer.music.ui.components.drawBlurredCover
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.localplayer.music.ui.theme.SheetMorph
import com.localplayer.music.ui.screens.DownloadScreen
import com.localplayer.music.ui.screens.NavigationHubScreen
import com.localplayer.music.ui.screens.TabOrderSettingsScreen
import com.localplayer.music.ui.screens.ActionRowSettingsScreen
import com.localplayer.music.ui.screens.BackButtonSettingsScreen
import com.localplayer.music.ui.screens.GesturesSettingsScreen
import com.localplayer.music.data.personalization.BackButtonSide
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import com.localplayer.music.ui.components.RecoveryPrompt
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
import com.localplayer.music.ui.screens.EditMetadataScreen
import com.localplayer.music.ui.screens.AlbumGainScreen
import com.localplayer.music.ui.screens.GainLibraryScreen
import com.localplayer.music.ui.screens.BulkEditScreen
import com.localplayer.music.ui.screens.DuplicatesScreen
import com.localplayer.music.ui.screens.HiddenSongsScreen
import com.localplayer.music.ui.screens.PlaybackSettingsScreen
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

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun AppRoot(viewModel: LibraryViewModel) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val currentResources by rememberUpdatedState(resources)
    var audioGranted by remember { mutableStateOf(MediaAccess.audioGranted(context)) }

    // Set when the system refused without a dialog (denied for good): the Grant button cannot work any more.
    var permanentlyDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        audioGranted = MediaAccess.audioGranted(context)
        if (audioGranted) {
            viewModel.rescan()
        } else {
            val activity = context.findActivity()
            val audioPermission = MediaAccess.requiredPermissions().first()
            permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, audioPermission)
        }
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
                currentResources.getString(message.res, *message.args.toTypedArray()),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    if (!audioGranted) {
        PermissionScreen(
            permanentlyDenied = permanentlyDenied,
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
private fun PermissionScreen(permanentlyDenied: Boolean, onGrant: () -> Unit, onOpenSettings: () -> Unit) {
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
        if (permanentlyDenied) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.permission_denied_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) { Text(stringResource(R.string.grant_access)) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
    }
}

/**
 * One layer of the screen stack. It stays composed while it is on top, right below the top one, or leaving.
 *  - The top screen opens as the card of a back step played in reverse (a push): it grows from the right and loses its
 *    rounded corners, over [Motion.SCREEN_OPEN_MS].
 *  - The screen it covered stays fully visible and at rest underneath, and is hidden once the card covers it.
 *  - The screen being left by a back step is a card that follows [backProgress] (shrinks, rounds, slides right).
 *  - The screen under the card ([revealed]) is fully visible and at rest from the first frame.
 * A layer that is hidden also takes no touches and is hidden from accessibility.
 */
@Composable
private fun NavLayer(
    transition: Transition<Screen>,
    screen: Screen,
    isTop: Boolean,
    revealed: Boolean,
    leaving: Boolean,
    reduceMotion: Boolean,
    backProgress: () -> Float,
    drawCard: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val topness by transition.animateFloat(
        transitionSpec = {
            tween(Motion.SCREEN_OPEN_MS, easing = Motion.EaseOut)
        },
        label = "layer",
    ) { if (it == screen) 1f else 0f }
    val hidden = !isTop && !revealed && !leaving
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                // The card transform is shared: a screen being left follows [backProgress] (0 -> 1.75), and a screen being
                // opened plays the same movement in REVERSE (1.75 -> 0), so opening and going back look like one gesture.
                fun card(p: Float) {
                    val s = 1f - 0.08f * p.coerceAtMost(1f)
                    scaleX = s
                    scaleY = s
                    translationX = p * size.width * 0.6f
                    val f = (p * 6f).coerceIn(0f, 1f)
                    shape = RoundedCornerShape((28.dp * f).toPx())
                    clip = f > 0f
                    shadowElevation = 12.dp.toPx() * f
                }
                if (leaving) {
                    val p = backProgress()
                    if (reduceMotion) alpha = (1f - p).coerceIn(0f, 1f) else card(p)
                } else if (!revealed) {
                    if (reduceMotion) {
                        // Reduced motion: plain cross-fade, no movement.
                        alpha = if (isTop) topness else (topness / 0.6f).coerceAtMost(1f)
                    } else if (isTop) {
                        card(1.75f * (1f - topness))
                    } else {
                        // The screen it covers stays fully visible and at rest until the opening card has covered it.
                        alpha = if (topness > 0f) 1f else 0f
                    }
                }
            }
            .drawBehind { if (leaving || (isTop && !reduceMotion && topness < 1f)) drawCard() }
            .then(if (hidden) Modifier.clearAndSetSemantics { } else Modifier),
    ) {
        content()
        if (hidden) {
            // Under another screen: it must not take touches.
            Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                },
            )
        }
    }
}

@Composable
private fun LibraryRoot(viewModel: LibraryViewModel) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val container = (context.applicationContext as LocalPlayerApp).container
    val nav = rememberSaveable(saver = NavState.Saver) { NavState() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Offers the original file back after an interrupted tag save (looks once, a few seconds after the app opened).
    val recovery: RecoveryViewModel = composeViewModel(
        factory = viewModelFactory { initializer { RecoveryViewModel(container) } },
    )
    RecoveryPrompt(recovery)
    val feedback = remember(snackbar, scope) { UiFeedback(snackbar, scope) }
    val menu = remember(viewModel, nav, feedback, resources) {
        SongMenuController(viewModel, nav, feedback) { id -> resources.getString(id) }
    }
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val updateAvailable by viewModel.updateAvailable.collectAsStateWithLifecycle()
    val backSide by container.settings.backButtonSide.collectAsStateWithLifecycle()
    val actionRowStyle by container.settings.actionRowStyle.collectAsStateWithLifecycle()
    val actionRowSide by container.settings.actionRowSide.collectAsStateWithLifecycle()
    // The Now Playing sheet is driven by ONE shared state (position, springs, drag); `wasOpen` is only what
    // survives rotation / recreation, so the player is still open afterwards.
    var wasOpen by rememberSaveable { mutableStateOf(false) }
    val sheet = rememberPlayerSheetState(initiallyOpen = wasOpen)
    LaunchedEffect(sheet) { snapshotFlow { sheet.isOpen }.collect { wasOpen = it } }
    val reduceMotion = LocalReduceMotion.current
    val haptics = rememberHaptics()
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

    // Back (gesture, back button and the on-screen arrows all end up in the same place): the leaving screen becomes a
    // "card" that shrinks a little, rounds its corners and slides right, while the screen below it is ALREADY composed and
    // simply shows. Nothing is rebuilt at the moment of the back step, so nothing can flash.
    val backProgress = remember { Animatable(0f) }
    // The screen a back gesture is dragging (before it is committed, [NavState.popped] is still empty).
    var gestureLeaving by remember { mutableStateOf<Screen?>(null) }
    // [0] a gesture is in progress, [1] the gesture was committed (the card continues from where the finger left it).
    val gesture = remember { booleanArrayOf(false, false) }
    PredictiveBackHandler(enabled = nav.stack.size > 1) { progress ->
        gesture[0] = true
        gesture[1] = false
        nav.clearPopped()
        backProgress.snapTo(0f)
        gestureLeaving = nav.current
        try {
            progress.collect { backProgress.snapTo(it.progress) }
            gesture[0] = false
            gesture[1] = true
            nav.pop()
        } catch (e: CancellationException) {
            gesture[0] = false
            scope.launch {
                backProgress.animateTo(0f, Motion.SettleCritical)
                gestureLeaving = null
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
        // Remember every screen seen so far at once: if the stack changes again during the delay below, this run is
        // cancelled, and a screen that came and went quickly must still be known to the next run (else it leaks).
        knownKeys = knownKeys + stackKeys
        // A popped screen is still on screen while it animates out; dropping its saved state now would let it
        // save itself again when it leaves, so the next visit would not start fresh.
        delay(Motion.SCREEN_CLEANUP_MS.toLong())
        (knownKeys - stackKeys).forEach { stateHolder.removeState(it) }
        knownKeys = stackKeys
    }
    // Runs the slide-out of a screen that was just popped. A gesture continues from where the finger left the card; any other
    // back step (button, arrow in a screen) starts the card from rest. When it is over (or a new push / gesture
    // cancels it) the popped screen is released and the card transform is reset.
    LaunchedEffect(nav.popped) {
        if (nav.popped == null) {
            if (!gesture[0]) {
                backProgress.snapTo(0f)
                gesture[1] = false
                // A new push cancelled the slide-out of a back step that came from the gesture handler: its screen must
                // be released too, or it stays "leaving" and is drawn on top of whatever opens next.
                gestureLeaving = null
            }
            return@LaunchedEffect
        }
        if (!gesture[1]) backProgress.snapTo(0f)
        if (reduceMotion) backProgress.animateTo(1f, tween(150)) else backProgress.animateTo(1.75f, tween(Motion.SCREEN_BACK_MS, easing = Motion.BackEase))
        gestureLeaving = null
        nav.clearPopped()
    }
    // A screen that is no longer on top must not keep the keyboard: Search opens it by itself, and the screens now stay
    // composed under the next one.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(nav.current) {
        if (nav.current != Screen.Search) focusManager.clearFocus(force = true)
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
    // "Blurred cover": the picture is made only when this choice is on AND something shows it (Now Playing is open,
    // or the user also wants it behind the whole app). Otherwise nothing is loaded or blurred.
    val artworkBlur by container.settings.artworkBlur.collectAsStateWithLifecycle()
    val blurEverywhere by container.settings.blurEverywhere.collectAsStateWithLifecycle()
    val blurStrength by container.settings.blurStrength.collectAsStateWithLifecycle()
    val blurOn = artworkBlur && !pureMode.isOn
    val blurred = com.localplayer.music.ui.components.rememberBlurredArtwork(
        if (blurOn && (blurEverywhere || sheet.isComposed || nav.current == Screen.Lyrics)) playerState.nowPlaying?.artwork else null,
        restoreLast = blurOn && blurEverywhere,
        level = blurStrength,
    )
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
    var miniTopPx by remember { mutableFloatStateOf(0f) }
    var miniBottomPx by remember { mutableFloatStateOf(0f) }
    val barInset = with(density) { barsHeightPx.toDp() }
    val onLyrics = nav.current == Screen.Lyrics
    // While the keyboard is open the floating bars must not float above it (a big empty strip over the keys).
    val imeOpen = WindowInsets.ime.getBottom(density) > 0

    Box(Modifier.fillMaxSize()) {
        background?.takeIf { !pureMode.isOn }?.let {
            Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = backgroundDim)))
        }
        // ONE continuous tonal backdrop for the whole window, status bar included. Every screen's top
        // controls sit on this same layer, so there is no separate strip behind the Settings button and nothing is
        // re-drawn when the tab changes. Same hue in every stop (see ArtworkGradient): no grey smear.
        if (blurOn && blurEverywhere) {
            com.localplayer.music.ui.components.BlurredCoverBackground(blurred, com.localplayer.music.ui.components.APP_BLUR_VEIL)
        } else Box(
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
            com.localplayer.music.ui.components.LocalFloatingBack provides (backSide != BackButtonSide.OFF),
            com.localplayer.music.ui.components.LocalPillOnRight provides when (actionRowSide) {
                com.localplayer.music.data.personalization.ActionRowSide.LEFT -> false
                com.localplayer.music.data.personalization.ActionRowSide.RIGHT -> true
            },
            com.localplayer.music.ui.components.LocalClassicActionRow provides
                (actionRowStyle == com.localplayer.music.data.personalization.ActionRowStyle.CLASSIC),
            com.localplayer.music.ui.components.LocalRowGestures provides
                com.localplayer.music.ui.components.RowGestures(swipeOn, queueButtonOn),
        ) {
            // One place that draws a screen (with its saved scroll/tab state).
            val renderScreen: @Composable (Screen) -> Unit = { screen ->
                stateHolder.SaveableStateProvider(screen.encode()) {
                    when (screen) {
                        Screen.Home -> HomeScreen(viewModel, nav, menu, tabOrder, pagerState)
                        Screen.Search -> SearchScreen(nav, viewModel, menu)
                        Screen.Recent -> RecentScreen(nav, viewModel, menu)
                        Screen.Settings -> SettingsScreen(nav, viewModel)
                        Screen.Queue -> QueueScreen(nav, viewModel, feedback)
                        Screen.Lyrics -> LyricsScreen(nav, viewModel, tone, blurMode = blurOn, blurredCover = blurred)
                        is Screen.LyricsEditor -> LyricsEditorScreen(screen.trackId, nav, viewModel, container)
                        is Screen.LyricsSearch -> LyricsSearchScreen(screen.trackId, nav, viewModel, container)
                        is Screen.Artist -> ArtistScreen(screen, nav, viewModel, menu)
                        is Screen.Album -> AlbumScreen(screen, nav, viewModel, menu)
                        is Screen.Genre -> GenreScreen(screen, nav, viewModel, menu)
                        is Screen.Year -> YearScreen(screen, nav, viewModel, menu)
                        is Screen.Playlist -> PlaylistScreen(screen, nav, viewModel, menu)
                        is Screen.AddTracks -> AddTracksScreen(screen, nav, viewModel)
                        is Screen.EditMetadata -> EditMetadataScreen(screen.trackId, nav, container)
                        is Screen.TrackInfo -> com.localplayer.music.ui.screens.TrackInfoScreen(screen.trackId, nav, viewModel)
                        Screen.HiddenSongs -> HiddenSongsScreen(nav, viewModel)
                        Screen.Duplicates -> DuplicatesScreen(nav, viewModel)
                        is Screen.BulkEditAlbum -> BulkEditScreen(screen, nav, container)
                        is Screen.GainAlbum -> AlbumGainScreen(screen, nav, container)
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
                        Screen.SettingsSpotify -> com.localplayer.music.ui.screens.SpotifyPlaylistScreen(nav, viewModel)
                        Screen.SettingsJam -> if (com.localplayer.music.jam.JamSwitch.ENABLED) com.localplayer.music.ui.screens.JamScreen(nav, container) else Unit
                        Screen.SettingsNavigation -> NavigationHubScreen(nav)
                        Screen.SettingsTabOrder -> TabOrderSettingsScreen(nav, viewModel)
                        Screen.SettingsGestures -> GesturesSettingsScreen(nav, viewModel)
                        Screen.SettingsBackButton -> BackButtonSettingsScreen(nav, viewModel)
                        Screen.SettingsActionRow -> ActionRowSettingsScreen(nav, viewModel)
                        Screen.SettingsScan -> ScanSettingsScreen(nav, viewModel)
                        Screen.SettingsTheme -> ThemeSettingsScreen(nav, viewModel)
                        Screen.SettingsEqualizer -> EqualizerSettingsScreen(nav, viewModel)
                        Screen.SettingsPlayback -> PlaybackSettingsScreen(nav, viewModel)
                        Screen.SettingsReplayGain -> ReplayGainSettingsScreen(nav, viewModel)
                        Screen.GainLibrary -> GainLibraryScreen(nav, container)
                        Screen.PeakLibrary -> GainLibraryScreen(nav, container, peaks = true)
                        Screen.SettingsMixGeneral -> MixGeneralSettingsScreen(nav, viewModel)
                        Screen.SettingsMixStrategy -> MixStrategySettingsScreen(nav, viewModel)
                        Screen.SettingsMixOptions -> MixOptionsSettingsScreen(nav, viewModel)
                        Screen.YourMix -> YourMixScreen(nav, viewModel, menu)
                        Screen.TimeUsage -> TimeUsageScreen(nav, viewModel)
                        Screen.Top20 -> Top20Screen(nav, viewModel)
                    }
                }
            }
            // The screens are LAYERS that stay composed: the library (Home), the screen below the top one, the top one and
            // the one that is just leaving. Going back therefore reveals a screen that is already there (scroll position,
            // covers and all), instead of building it in the middle of the slide, and opening a screen never rebuilds the
            // one it opens over. Only the layers at the very end of the list are dropped (after the animation).
            val transition = updateTransition(nav.current, label = "screen")
            val below = nav.stack.getOrNull(nav.stack.size - 2)
            // A dragged screen counts as leaving only while it is still the top one (drag in progress or being cancelled);
            // a leftover value can never put an old screen on top of a new one.
            val leaving: Screen? = nav.popped ?: gestureLeaving?.takeIf { it == nav.current }
            // The screen right under the card: it is simply visible and at rest, whatever the transition says.
            val revealed: Screen? = when {
                leaving == null -> null
                leaving == nav.current -> below
                else -> nav.current
            }
            val layers = buildList {
                below?.let { add(it) }
                add(nav.current)
                leaving?.let { add(it) }
                // NOT transition.currentState: after quick open / back / open steps it still names an old screen, which
                // was drawn on top of the new one and showed for a moment (e.g. the Library settings instead of Lyrics).
            }.filter { it != Screen.Home }.distinct()

            val base = MaterialTheme.colorScheme.background
            val drawCard: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                // The screen being left is opaque from the very first frame, so the screen underneath can never show through.
                drawRect(base)
                if (blurOn && blurEverywhere) {
                    // "Blurred cover behind the whole app": the card shows the same picture and veil.
                    drawBlurredCover(blurred, base.copy(alpha = com.localplayer.music.ui.components.APP_BLUR_VEIL))
                } else {
                    // Same tonal gradient the window backdrop draws, so the card looks exactly like the page did a moment ago.
                    drawRect(
                        artworkVerticalBrush(
                            tone = backdropBase.value,
                            strength = 0.45f,
                            endY = 420.dp.toPx() / ArtworkGradient.FADE_END,
                        ),
                    )
                }
            }
            val layerList = listOf(Screen.Home) + layers
            for (screen in layerList) {
                key(screen.encode()) {
                    NavLayer(
                        transition = transition,
                        screen = screen,
                        isTop = screen == nav.current,
                        revealed = screen == revealed,
                        leaving = screen == leaving,
                        reduceMotion = reduceMotion,
                        backProgress = { backProgress.value },
                        drawCard = drawCard,
                    ) { renderScreen(screen) }
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
                    // The tab bar (with the mini player it carries) is pushed off the screen by the sheet's bottom edge, which
                    // eases down while the top edge rises and has covered it when the top is at 90 % (see SheetMorph).
                    // Draw phase only.
                    if (!reduceMotion) {
                        val p = SheetMorph.progress(sheet.offsetPx.value, sheet.extentPx)
                        val mb = miniBottomPx.takeIf { it > 0f } ?: (sheet.extentPx - size.height * 0.5f)
                        translationY = SheetMorph.frame(p, sheet.extentPx, 0f, mb, 0f, 0f).navShift
                    }
                    if (imeOpen) translationY = size.height * 2f // off-screen: invisible and not touchable
                }
                .onSizeChanged { barsHeightPx = it.height },
        ) {
            // Height of the floating back button's slot while it opens / closes. The soft scrim starts BELOW that slot, so the
            // scrim keeps its own shape and does not stretch with the button (read only while drawing: no recomposition).
            var backSlotPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
            Box(
                Modifier.matchParentSize().drawBehind {
                    val top = backSlotPx.toFloat().coerceIn(0f, size.height)
                    if (size.height - top > 0f) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to scrimBase.copy(alpha = 0f),
                                    0.30f to scrimBase.copy(alpha = 0.55f),
                                    1f to scrimBase.copy(alpha = 0.82f),
                                ),
                                startY = top,
                                endY = size.height,
                            ),
                            topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                            size = androidx.compose.ui.geometry.Size(size.width, size.height - top),
                        )
                    }
                },
            )
            Column(Modifier.padding(top = 20.dp)) {
                // One-handed use: the back button floats above the mini player, on the side of the user's thumb.
                // The button stays where it is while one screen slides out and the next stays (it only hides when the last
                // screen above the tabs is left). Its slot opens and closes without clipping the button, and the button
                // fades, so it is not drawn from the bottom up. While a screen is sliding out the button does nothing, so a
                // second quick press can never start another back step in the middle of the first one.
                AnimatedVisibility(
                    modifier = Modifier.onSizeChanged { backSlotPx = it.height },
                    visible = backSide != BackButtonSide.OFF && nav.stack.size > 1,
                    enter = expandVertically(
                        tween(if (reduceMotion) 0 else Motion.SCREEN_IN_MS, easing = Motion.EaseOut),
                        expandFrom = Alignment.Bottom,
                        clip = false,
                    ) + androidx.compose.animation.fadeIn(tween(if (reduceMotion) 0 else Motion.SCREEN_IN_MS)),
                    exit = shrinkVertically(
                        tween(if (reduceMotion) 0 else Motion.SCREEN_IN_MS, easing = Motion.EaseOut),
                        shrinkTowards = Alignment.Bottom,
                        clip = false,
                    ) + androidx.compose.animation.fadeOut(tween(if (reduceMotion) 0 else Motion.SCREEN_IN_MS)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                        horizontalArrangement = if (backSide == BackButtonSide.LEFT) Arrangement.Start else Arrangement.End,
                    ) {
                        com.localplayer.music.ui.components.FloatingBackButton(onClick = { if (nav.popped == null) nav.pop() })
                    }
                }
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
                                    // Nothing to dismiss (queue already empty): no "dismissed" message, no useless Undo.
                                    val snapshot = viewModel.dismissQueue() ?: return@launch
                                    val result = withTimeoutOrNull(5_000L) {
                                        snackbar.showSnackbar(
                                            message = dismissedText,
                                            actionLabel = undoText,
                                            duration = SnackbarDuration.Indefinite,
                                        )
                                    }
                                    if (result == SnackbarResult.ActionPerformed) {
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
                            shape = RoundedCornerShape(20.dp),
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
                // The bar grows / shrinks over the same time as the screen slide (no fade, no sudden jump of the
                // height every list is padded by), instead of switching on the very first frame of the transition.
                val back = tween<androidx.compose.ui.unit.IntSize>(if (reduceMotion) 0 else Motion.SCREEN_BACK_MS, easing = Motion.BackEase)
                val quick = tween<androidx.compose.ui.unit.IntSize>(if (reduceMotion) 0 else Motion.SCREEN_IN_MS, easing = Motion.EaseOut)
                AnimatedVisibility(
                    visible = nav.stack.size == 1,
                    enter = expandVertically(back, expandFrom = Alignment.Top),
                    exit = shrinkVertically(quick, shrinkTowards = Alignment.Top),
                ) {
                    LibraryBottomNav(
                        selected = tabOrder.getOrElse(pagerState.targetPage) { LibraryTab.fromOrdinal(tabIndex) },
                        order = tabOrder,
                        onSelect = { tabIndex = it.ordinal },
                        onSearch = { nav.push(Screen.Search) },
                        onSettings = { nav.push(Screen.Settings) },
                        showUpdateDot = updateAvailable,
                    )
                }
                AnimatedVisibility(
                    visible = nav.stack.size != 1,
                    enter = expandVertically(quick, expandFrom = Alignment.Top),
                    exit = shrinkVertically(back, shrinkTowards = Alignment.Top),
                ) {
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
            blurMode = blurOn,
            blurred = blurred,
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
                        feedback.snackbar.showSnackbar(resources.getString(R.string.song_not_in_library))
                        return@launch
                    }
                    when (action) {
                        NowPlayingAction.GoToAlbum -> {
                            val key = track.albumGroupKey
                            if (key == null) {
                                feedback.snackbar.showSnackbar(resources.getString(R.string.no_album_for_song))
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
                        NowPlayingAction.Info -> {
                            sheet.close()
                            nav.push(Screen.TrackInfo(track.id))
                        }
                        NowPlayingAction.EditLyrics -> {
                            sheet.close()
                            nav.push(Screen.LyricsEditor(track.id))
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
