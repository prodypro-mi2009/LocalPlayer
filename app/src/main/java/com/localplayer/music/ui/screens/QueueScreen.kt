package com.localplayer.music.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.localplayer.music.R
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.DetailTopBar
import com.localplayer.music.ui.components.UiFeedback
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.ui.player.QueueList
import kotlinx.coroutines.launch

/** The current playback queue (the real Media3 queue). Remove and Clear upcoming offer Undo. */
@Composable
fun QueueScreen(nav: NavState, viewModel: LibraryViewModel, feedback: UiFeedback) {
    val removedText = stringResource(R.string.queue_item_removed)
    val clearedText = stringResource(R.string.queue_upcoming_cleared)
    val undoText = stringResource(R.string.undo)
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(
            title = stringResource(R.string.queue),
            onBack = { nav.pop() },
            actions = {
                TextButton(
                    onClick = {
                        feedback.scope.launch {
                            val removed = viewModel.clearUpcoming()
                            if (removed.isNotEmpty()) {
                                val result = feedback.snackbar.showSnackbar(
                                    message = clearedText,
                                    actionLabel = undoText,
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) viewModel.reinsertQueueItems(removed)
                            }
                        }
                    },
                ) { Text(stringResource(R.string.queue_clear_upcoming)) }
                TextButton(onClick = viewModel::clearQueue) { Text(stringResource(R.string.clear_queue)) }
            },
        )
        QueueList(
            viewModel = viewModel,
            onRemove = { entry ->
                feedback.scope.launch {
                    val removed = viewModel.removeQueueItem(entry.index)
                    if (removed != null) {
                        val result = feedback.snackbar.showSnackbar(
                            message = removedText,
                            actionLabel = undoText,
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.reinsertQueueItem(removed)
                    }
                }
            },
        )
    }
}
