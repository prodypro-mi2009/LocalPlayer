package com.localplayer.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    /** Opt-in: the active line fills with colour across its length (an estimate, see [rememberLineFill]). */
    fill: Boolean = false,
    isPlaying: Boolean = false,
    positionMs: () -> Long = { 0L },
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
            val lineModifier = Modifier
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
                .padding(vertical = 10.dp)
            if (fill && active) {
                val text = line.text.ifEmpty { "♪" }
                val progress = rememberLineFill(
                    active = true,
                    startMs = line.timestampMs,
                    endMs = lines.getOrNull(index + 1)?.timestampMs ?: (line.timestampMs + 5_000L),
                    textLength = text.length,
                    isPlaying = isPlaying,
                    position = positionMs,
                )
                FilledLyricText(
                    text = text,
                    style = MaterialTheme.typography.titleLarge.lyricsFont(),
                    fontWeight = FontWeight.Medium,
                    textAlign = textAlign,
                    progress = { progress.value },
                    modifier = lineModifier,
                )
            } else {
                Text(
                    text = line.text.ifEmpty { "♪" },
                    style = MaterialTheme.typography.titleLarge.lyricsFont(),
                    fontWeight = FontWeight.Medium,
                    textAlign = textAlign,
                    modifier = lineModifier,
                )
            }
        }
    }
}

/**
 * 0 -> 1 across the line while it is the active one (OPT-IN feature). Lyrics are timed per LINE, not per word, so the speed
 * is an estimate: the line's duration, but no longer than about 110 ms per character (the rest is a pause). One animation
 * per line, started from the real playback position (also after a seek); paused while the music is paused. The value is read
 * in the draw phase only, so nothing recomposes per frame.
 */
@Composable
fun rememberLineFill(
    active: Boolean,
    startMs: Long,
    endMs: Long,
    textLength: Int,
    isPlaying: Boolean,
    position: () -> Long,
): androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(active, isPlaying, startMs, endMs, textLength) {
        if (!active) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        val lineMs = (endMs - startMs).coerceAtLeast(1L)
        val fillMs = minOf(lineMs, (textLength * 110L).coerceAtLeast(1_200L))
        val done = ((position() - startMs).toFloat() / fillMs).coerceIn(0f, 1f)
        progress.snapTo(done)
        if (isPlaying && done < 1f) {
            progress.animateTo(1f, tween(((1f - done) * fillMs).toInt().coerceAtLeast(1), easing = androidx.compose.animation.core.LinearEasing))
        }
    }
    return progress
}

