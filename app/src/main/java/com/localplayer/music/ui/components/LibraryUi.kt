package com.localplayer.music.ui.components

import com.localplayer.music.ui.theme.albumGenreFont
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localplayer.music.R
import com.localplayer.music.data.library.TextNormalizer
import com.localplayer.music.playback.ArtworkRef

enum class ViewMode { GRID, LIST }

/** "12 songs" / "1 song" (and the Greek equivalents), via a plurals resource. */
@Composable
fun songCountText(count: Int): String =
    LocalResources.current.getQuantityString(R.plurals.song_count, count, count)

class SortOption(val label: String, val selected: Boolean, val onSelect: () -> Unit)

/** Height of the action row of a library tab; lists start below it and scroll under it. */
val ActionRowHeight = 56.dp

/** Height of the soft fade under the action row on the main tabs. */
val TabFadeHeight = 52.dp

/** Stand-in distance for a scrolled list that has not been measured yet (any value above the pill threshold). */
private const val UnmeasuredScrolled = 100_000

/** True when the user is right-handed (back button on the right): the action pill then sits on the right side. */
val LocalPillOnRight = androidx.compose.runtime.compositionLocalOf { false }

/** True with the "Classic" look of the Shuffle row: the row stays at the top and the list starts below it. */
val LocalClassicActionRow = androidx.compose.runtime.compositionLocalOf { false }

/** Top content padding of a tab's list: room for the overlapping row, or just [extra] when the list starts below the row. */
@Composable
fun actionRowContentTop(extra: androidx.compose.ui.unit.Dp = 0.dp): androidx.compose.ui.unit.Dp =
    if (LocalClassicActionRow.current) extra else ActionRowHeight + extra

/** Classic look: the list starts below the row instead of scrolling under it. No effect with the pill. */
@Composable
fun Modifier.actionRowListInset(): Modifier = if (LocalClassicActionRow.current) padding(top = ActionRowHeight) else this

/** Rough distance scrolled, in px (first row size times rows passed, plus the offset into the first row). */
fun LazyListState.scrollEstimate(): Int {
    // Right after a tab is re-entered the layout is not measured yet: a restored, scrolled list must still count as scrolled.
    val first = layoutInfo.visibleItemsInfo.firstOrNull()
        ?: return if (firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0) UnmeasuredScrolled else 0
    return firstVisibleItemIndex * first.size + firstVisibleItemScrollOffset
}

/** Same for a two-column grid. */
fun LazyGridState.scrollEstimate(): Int {
    val first = layoutInfo.visibleItemsInfo.firstOrNull()
        ?: return if (firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0) UnmeasuredScrolled else 0
    return (firstVisibleItemIndex / 2) * first.size.height + firstVisibleItemScrollOffset
}

/** How far (in dp) the list has to scroll for the action row to be fully packed into its pill. */
val ActionRowScrollRange = 120.dp

/**
 * How far the scrolling has pulled the action row together: 0 at the top of the list, 1 once the list has moved
 * [ActionRowScrollRange]. It follows the scroll position directly (no timer), so it moves with the finger in both
 * directions. A restored, scrolled list starts at 1. Returned as a function so that only layout and drawing read it.
 */
@Composable
fun rememberActionRowProgress(position: () -> Int): () -> Float {
    val rangePx = with(androidx.compose.ui.platform.LocalDensity.current) { ActionRowScrollRange.toPx() }
    val classic = LocalClassicActionRow.current
    val current = androidx.compose.runtime.rememberUpdatedState(position)
    val state = remember(rangePx) {
        androidx.compose.runtime.derivedStateOf { (current.value().toFloat() / rangePx).coerceIn(0f, 1f) }
    }
    // Classic look: the row never packs into the pill.
    return remember(state, classic) { if (classic) ({ 0f }) else ({ state.value }) }
}

/**
 * The action row of a library tab: Shuffle, optional "locate current song", grid/list toggle and sort
 * menu. Parts a tab does not need are left out. It sits on top of the list. At rest Shuffle is on the left
 * and the icons on the right; while the list is scrolled they glide together into one pill, in step with the scrolling
 * ([progress], see [rememberActionRowProgress]).
 */
