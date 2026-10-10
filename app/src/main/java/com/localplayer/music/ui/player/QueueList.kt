package com.localplayer.music.ui.player

import com.localplayer.music.ui.components.barPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.music.R
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.ArtworkImage
import com.localplayer.music.ui.components.EmptyState

/** The real Media3 playback queue (no copy of it is kept anywhere): tap to play, move up/down, remove. */
@Composable
fun QueueList(
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
    onRemove: (com.localplayer.music.playback.QueueEntry) -> Unit = { viewModel.removeQueueIndex(it.index) },
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.queueIndex.coerceAtLeast(0))

    if (queue.isEmpty()) {
        EmptyState(stringResource(R.string.queue_empty), modifier)
        return
    }
    // Stable key per queue ENTRY: its own occurrence id (the same file queued twice has two different ids, and
    // moving or removing another row never changes this row's identity). Legacy items without an id fall back to
    // "trackId", and any accidental repeat gets a counter suffix so keys are always unique.
    val keys = remember(queue) {
        val seen = HashMap<String, Int>()
        queue.map { entry ->
            val base = entry.occurrenceId ?: entry.mediaId
            val n = seen.merge(base, 1, Int::plus)!!
            if (n == 1) base else "$base#$n"
        }
    }
    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = barPadding(extra = 8.dp)) {
        itemsIndexed(queue, key = { i, _ -> keys[i] }) { _, entry ->
            val isCurrent = entry.index == state.queueIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.playQueueIndex(entry.index) }
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(artwork = entry.artwork, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.index > 0) {
                    IconButton(onClick = { viewModel.moveQueueItem(entry.index, entry.index - 1) }) {
                        Icon(AppIcons.ArrowUp, contentDescription = stringResource(R.string.move_up))
                    }
                }
                if (entry.index < queue.lastIndex) {
                    IconButton(onClick = { viewModel.moveQueueItem(entry.index, entry.index + 1) }) {
                        Icon(AppIcons.ArrowDown, contentDescription = stringResource(R.string.move_down))
                    }
                }
                IconButton(onClick = { onRemove(entry) }) {
                    Icon(
                        AppIcons.Close,
                        contentDescription = stringResource(R.string.remove_from_queue, entry.title),
                    )
                }
            }
        }
    }
}
