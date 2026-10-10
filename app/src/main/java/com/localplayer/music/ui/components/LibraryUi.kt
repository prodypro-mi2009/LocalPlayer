package com.localplayer.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
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
    LocalContext.current.resources.getQuantityString(R.plurals.song_count, count, count)

class SortOption(val label: String, val selected: Boolean, val onSelect: () -> Unit)

/**
 * The action row of a library tab: Shuffle, optional "locate current song", grid/list toggle and sort
 * menu. Parts a tab does not need are left out.
 */
@Composable
fun LibraryActionRow(
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    viewMode: ViewMode? = null,
    onToggleView: () -> Unit = {},
    sortOptions: List<SortOption> = emptyList(),
    onLocate: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(onClick = onShuffle) {
            Icon(AppIcons.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.shuffle))
        }
        Spacer(Modifier.weight(1f))
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
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    sortOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
                                    fontWeight = if (option.selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (option.selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            },
                            onClick = {
                                open = false
                                option.onSelect()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fast scrollbar: a column of the letters that occur (Latin, Greek, #). Tap or drag on the thin strip
 * to jump; the rest of the screen is untouched, so normal vertical scrolling is unaffected.
 */
@Composable
fun FastScrollbar(
    letters: List<Char>,
    onLetter: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.size < 2) return
    var heightPx by remember { mutableStateOf(0) }
    val pick: (Float) -> Unit = { y ->
        if (heightPx > 0) {
            val index = ((y / heightPx) * letters.size).toInt().coerceIn(0, letters.lastIndex)
            onLetter(letters[index])
        }
    }
    Column(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight()
            .onSizeChanged { heightPx = it.height }
            .pointerInput(letters) { detectTapGestures { offset -> pick(offset.y) } }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> pick(offset.y) },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        pick(change.position.y)
                    },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = letter.toString(),
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
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
                if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
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

/** Icon of a genre card, chosen from the genre name. Purely decorative. */
object GenreStyle {
    fun icon(name: String?): ImageVector {
        val n = TextNormalizer.fold(name)
        return when {
            n.isEmpty() -> AppIcons.MusicNote
            listOf("rock", "metal", "punk", "grunge", "indie", "alternative").any { n.contains(it) } -> AppIcons.Album
            listOf("pop", "hip", "rap", "soul", "folk", "vocal", "singer").any { n.contains(it) } -> AppIcons.Mic
            listOf("electro", "dance", "house", "techno", "trance", "edm", "dubstep", "ambient").any { n.contains(it) } ->
                AppIcons.GraphicEq
            else -> AppIcons.MusicNote
        }
    }

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

/** Floating round back button for the bottom-left corner of detail screens. */
@Composable
fun BottomBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.back)
    Box(
        modifier = modifier
            .padding(bottom = LocalBottomBarInset.current)
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClickLabel = label, onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.ArrowBack, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
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
            style = com.localplayer.music.ui.theme.DisplayHeavyCard.copy(fontSize = 28.sp, lineHeight = 32.sp),
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
            FilledTonalButton(onClick = onShuffle) {
                Icon(AppIcons.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.shuffle))
            }
        }
    }
}