@Composable
fun LibraryActionRow(
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    viewMode: ViewMode? = null,
    onToggleView: () -> Unit = {},
    sortOptions: List<SortOption> = emptyList(),
    onLocate: (() -> Unit)? = null,
    progress: () -> Float = { 0f },
) {
    ActionRowLayout(progress = progress, modifier = modifier) {
        FilledTonalButton(onClick = onShuffle, colors = com.localplayer.music.ui.theme.tonalButtonColors()) {
            Icon(AppIcons.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.shuffle))
        }
        if (onLocate != null) {
            IconButton(onClick = onLocate, modifier = Modifier.size(48.dp)) {
                Icon(AppIcons.Locate, contentDescription = stringResource(R.string.locate_current))
            }
        }
        if (viewMode != null) {
            IconButton(onClick = onToggleView, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (viewMode == ViewMode.GRID) AppIcons.ViewList else AppIcons.ViewGrid,
                    contentDescription = stringResource(
                        if (viewMode == ViewMode.GRID) R.string.view_as_list else R.string.view_as_grid,
                    ),
                )
            }
        }
        if (sortOptions.isNotEmpty()) {
            var open by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp)) {
                    Icon(AppIcons.Sort, contentDescription = stringResource(R.string.sort_menu))
                }
                AppDropdownMenu(expanded = open, onDismiss = { open = false }) {
                    sortOptions.forEach { option ->
                        AppMenuItem(icon = null, label = option.label, selected = option.selected) {
                            open = false
                            option.onSelect()
                        }
                    }
                }
            }
        }
    }
}

/** Share of the progress in which the buttons glide together (0 .. this), and where the pill starts to show (this .. 1). */
private const val MoveEnd = 0.65f
private const val PillStart = 0.55f

/**
 * Lays the buttons of [content] out in a row. [progress] 0 = at rest (the first button on the left, the others against
 * the right edge); 1 = all packed together inside a pill (on the left, or on the right for right-handed use). [progress] is
 * read only while laying out and drawing, so following the scroll costs no recomposition. The buttons move closer in
 * proportion to it; the pill fades in around them while they arrive, and fades out again on the way back.
 */
@Composable
fun ActionRowLayout(progress: () -> Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val pillColor = MaterialTheme.colorScheme.surfaceContainerLow
    val pillBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val onRight = LocalPillOnRight.current
    androidx.compose.ui.layout.Layout(
        content = {
            androidx.compose.material3.Surface(
                modifier = Modifier.graphicsLayer {
                    val pillP = ((progress() - PillStart) / (1f - PillStart)).coerceIn(0f, 1f)
                    alpha = pillP
                    scaleX = 0.9f + 0.1f * pillP
                    scaleY = 0.9f + 0.1f * pillP
                },
                shape = CircleShape,
                color = pillColor,
                shadowElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, pillBorder),
            ) {}
            content()
        },
        modifier = modifier.fillMaxWidth().height(ActionRowHeight),
    ) { measurables, constraints ->
        val side = 12.dp.roundToPx()
        val pad = 8.dp.roundToPx()
        val gap = 4.dp.roundToPx()
        val full = constraints.maxWidth
        val h = ActionRowHeight.roundToPx()
        val items = measurables.drop(1).map { it.measure(androidx.compose.ui.unit.Constraints()) }
        val packedWidth = items.sumOf { it.width } + gap * (items.size - 1) + 2 * pad
        val pillHeight = 52.dp.roundToPx()
        val pill = measurables[0].measure(androidx.compose.ui.unit.Constraints.fixed(packedWidth.coerceAtLeast(0), pillHeight))
        layout(full, h) {
            val pillX = if (onRight) full - side - packedWidth else side
            pill.place(pillX, (h - pillHeight) / 2)
            // Where each item sits at rest (first left, the rest packed against the right edge) and when packed.
            var right = full - side
            val restX = IntArray(items.size)
            for (i in items.indices.reversed()) {
                if (i == 0) { restX[i] = side } else { right -= items[i].width; restX[i] = right }
            }
            val moveP = (progress() / MoveEnd).coerceIn(0f, 1f)
            var packedX = pillX + pad
            items.forEachIndexed { i, item ->
                val x = androidx.compose.ui.util.lerp(restX[i], packedX, moveP)
                item.place(x, (h - item.height) / 2)
                packedX += item.width + gap
            }
        }
    }
}

