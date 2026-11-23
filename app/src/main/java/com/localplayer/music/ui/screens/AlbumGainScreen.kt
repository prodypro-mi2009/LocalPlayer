package com.localplayer.music.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.localplayer.music.AppContainer
import com.localplayer.music.R
import com.localplayer.music.ui.AlbumGainViewModel
import com.localplayer.music.ui.GainPhase
import com.localplayer.music.ui.components.ChunkyProgress
import com.localplayer.music.ui.components.DetailTopBar
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.ui.nav.Screen

/** Album > ReplayGain of all songs: measure the album, look at the result, then write it into the files. */
@Composable
fun AlbumGainScreen(screen: Screen.GainAlbum, nav: NavState, container: AppContainer) {
    val vm: AlbumGainViewModel = viewModel(
        key = "album-gain-${screen.groupKey}",
        factory = viewModelFactory { initializer { AlbumGainViewModel(container, screen.groupKey) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        vm.onConsentResult(result.resultCode == android.app.Activity.RESULT_OK)
    }
    LaunchedEffect(vm) { vm.consent.collect { consentLauncher.launch(IntentSenderRequest.Builder(it).build()) } }

    val count = state.tracks?.size ?: 0
    val busy = state.phase == GainPhase.MEASURING || state.phase == GainPhase.WRITING
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = stringResource(R.string.gain_album_title), onBack = { if (!busy) nav.pop() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(screen.title.ifEmpty { stringResource(R.string.unknown_album) }, style = MaterialTheme.typography.titleMedium)
            Text(
                pluralStringResource(R.plurals.gain_album_note, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = vm::measure, enabled = !busy && count > 0, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.gain_album_measure))
            }
            if (state.phase == GainPhase.MEASURING && count > 0) {
                ChunkyProgress(state.progress.toFloat() / count)
                Text(pluralStringResource(R.plurals.bulk_edit_progress, count, state.progress, count), style = MaterialTheme.typography.bodySmall)
            }
            if (state.phase == GainPhase.READY || state.phase == GainPhase.WRITING || state.phase == GainPhase.DONE) {
                Text(
                    stringResource(R.string.gain_album_value, state.albumGain ?: "—"),
                    style = MaterialTheme.typography.titleSmall,
                )
                for (row in state.rows) {
                    Text(
                        "${row.track.title}   ${row.gain?.let { "$it dB" } ?: stringResource(R.string.gain_album_unmeasured)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (state.cancelled) Text(stringResource(R.string.err_cancelled), color = MaterialTheme.colorScheme.error)
            if (state.phase == GainPhase.WRITING && count > 0) {
                ChunkyProgress((state.done + state.failed).toFloat() / count)
                Text(pluralStringResource(R.plurals.bulk_edit_progress, count, state.done + state.failed, count), style = MaterialTheme.typography.bodySmall)
            }
            if (state.phase == GainPhase.DONE) {
                Text(stringResource(R.string.bulk_edit_result, state.done, state.failed), style = MaterialTheme.typography.bodyMedium)
                if (state.firstError.isNotEmpty()) {
                    Text(state.firstError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (state.phase == GainPhase.READY && state.albumGain != null) {
                Button(onClick = vm::write, modifier = Modifier.fillMaxWidth()) { Text(pluralStringResource(R.plurals.gain_album_write, count, count)) }
            }
        }
    }
}
