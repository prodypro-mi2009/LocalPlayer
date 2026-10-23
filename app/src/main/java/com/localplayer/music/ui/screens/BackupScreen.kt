package com.localplayer.music.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.localplayer.music.AppContainer
import com.localplayer.music.R
import com.localplayer.music.ui.BackupUi
import com.localplayer.music.ui.BackupViewModel
import com.localplayer.music.ui.nav.NavState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun groupLabel(group: String): Int = when (group) {
    "appearance" -> R.string.backup_group_appearance
    "library" -> R.string.backup_group_library
    "navigation" -> R.string.backup_group_navigation
    "lyrics" -> R.string.backup_group_lyrics
    "yourMix" -> R.string.backup_group_your_mix
    "updates" -> R.string.backup_group_updates
    "widgets" -> R.string.backup_group_widgets
    "replayGain" -> R.string.backup_group_replaygain
    else -> R.string.backup_group_equalizer
}

/** Settings > Backup & Restore. Uses the system file picker only (no storage permission). */
@Composable
fun BackupSettingsScreen(nav: NavState, container: AppContainer) {
    val vm: BackupViewModel = viewModel(
        key = "backup",
        factory = viewModelFactory { initializer { BackupViewModel(container) } },
    )
    val ui by vm.state.collectAsStateWithLifecycle()
    val stamp = remember { SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date()) }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.export(it, false) } }
    val exportFull = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { vm.export(it, true) } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.choose(it) } }

    SettingsPage(R.string.settings_backup, nav) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.backup_scope), style = MaterialTheme.typography.bodyMedium)

            Text(stringResource(R.string.backup_export), style = MaterialTheme.typography.titleMedium)
            Button(onClick = { exportJson.launch("LocalPlayer-settings-$stamp.json") }, enabled = ui !is BackupUi.Working && ui !is BackupUi.Preview) {
                Text(stringResource(R.string.backup_export_json))
            }
            Text(stringResource(R.string.backup_export_json_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { exportFull.launch("LocalPlayer-$stamp.lpbackup") }, enabled = ui !is BackupUi.Working && ui !is BackupUi.Preview) {
                Text(stringResource(R.string.backup_export_complete))
            }
            Text(stringResource(R.string.backup_export_complete_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(stringResource(R.string.backup_import), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { import.launch(arrayOf("*/*")) }, enabled = ui !is BackupUi.Working && ui !is BackupUi.Preview) {
                Text(stringResource(R.string.backup_import_choose))
            }
            Text(stringResource(R.string.backup_import_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            when (val s = ui) {
                BackupUi.Idle -> Unit
                BackupUi.Working -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(); Text(stringResource(R.string.backup_working))
                }
                is BackupUi.Done -> {
                    val text = if (s.arg > 0) stringResource(s.messageRes, s.arg.toString()) else stringResource(s.messageRes)
                    Text(text, color = if (s.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                    if (s.detail.isNotBlank()) Text(s.detail, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = vm::dismiss) { Text(stringResource(R.string.ok)) }
                }
                is BackupUi.Preview -> {
                    Text(stringResource(R.string.backup_preview), style = MaterialTheme.typography.titleMedium)
                    val plan = s.plan
                    if (plan.changes.isEmpty()) Text(stringResource(R.string.backup_preview_nothing))
                    plan.changes.groupBy { it.group }.forEach { (group, items) ->
                        Text(stringResource(groupLabel(group)) + ": " + items.joinToString(", ") { it.key }, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (plan.restoreBackground) Text(stringResource(R.string.backup_preview_background))
                    if (plan.restoreFont) Text(stringResource(R.string.backup_preview_font))
                    if (!s.isArchive) Text(stringResource(R.string.backup_preview_json_noassets), style = MaterialTheme.typography.bodySmall)
                    if (plan.keptActivation.isNotEmpty()) {
                        Text(stringResource(R.string.backup_preview_kept, plan.keptActivation.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
                    }
                    if (plan.rejected.isNotEmpty() || plan.unknown.isNotEmpty()) {
                        Text(
                            stringResource(R.string.backup_preview_skipped, (plan.rejected.keys + plan.unknown).joinToString(", ")),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = s.restoreActivation, onCheckedChange = vm::setRestoreActivation)
                        Column {
                            Text(stringResource(R.string.backup_restore_activation))
                            Text(stringResource(R.string.backup_restore_activation_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::confirm) { Text(stringResource(R.string.backup_apply)) }
                        OutlinedButton(onClick = vm::cancel) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }
        }
    }
}
