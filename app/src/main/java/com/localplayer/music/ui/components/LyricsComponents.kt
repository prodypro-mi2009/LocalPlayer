package com.localplayer.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.localplayer.music.LocalPlayerApp
import com.localplayer.music.R
import com.localplayer.music.data.artwork.ArtworkBlur
import com.localplayer.music.data.lyrics.LyricLine
import com.localplayer.music.data.lyrics.LyricsSync
import com.localplayer.music.playback.ArtworkRef
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.theme.Motion
import com.localplayer.music.ui.theme.lyricsFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Index of the active synchronized line, computed from the REAL Media3 position (no timer of its own).
 * While playing it wakes up exactly when the next line is due (never later than every 500 ms, so a
 * missed seek corrects itself); while paused it does nothing; seeks and song changes re-sync at once.
 */
@Composable
fun rememberActiveLyricIndex(viewModel: LibraryViewModel, lines: List<LyricLine>, isPlaying: Boolean): Int {
    val jumps by viewModel.positionJumps.collectAsStateWithLifecycle()
    var index by remember(lines) { mutableIntStateOf(-1) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lines, isPlaying, jumps, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val position = viewModel.currentPositionMs()
                index = LyricsSync.activeIndex(lines, position)
                if (!isPlaying) break
                val wait = LyricsSync.msUntilNextLine(lines, position) ?: break
                delay(wait.coerceIn(30L, 500L))
            }
        }
    }
    return index
}

/** Synchronized lyrics: active line highlighted, smooth auto-scroll, tap a line to seek. */
@Composable
fun SyncedLyricsList(
    lines: List<LyricLine>,
    activeIndex: Int,
    follow: Boolean,
    onUserScroll: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    val listState = rememberLazyListState()
    var programmatic by remember { mutableStateOf(false) }

    // Follow the active line (centered-ish). Skipped while the user is browsing manually.
    LaunchedEffect(activeIndex, follow, lines) {
        if (follow && activeIndex >= 0) {
            programmatic = true
            try {
                listState.animateScrollToItem(activeIndex, scrollOffset = -(listState.layoutInfo.viewportSize.height / 3))
            } finally {
                programmatic = false
            }
        }
    }
    // A manual drag switches follow mode off, so automatic scrolling never fights the user.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress && !programmatic }.collect { manual -> if (manual) onUserScroll() }
    }

    val reduceMotion = com.localplayer.music.ui.theme.LocalReduceMotion.current
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 160.dp, end = 24.dp, bottom = 160.dp + LocalBottomBarInset.current),
    ) {
        itemsIndexed(lines, key = { index, line -> "$index-${line.timestampMs}" }) { index, line ->
            val active = index == activeIndex
            // 0 → 1 as this line becomes the active one. Read only inside graphicsLayer, so no recomposition per frame.
            val emphasis = animateFloatAsState(
                targetValue = if (active) 1f else 0f,
                animationSpec = tween(Motion.LYRIC_ACTIVE_MS, easing = Motion.EaseOut),
                label = "lyric-emphasis",
            )
            // Grow from the edge the text is aligned to, so the line does not drift sideways.
            val origin = when (textAlign) {
                TextAlign.Center -> TransformOrigin(0.5f, 0.5f)
                TextAlign.End, TextAlign.Right -> TransformOrigin(1f, 0.5f)
                else -> TransformOrigin(0f, 0.5f)
            }
            Text(
                text = line.text.ifEmpty { "♪" },
                style = MaterialTheme.typography.titleLarge.lyricsFont(),
                fontWeight = FontWeight.Medium,
                textAlign = textAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val e = emphasis.value
                        alpha = lerp(0.45f, 1f, e)
                        val s = if (reduceMotion) 1f else lerp(1f, 1.05f, e)
                        scaleX = s
                        scaleY = s
                        transformOrigin = origin
                    }
                    .clickable { onSeek(line.timestampMs) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
fun PlainLyrics(text: String, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.lyricsFont(),
        textAlign = textAlign,
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp + LocalBottomBarInset.current),
    )
}

/**
 * Heavily blurred copy of the CURRENT artwork, or null when it cannot be produced (the caller then uses a
 * plain background). Built from a 128 px thumbnail and blurred at 40x40 pixels once per song; keyed by the
 * artwork so a slow result for the previous song is dropped.
 */
@Composable
fun rememberBlurredArtwork(artwork: ArtworkRef?): ImageBitmap? {
    val repository = (LocalContext.current.applicationContext as LocalPlayerApp).container.artwork
    var result by remember(artwork) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(artwork) {
        if (artwork == null) return@LaunchedEffect
        val key = "${artwork.trackId}_${artwork.lastModified}"
        val blurred = ArtworkBlur.cached(key) ?: withContext(Dispatchers.Default) {
            val small = repository.load(artwork.trackId, artwork.sourceUri, artwork.lastModified, 128)
            small?.let { ArtworkBlur.blurredSmall(it) }?.also { ArtworkBlur.remember(key, it) }
        }
        result = blurred?.asImageBitmap()
    }
    return result
}

/** Opacity of the blurred artwork texture on the lyrics page. */
const val BLURRED_TEXTURE_ALPHA = 0.30f

@Composable
fun BlurredBackdrop(image: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        if (image != null) {
            // A faint texture only; the darkening is done once, uniformly, by the lyrics page (not a 62 % black block).
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = BLURRED_TEXTURE_ALPHA,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun FollowButtonHint(visible: Boolean, onClick: () -> Unit) {
    if (!visible) return
    Box(
        Modifier.fillMaxSize().padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp + LocalBottomBarInset.current),
        contentAlignment = Alignment.BottomCenter,
    ) {
        androidx.compose.material3.FilledTonalButton(onClick = onClick, colors = com.localplayer.music.ui.theme.tonalButtonColors()) { Text(stringResource(R.string.lyrics_follow)) }
    }
}
