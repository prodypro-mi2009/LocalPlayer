package com.localplayer.music.ui.screens

import com.localplayer.music.ui.nav.Screen
import com.localplayer.music.ui.components.AppIcons
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.music.R
import com.localplayer.music.data.effects.EqModel
import com.localplayer.music.data.effects.EqPreset
import com.localplayer.music.data.effects.ReplayGainMode
import com.localplayer.music.playback.AudioEffectsHub
import com.localplayer.music.playback.EqStatus
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.nav.NavState

@Composable
private fun SwitchRow(title: Int, note: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun presetLabel(p: EqPreset): Int = when (p) {
    EqPreset.FLAT -> R.string.eq_flat
    EqPreset.BASS_BOOST -> R.string.eq_bass
    EqPreset.ROCK -> R.string.eq_rock
    EqPreset.POP -> R.string.eq_pop
    EqPreset.VOCAL -> R.string.eq_vocal
    EqPreset.CUSTOM -> R.string.eq_custom
}

private fun bandLabel(hz: Int): String = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"

/** Settings > Audio: ReplayGain and the equaliser. Both are off until switched on here. */
@Composable
fun AudioSettingsScreen(nav: NavState, @Suppress("UNUSED_PARAMETER") viewModel: LibraryViewModel) = SettingsHub(
    R.string.settings_audio, nav,
    listOf(
        SettingsEntry(R.string.eq_title, R.string.settings_card_eq, AppIcons.GraphicEq, Color(0xFF7A5A12)) { nav.push(Screen.SettingsEqualizer) },
        SettingsEntry(R.string.rg_title, R.string.settings_card_rg, AppIcons.VolumeUp, Color(0xFF2F6B7A)) { nav.push(Screen.SettingsReplayGain) },
    ),
)

@Composable
fun ReplayGainSettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val s = viewModel.settings
    val rgOn by s.replayGainEnabled.collectAsStateWithLifecycle()
    val rgMode by s.replayGainMode.collectAsStateWithLifecycle()
    val rgClip by s.replayGainPreventClipping.collectAsStateWithLifecycle()
    SettingsPage(R.string.rg_title, nav) {
        // ---- ReplayGain
        SwitchRow(R.string.rg_title, R.string.rg_note, rgOn, viewModel::setReplayGainEnabled)
        if (rgOn) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = rgMode == ReplayGainMode.TRACK,
                        onClick = { viewModel.setReplayGainMode(ReplayGainMode.TRACK) },
                        label = { Text(stringResource(R.string.rg_track)) },
                    )
                    FilterChip(
                        selected = rgMode == ReplayGainMode.ALBUM,
                        onClick = { viewModel.setReplayGainMode(ReplayGainMode.ALBUM) },
                        label = { Text(stringResource(R.string.rg_album)) },
                    )
                }
                Text(stringResource(R.string.rg_mode_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SwitchRow(R.string.rg_clip, R.string.rg_clip_note, rgClip, viewModel::setReplayGainPreventClipping)
            Text(
                stringResource(R.string.rg_limits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerSettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val s = viewModel.settings
    val eqOn by s.equalizerEnabled.collectAsStateWithLifecycle()
    val preset by s.eqPreset.collectAsStateWithLifecycle()
    val custom by s.eqCustom.collectAsStateWithLifecycle()
    val status by AudioEffectsHub.eqStatus.collectAsStateWithLifecycle()
    SettingsPage(R.string.eq_title, nav) {
        // ---- Equalizer
        SwitchRow(R.string.eq_title, R.string.eq_note, eqOn, viewModel::setEqualizerEnabled)
        if (eqOn) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val st = status) {
                    is EqStatus.Active -> Text(
                        stringResource(R.string.eq_device_bands, st.centersHz.size.toString(), st.centersHz.joinToString(", ") { "$it Hz" }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EqStatus.Unavailable -> Text(stringResource(R.string.eq_unavailable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    EqStatus.Off -> Text(stringResource(R.string.eq_waiting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EqPreset.entries.forEach { p ->
                        FilterChip(selected = preset == p, onClick = { viewModel.setEqPreset(p) }, label = { Text(stringResource(presetLabel(p))) })
                    }
                }
                val shown = preset.gainsDb(custom)
                EqModel.BAND_HZ.forEachIndexed { i, hz ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bandLabel(hz), Modifier.weight(0.25f), style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = shown[i],
                            onValueChange = { viewModel.setEqCustomBand(i, Math.round(it * 2) / 2f) },
                            valueRange = -EqModel.MAX_DB..EqModel.MAX_DB,
                            modifier = Modifier.weight(0.5f),
                        )
                        Text("%+.1f dB".format(java.util.Locale.ROOT, shown[i]), Modifier.weight(0.25f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(onClick = viewModel::resetEqualizer) { Text(stringResource(R.string.eq_reset)) }
                Text(stringResource(R.string.eq_five_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Settings > Widgets: the master switch of the home-screen widgets. */
@Composable
fun WidgetsSettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val on by viewModel.settings.widgetsEnabled.collectAsStateWithLifecycle()
    SettingsPage(R.string.settings_widgets, nav) {
        SwitchRow(R.string.widgets_title, R.string.widgets_note, on, viewModel::setWidgetsEnabled)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.widgets_how), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.widgets_off_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
