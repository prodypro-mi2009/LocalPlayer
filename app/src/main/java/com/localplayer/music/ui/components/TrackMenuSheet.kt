package com.localplayer.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localplayer.music.R
import com.localplayer.music.data.db.TrackListItem

/** Everything the track menu can do. Each callback is invoked AFTER the sheet has been closed. */
class TrackMenuActions(
    val onPlayNext: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onAddToPlaylist: () -> Unit,
    val onGoToQueue: () -> Unit,
    val onGoToAlbum: () -> Unit,
    val onGoToArtist: () -> Unit,
    val onEditTag: () -> Unit,
    val onEditLyrics: () -> Unit,
    val onHide: () -> Unit,
)

/**
 * The ONE track menu used by every list that shows tracks. Fixed order:
 *   Play next / Add to queue / Add to playlist
 *   ---
 *   Go to queue / Go to album / Go to artist
 *   ---
 *   Edit tag / Edit lyrics
 *   ---
 *   Hide song (library only: the file is not deleted)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuSheet(track: TrackListItem, actions: TrackMenuActions, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val unknownArtist = stringResource(R.string.unknown_artist)

    @Composable
    fun Entry(icon: ImageVector, label: String, action: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onDismiss()
                    action()
                }
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(20.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(artwork = track.artworkRef(), size = 48.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        track.artist ?: unknownArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Entry(AppIcons.SkipNext, stringResource(R.string.play_next), actions.onPlayNext)
            Entry(AppIcons.Queue, stringResource(R.string.add_to_queue), actions.onAddToQueue)
            Entry(AppIcons.Add, stringResource(R.string.add_to_playlist), actions.onAddToPlaylist)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Entry(AppIcons.Queue, stringResource(R.string.go_to_queue), actions.onGoToQueue)
            Entry(AppIcons.Album, stringResource(R.string.go_to_album), actions.onGoToAlbum)
            Entry(AppIcons.Person, stringResource(R.string.go_to_artist), actions.onGoToArtist)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Entry(AppIcons.Edit, stringResource(R.string.edit_tag), actions.onEditTag)
            Entry(AppIcons.Lyrics, stringResource(R.string.edit_lyrics), actions.onEditLyrics)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Entry(AppIcons.VisibilityOff, stringResource(R.string.hide_song), actions.onHide)
        }
    }
}
