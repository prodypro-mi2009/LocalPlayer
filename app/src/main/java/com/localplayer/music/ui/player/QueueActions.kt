package com.localplayer.music.ui.player

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.localplayer.music.R
import com.localplayer.music.playback.QueueEntry
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.UiFeedback
import kotlinx.coroutines.launch

/**
 * The queue actions with their Undo snackbars, shared by the Queue screen and the Next-up sheet of Now Playing, so
 * the logic exists exactly once (remove one entry, "Clear upcoming", "Clear queue"). Behaviour is unchanged from the
 * code that used to live in QueueScreen.
 */
class QueueActions internal constructor(
    private val viewModel: LibraryViewModel,
    private val feedback: UiFeedback,
    private val removedText: String,
    private val clearedText: String,
    private val queueClearedText: String,
    private val undoFailedText: String,
    private val undoText: String,
) {
    fun remove(entry: QueueEntry) {
        feedback.scope.launch {
            // By occurrence id, so a re-order that happened meanwhile can never remove the wrong row.
            val removed = entry.occurrenceId?.let { viewModel.removeQueueEntry(it) }
                ?: viewModel.removeQueueItem(entry.index)
            if (removed != null) {
                val result = feedback.snackbar.showSnackbar(
                    message = removedText,
                    actionLabel = undoText,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.reinsertQueueItem(removed)
            }
        }
    }

    fun clearUpcoming() {
        feedback.scope.launch {
            val cleared = viewModel.clearUpcoming()
            if (cleared != null) {
                val result = feedback.snackbar.showSnackbar(
                    message = clearedText,
                    actionLabel = undoText,
                    duration = SnackbarDuration.Short,
                )
                // Undo only while the queue is exactly what Clear left behind.
                if (result == SnackbarResult.ActionPerformed && !viewModel.undoClearQueue(cleared)) {
                    feedback.snackbar.showSnackbar(message = undoFailedText, duration = SnackbarDuration.Short)
                }
            }
        }
    }

    /** Keeps ONLY the current song, untouched (same position and play state). */
    fun clearQueue() {
        feedback.scope.launch {
            val cleared = viewModel.clearQueueExceptCurrent()
            if (cleared != null) {
                val result = feedback.snackbar.showSnackbar(
                    message = queueClearedText,
                    actionLabel = undoText,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed && !viewModel.undoClearQueue(cleared)) {
                    feedback.snackbar.showSnackbar(message = undoFailedText, duration = SnackbarDuration.Short)
                }
            }
        }
    }
}

@Composable
fun rememberQueueActions(viewModel: LibraryViewModel, feedback: UiFeedback): QueueActions {
    val removedText = stringResource(R.string.queue_item_removed)
    val clearedText = stringResource(R.string.queue_upcoming_cleared)
    val queueClearedText = stringResource(R.string.queue_cleared)
    val undoFailedText = stringResource(R.string.undo_not_possible)
    val undoText = stringResource(R.string.undo)
    return remember(viewModel, feedback, removedText, clearedText, queueClearedText, undoFailedText, undoText) {
        QueueActions(viewModel, feedback, removedText, clearedText, queueClearedText, undoFailedText, undoText)
    }
}
