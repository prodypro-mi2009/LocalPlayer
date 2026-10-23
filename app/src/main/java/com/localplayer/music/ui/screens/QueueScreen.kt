package com.localplayer.music.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.localplayer.music.ui.player.rememberQueueActions

/** The current playback queue (the real Media3 queue). Remove and Clear upcoming offer Undo. */
@Composable
fun QueueScreen(nav: NavState, viewModel: LibraryViewModel, feedback: UiFeedback) {
    // Same actions (and Undo logic) as the Next-up sheet in Now Playing.
    val actions = rememberQueueActions(viewModel, feedback)
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(
            title = stringResource(R.string.queue),
            onBack = { nav.pop() },
            actions = {
                TextButton(onClick = actions::clearUpcoming) { Text(stringResource(R.string.queue_clear_upcoming)) }
                TextButton(onClick = actions::clearQueue) { Text(stringResource(R.string.clear_queue)) }
            },
        )
        QueueList(viewModel = viewModel, onRemove = actions::remove)
    }
}
