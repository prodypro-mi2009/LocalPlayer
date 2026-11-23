package com.localplayer.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localplayer.music.R
import com.localplayer.music.data.library.PlaylistItem

/**
 * Asks which playlist should get the songs that "Update playlist" found already in the library. A playlist that
 * already holds all of them (or that mirrors a folder and cannot be edited) is listed but greyed out and cannot be picked.
 */
@Composable
fun ExistingSongsDialog(
    count: Int,
    playlists: List<PlaylistItem>,
    holdingAll: Set<Long>,
    onPick: (PlaylistItem) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog(
            title = stringResource(R.string.new_playlist),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onConfirm = onCreate,
            onDismiss = { creating = false },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.dl_existing_title, count, count)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.dl_existing_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { creating = true }) {
                    Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.new_playlist))
                }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        val blocked = playlist.id in holdingAll || playlist.sourceFolderKey != null
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (blocked) 0.4f else 1f)
                                .then(if (blocked) Modifier else Modifier.clickable { onPick(playlist) })
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = when {
                                    playlist.sourceFolderKey != null -> stringResource(R.string.dl_existing_folder_linked)
                                    playlist.id in holdingAll -> stringResource(R.string.dl_existing_has_all)
                                    else -> pluralStringResource(R.plurals.track_count, playlist.trackCount, playlist.trackCount)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
