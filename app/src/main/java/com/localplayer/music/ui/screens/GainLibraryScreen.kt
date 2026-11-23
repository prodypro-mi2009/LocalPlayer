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
import com.localplayer.music.ui.GainLibraryViewModel
import com.localplayer.music.ui.LibraryGainPhase
import com.localplayer.music.ui.components.ChunkyProgress
import com.localplayer.music.ui.components.DetailTopBar
import com.localplayer.music.ui.components.SettingsGroup
import com.localplayer.music.ui.components.SettingsSwitchRow
import com.localplayer.music.ui.nav.NavState

/** Settings > ReplayGain > whole library: find what is missing, then Start (can be stopped any time). */
@Composable
fun GainLibraryScreen(nav: NavState, container: AppContainer, peaks: Boolean = false) {
    val vm: GainLibraryViewModel = viewModel(
        key = if (peaks) "peak-library" else "gain-library",
        factory = viewModelFactory { initializer { GainLibraryViewModel(container, peaks) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        vm.onConsentResult(result.resultCode == android.app.Activity.RESULT_OK)
    }
    LaunchedEffect(vm) { vm.consent.collect { consentLauncher.launch(IntentSenderRequest.Builder(it).build()) } }

    val phase = state.phase
    val busy = phase == LibraryGainPhase.LOOKING || phase == LibraryGainPhase.RUNNING
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = stringResource(if (peaks) R.string.peak_lib_title else R.string.gain_lib_title), onBack = { if (!busy) nav.pop() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(if (peaks) R.string.peak_lib_note else R.string.gain_lib_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (peaks) {
                SettingsGroup(note = stringResource(R.string.peak_lib_remeasure_note)) {
                    item {
                        SettingsSwitchRow(
                            title = stringResource(R.string.peak_lib_remeasure),
                            checked = state.remeasure,
                            onChange = vm::setRemeasure,
                            enabled = !busy,
                        )
                    }
                }
            }
            FilledTonalButton(onClick = vm::look, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(if (peaks) R.string.peak_lib_look else R.string.gain_lib_look))
            }
            if (phase == LibraryGainPhase.LOOKING && state.total > 0) {
                ChunkyProgress(state.looked.toFloat() / state.total)
                Text(pluralStringResource(R.plurals.gain_lib_looking, state.total, state.looked, state.total), style = MaterialTheme.typography.bodySmall)
            }
            if (phase == LibraryGainPhase.READY || phase == LibraryGainPhase.RUNNING || phase == LibraryGainPhase.DONE) {
                Text(stringResource(if (peaks) R.string.peak_lib_found else R.string.gain_lib_found, state.songs, state.albums), style = MaterialTheme.typography.titleSmall)
            }
            if (phase == LibraryGainPhase.READY) {
                if (state.songs > 0) {
                    Button(onClick = vm::start, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.gain_lib_start)) }
                } else {
                    Text(stringResource(if (peaks) R.string.peak_lib_nothing else R.string.gain_lib_nothing))
                }
            }
            if (phase == LibraryGainPhase.RUNNING) {
                ChunkyProgress(if (state.albums > 0) state.albumsDone.toFloat() / state.albums else 0f)
                Text(pluralStringResource(R.plurals.gain_lib_running, state.albums, state.albumsDone, state.albums), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.bulk_edit_result, state.songsDone, state.failed), style = MaterialTheme.typography.bodySmall)
                if (peaks) PrecisionLine(state.floatSongs, state.shortSongs)
                if (state.current.isNotEmpty()) Text(state.current, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = vm::stop, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.gain_lib_stop)) }
            }
            if (phase == LibraryGainPhase.DONE) {
                if (state.cancelled) Text(stringResource(R.string.err_cancelled), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.bulk_edit_result, state.songsDone, state.failed), style = MaterialTheme.typography.bodyMedium)
                if (peaks) PrecisionLine(state.floatSongs, state.shortSongs)
                if (state.firstError.isNotEmpty()) {
                    Text(state.firstError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** How the songs were measured: float samples give the true peak, 16-bit samples only a lower limit (cut off at 1.0). */
@Composable
private fun PrecisionLine(floatSongs: Int, shortSongs: Int) {
    if (floatSongs + shortSongs == 0) return
    Text(
        stringResource(R.string.peak_lib_precision, floatSongs, shortSongs),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