/** A song as a grid card (Songs tab in grid mode). */
@Composable
fun SongGridCard(
    song: com.localplayer.music.data.db.TrackListItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unknownArtist = stringResource(R.string.unknown_artist)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isCurrent) com.localplayer.music.ui.theme.currentRowColor(0.55f)
                else MaterialTheme.colorScheme.surfaceContainerLow,
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box {
            ArtworkImage(
                artwork = song.artworkRef(),
                size = 160.dp,
                cornerRadius = 14.dp,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            IconButton(onClick = onMore, modifier = Modifier.align(Alignment.TopEnd).size(48.dp)) {
                Icon(
                    AppIcons.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = Color.White,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape),
                )
            }
        }
        Text(
            song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            song.artist ?: unknownArtist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Icon of a genre card, chosen from the genre name (see [GenreKind]). Purely decorative. */
object GenreStyle {
    fun icon(name: String?): ImageVector = GenreKind.of(name)?.let { GenreIcons.of(it) } ?: AppIcons.MusicNote

    /** Stable pick (0..2) of one of three theme tones for this name. */
    fun tone(name: String?): Int = ((TextNormalizer.fold(name).hashCode() % 3) + 3) % 3
}

/** Up to four local artworks as a 2x2 collage (playlists). Empty slots show the neutral placeholder. */
@Composable
fun ArtworkCollage(artworks: List<ArtworkRef>, modifier: Modifier = Modifier) {
    val slots = artworks.take(4)
    Column(modifier.clip(RoundedCornerShape(16.dp))) {
        for (row in 0..1) {
            Row(Modifier.weight(1f)) {
                for (col in 0..1) {
                    val art = slots.getOrNull(row * 2 + col)
                    ArtworkImage(
                        artwork = art,
                        size = 96.dp,
                        cornerRadius = 0.dp,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/** The user's own playlist cover (a small square JPEG from [com.localplayer.music.data.artwork.PlaylistCoverStore]). */
@Composable
fun PlaylistCoverImage(coverFile: String, modifier: Modifier = Modifier) {
    val store = (LocalContext.current.applicationContext as com.localplayer.music.LocalPlayerApp).container.playlistCovers
    var bitmap by remember(coverFile) { mutableStateOf(store.peek(coverFile)) }
    androidx.compose.runtime.LaunchedEffect(coverFile) {
        if (bitmap == null) bitmap = store.load(coverFile)
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let {
            androidx.compose.foundation.Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Expressive header of a detail screen: large local artwork on a tonal gradient, heavy title, subtitle,
 * and the Play / Shuffle buttons.
 */
@Composable
fun DetailHero(
    title: String,
    subtitle: String,
    artwork: ArtworkRef?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    /** Replaces the single artwork (the playlist screen passes its collage here). */
    cover: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (cover != null) {
            cover()
        } else {
            ArtworkImage(
                artwork = artwork,
                size = 200.dp,
                cornerRadius = if (round) 100.dp else 44.dp,
            )
        }
        Text(
            text = title,
            style = com.localplayer.music.ui.theme.DisplayHeavyCard.copy(fontSize = 28.sp, lineHeight = 32.sp).albumGenreFont(),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.Button(onClick = onPlay) {
                Icon(AppIcons.Play, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.play))
            }
            FilledTonalButton(onClick = onShuffle, colors = com.localplayer.music.ui.theme.tonalButtonColors()) {
                Icon(AppIcons.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.shuffle))
            }
        }
    }
}


private val TopFadeHeight = 72.dp

/** Smoothstep alpha stops for a fade that is fully transparent at the top edge. */
private fun Modifier.topFadeImpl(fadeHeight: androidx.compose.ui.unit.Dp = TopFadeHeight, strength: (Float) -> Float): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = fadeHeight.toPx()
        val s = strength(h).coerceIn(0f, 1f)
        if (s > 0.01f) {
            val stops = Array(9) { i ->
                val t = i / 8f
                val smooth = t * t * (3f - 2f * t)
                t to Color.Black.copy(alpha = 1f - s * (1f - smooth))
            }
            drawRect(
                brush = Brush.verticalGradient(colorStops = stops, startY = 0f, endY = h),
                size = Size(size.width, h),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * Soft ~72dp fade at the top edge of a scrolling list, so content dissolves into the backdrop instead
 * of being cut by a hard line under the controls row. Strength follows the scroll offset, so a list at
 * rest is untouched.
 */
fun Modifier.topFade(state: LazyListState, height: androidx.compose.ui.unit.Dp = TopFadeHeight): Modifier = topFadeImpl(height) { h ->
    if (state.firstVisibleItemIndex > 0) 1f else state.firstVisibleItemScrollOffset / h
}

/** The same fade for a column that scrolls with [androidx.compose.foundation.verticalScroll] (the settings pages). */
fun Modifier.topFade(state: androidx.compose.foundation.ScrollState): Modifier = topFadeImpl { h -> state.value / h }

fun Modifier.topFade(state: LazyGridState, height: androidx.compose.ui.unit.Dp = TopFadeHeight): Modifier = topFadeImpl(height) { h ->
    if (state.firstVisibleItemIndex > 0) 1f else state.firstVisibleItemScrollOffset / h
}

/** A [androidx.compose.foundation.lazy.LazyColumn] with the soft top-edge fade ([topFade]) built in. */
@Composable
fun FadingLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
    fadeHeight: androidx.compose.ui.unit.Dp = TopFadeHeight,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.topFade(state, fadeHeight),
        state = state,
        contentPadding = contentPadding,
        content = content,
    )
}