/** The text drawn dim, with the same text drawn bright over the part reached by [progress] (line after line when it wraps). */
@Composable
fun FilledLyricText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    fontWeight: FontWeight,
    textAlign: TextAlign,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    var layout by remember(text) { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val bright = androidx.compose.material3.LocalContentColor.current
    Box(modifier) {
        Text(
            text = text,
            style = style,
            color = bright.copy(alpha = 0.55f),
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            onTextLayout = { layout = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = text,
            style = style,
            color = bright,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().drawWithContent {
                val l = layout ?: return@drawWithContent
                var total = 0f
                for (i in 0 until l.lineCount) total += l.getLineRight(i) - l.getLineLeft(i)
                var remaining = progress().coerceIn(0f, 1f) * total
                for (i in 0 until l.lineCount) {
                    val left = l.getLineLeft(i)
                    val width = l.getLineRight(i) - left
                    val take = remaining.coerceIn(0f, width)
                    if (take > 0f) {
                        clipRect(left = left, top = l.getLineTop(i), right = left + take, bottom = l.getLineBottom(i)) {
                            this@drawWithContent.drawContent()
                        }
                    }
                    remaining -= width
                }
            },
        )
    }
}

/**
 * Now Playing, OPT-IN (lyrics line above the progress bar): the current line of the SYNCHRONIZED lyrics of the playing song, in a
 * fixed-height slot (the controls never move). It reads the lyrics from the file once per song and wakes up only when the
 * next line is due (same mechanism as the lyrics page). Nothing is shown for songs without synchronized lyrics.
 */
@Composable
fun NowPlayingLyricLine(viewModel: LibraryViewModel, trackId: Long?, isPlaying: Boolean, fill: Boolean, modifier: Modifier = Modifier) {
    val revision by viewModel.lyricsRevision.collectAsStateWithLifecycle()
    val load by androidx.compose.runtime.produceState<LibraryViewModel.LyricsLoad?>(initialValue = null, trackId, revision) {
        value = null
        value = trackId?.let { viewModel.loadLyrics(it) }
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val lyrics = (load as? LibraryViewModel.LyricsLoad.Loaded)?.lyrics?.takeIf { it.isSynced }
        if (lyrics != null) {
            val active = rememberActiveLyricIndex(viewModel, lyrics.lines, isPlaying)
            val line = lyrics.lines.getOrNull(active)
            val text = line?.text.orEmpty()
            if (line != null && text.isNotBlank()) {
                // ONE line in the slot above the progress bar: a long line gets slightly smaller letters (by length, no
                // measuring), and only when that is still not enough it is cut with an ellipsis.
                val base = MaterialTheme.typography.titleMedium.lyricsFont()
                val style = when {
                    text.length > 56 -> base.copy(fontSize = 12.sp, lineHeight = 16.sp)
                    text.length > 42 -> base.copy(fontSize = 14.sp, lineHeight = 20.sp)
                    else -> base
                }
                if (fill) {
                    val progress = rememberLineFill(
                        active = true,
                        startMs = line.timestampMs,
                        endMs = lyrics.lines.getOrNull(active + 1)?.timestampMs ?: (line.timestampMs + 5_000L),
                        textLength = text.length,
                        isPlaying = isPlaying,
                        position = viewModel::currentPositionMs,
                    )
                    FilledLyricText(
                        text = text,
                        style = style,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        progress = { progress.value },
                        maxLines = 1,
                    )
                } else {
                    Text(
                        text = text,
                        style = style,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
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
 * plain background). Built from a 128 px thumbnail and blurred at that size once per song and strength; keyed by the
 * artwork so a slow result for the previous song is dropped.
 */
@Composable
fun rememberBlurredArtwork(artwork: ArtworkRef?, restoreLast: Boolean = false, level: Int = ArtworkBlur.TEXTURE_LEVEL): ImageBitmap? {
    val appContext = LocalContext.current.applicationContext
    val repository = (appContext as LocalPlayerApp).container.artwork
    // NOT reset when the song changes: the previous picture stays until the new one is ready, so the background
    // cross-fades from picture to picture and never dips to empty (that dip was the flash on Next / Previous).
    // The first value comes straight from the small in-memory cache, so coming back to the app (or reopening the
    // screen) shows the picture in the very first frame instead of an empty, dark background.
    // [restoreLast] (the whole-app background only; the user switched it on): after the system killed the app the
    // cache is empty, so the picture that was last shown is read back from one tiny file (128x128 px) and shown at once.
    var result by remember {
        mutableStateOf<ImageBitmap?>(
            artwork?.let { ArtworkBlur.cached("${it.trackId}_${it.lastModified}_$level")?.asImageBitmap() }
                ?: if (restoreLast) ArtworkBlur.loadLast(appContext)?.asImageBitmap() else null,
        )
    }
    LaunchedEffect(artwork, level) {
        if (artwork == null) {
            // The song info is briefly empty while the player reconnects after the app comes back to the front.
            // Keep the picture for a moment; only a lasting "no artwork" clears it.
            delay(if (restoreLast) 4_000 else 2_000)
            result = null
            // Nothing is playing (an empty queue): the next start must be plain black, not the old picture.
            if (restoreLast) withContext(Dispatchers.IO) { ArtworkBlur.clearLast(appContext) }
            return@LaunchedEffect
        }
        val key = "${artwork.trackId}_${artwork.lastModified}_$level"
        val blurred = ArtworkBlur.cached(key) ?: withContext(Dispatchers.Default) {
            val small = repository.load(artwork.trackId, artwork.sourceUri, artwork.lastModified, 128)
            small?.let { ArtworkBlur.blurredSmall(it, level) }?.also { ArtworkBlur.remember(key, it) }
        }
        if (blurred != null) {
            result = blurred.asImageBitmap()
            if (restoreLast) withContext(Dispatchers.IO) { ArtworkBlur.saveLast(appContext, key, blurred) }
        }
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
