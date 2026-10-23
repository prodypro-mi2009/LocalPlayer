package com.localplayer.music.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.music.R
import com.localplayer.music.data.db.TrackListItem
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.ui.nav.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Snackbar access for the whole app. Strings are passed already resolved (callers use string resources).
 */
class UiFeedback(val snackbar: SnackbarHostState, val scope: CoroutineScope)

/**
 * Holds the state of the track menu sheet and its dialogs, and builds the row actions.
 * One instance lives at the app root; [Dialogs] is rendered once there. Every track list uses it,
 * so the menu logic exists exactly once.
 */
class SongMenuController(
    private val viewModel: LibraryViewModel,
    private val nav: NavState,
    private val feedback: UiFeedback,
    private val strings: (Int) -> String,
) {
    private var menuTrack by mutableStateOf<TrackListItem?>(null)
    private var playlistTargetUris by mutableStateOf<List<String>?>(null)

    fun actionsFor(
        song: TrackListItem,
        onRemove: (() -> Unit)? = null,
        removeLabel: String? = null,
    ) = SongActions(
        onMore = { menuTrack = song },
        onRemove = onRemove,
        removeLabel = removeLabel,
        onSwipeToQueue = { addToQueueEndWithUndo(song) },
        onSwipeToPlayNext = { playNextWithUndo(song) },
    )

    /**
     * Swipe-left action of EVERY track row: append this track to the END of the queue (one new occurrence),
     * with an Undo that removes exactly that occurrence. Never starts playback, never touches the current item.
     */
    fun addToQueueEndWithUndo(song: TrackListItem) {
        feedback.scope.launch {
            val added = viewModel.appendToQueueEnd(song)
            val result = feedback.snackbar.showSnackbar(
                message = strings(R.string.moved_to_end_of_queue),
                actionLabel = strings(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && !viewModel.undoAppend(added)) {
                feedback.snackbar.showSnackbar(message = strings(R.string.undo_not_possible), duration = SnackbarDuration.Short)
            }
        }
    }

    /**
     * Swipe-right action of EVERY track row: insert this track as the NEXT song (right after the current one and
     * after earlier "Play next" entries), with an Undo that removes exactly that occurrence. Never starts playback
     * and never touches the current item.
     */
    fun playNextWithUndo(song: TrackListItem) {
        feedback.scope.launch {
            val added = viewModel.insertNextInQueue(song)
            val result = feedback.snackbar.showSnackbar(
                message = strings(R.string.added_play_next),
                actionLabel = strings(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && !viewModel.undoAppend(added)) {
                feedback.snackbar.showSnackbar(message = strings(R.string.undo_not_possible), duration = SnackbarDuration.Short)
            }
        }
    }

    /** Hides the song from the library (file untouched) and offers Undo. */
    fun hideWithUndo(song: TrackListItem) {
        feedback.scope.launch {
            viewModel.hideTrack(song.uri)
            val result = feedback.snackbar.showSnackbar(
                message = strings(R.string.song_hidden),
                actionLabel = strings(R.string.undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.unhideTrack(song.uri)
        }
    }

    @Composable
    fun Dialogs() {
        val track = menuTrack
        if (track != null) {
            TrackMenuSheet(
                track = track,
                onDismiss = { menuTrack = null },
                actions = TrackMenuActions(
                    onPlayNext = { playNextWithUndo(track) },
                    onAddToQueue = { addToQueueEndWithUndo(track) },
                    onAddToPlaylist = { playlistTargetUris = listOf(track.uri) },
                    onGoToQueue = { nav.push(Screen.Queue) },
                    onGoToAlbum = {
                        val key = track.albumGroupKey
                        if (key != null) {
                            nav.push(Screen.Album(key, track.album.orEmpty()))
                        } else {
                            feedback.scope.launch { feedback.snackbar.showSnackbar(strings(R.string.no_album_for_song)) }
                        }
                    },
                    onGoToArtist = {
                        nav.push(Screen.Artist(track.primaryArtistKey.orEmpty(), track.primaryArtist.orEmpty()))
                    },
                    onEditTag = { nav.push(Screen.EditMetadata(track.id)) },
                    onEditLyrics = { nav.push(Screen.LyricsEditor(track.id)) },
                    onHide = { hideWithUndo(track) },
                ),
            )
        }
        val uris = playlistTargetUris
        if (uris != null) {
            val playlists by viewModel.playlists.collectAsStateWithLifecycle()
            AddToPlaylistDialog(
                playlists = playlists.orEmpty(),
                onPick = { playlist ->
                    viewModel.addToPlaylist(playlist, uris)
                    playlistTargetUris = null
                },
                onCreate = { name ->
                    viewModel.createPlaylist(name, thenAdd = uris)
                    playlistTargetUris = null
                },
                onDismiss = { playlistTargetUris = null },
            )
        }
    }
}
