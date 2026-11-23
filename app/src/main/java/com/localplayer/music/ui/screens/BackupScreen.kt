package com.localplayer.music.ui.screens

import com.localplayer.music.ui.components.SettingsCustomRow
import com.localplayer.music.ui.components.SettingsGroup
import com.localplayer.music.ui.components.SettingsNote
import com.localplayer.music.ui.components.SettingsScaffold
import com.localplayer.music.ui.components.SettingsSwitchRow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import com.localplayer.music.data.backup.LibrarySection
import com.localplayer.music.ui.BackupUi
import com.localplayer.music.ui.BackupViewModel
import com.localplayer.music.ui.nav.NavState
import android.net.Uri
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
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { vm.setAutoBackupFolder(it) } }
    val autoOn by container.settings.autoBackupEnabled.collectAsStateWithLifecycle()
    val autoFolder by container.settings.autoBackupFolder.collectAsStateWithLifecycle()
    val androidCopy by container.settings.androidBackupCopy.collectAsStateWithLifecycle()
    val lastFolder by vm.lastFolderBackup.collectAsStateWithLifecycle()
    val lastAndroid by vm.lastAndroidCopy.collectAsStateWithLifecycle()
    val restoredCopy by vm.restoredCopy.collectAsStateWithLifecycle()
    fun whenText(ms: Long) = if (ms <= 0L) null else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

    SettingsScaffold(title = stringResource(R.string.settings_backup), onBack = { nav.pop() }) {
        SettingsNote(stringResource(R.string.backup_scope))
        SettingsGroup(title = stringResource(R.string.backup_export)) { item { SettingsCustomRow { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { exportJson.launch("LocalPlayer-settings-$stamp.json") }, enabled = ui !is BackupUi.Working && ui !is BackupUi.Preview) {
                Text(stringResource(R.string.backup_export_json))
            }
            Text(stringResource(R.string.backup_export_json_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { exportFull.launch("LocalPlayer-$stamp.lpbackup") }, enabled = ui !is BackupUi.Working && ui !is BackupUi.Preview) {
                Text(stringResource(R.string.backup_export_complete))
            }
            Text(stringResource(R.string.backup_export_complete_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } } } }
        SettingsGroup(title = stringResource(R.string.backup_import)) { item { SettingsCustomRow { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { import.launch(arrayOf("*/*")) }, enabled = ui !is BackupUi.Working && ui !is BackupUi.Preview) {
                Text(stringResource(R.string.backup_import_choose))
            }
            Text(stringResource(R.string.backup_import_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } } } }
        if (ui !is BackupUi.Idle) SettingsGroup { item { SettingsCustomRow { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (val s = ui) {
                BackupUi.Idle -> Unit
                BackupUi.Working -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(); Text(stringResource(R.string.backup_working))
                }
                is BackupUi.Done -> {
                    val text = if (s.arg > 0) stringResource(s.messageRes, s.arg.toString()) else stringResource(s.messageRes)
                    Text(text, color = if (s.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                    if (s.message.isNotBlank()) Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    if (s.detail.isNotBlank()) Text(s.detail, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = vm::dismiss) { Text(stringResource(R.string.ok)) }
                }
                is BackupUi.Preview -> {
                    Text(stringResource(R.string.backup_preview), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.backup_pick_parts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val plan = s.plan
                    TickRow(
                        checked = BackupUi.SETTINGS_PART in s.selected,
                        title = stringResource(R.string.backup_part_settings),
                        subtitle = stringResource(R.string.backup_part_settings_count, plan.changes.size),
                        onToggle = { vm.toggleSection(BackupUi.SETTINGS_PART) },
                    )
                    s.prepared.libraryPreview.forEach { part ->
                        TickRow(
                            checked = part.section in s.selected,
                            title = stringResource(partTitle(part.section)),
                            subtitle = stringResource(R.string.backup_part_counts, part.total) +
                                if (part.unmatched > 0) " · " + pluralStringResource(R.plurals.backup_part_unmatched, part.unmatched, part.unmatched) else "",
                            onToggle = { vm.toggleSection(part.section) },
                        )
                    }
                    if (s.prepared.libraryPreview.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().clickable { vm.setReplace(!s.replace) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = s.replace, onCheckedChange = vm::setReplace)
                            Column {
                                Text(stringResource(R.string.backup_replace))
                                Text(
                                    stringResource(if (s.replace) R.string.backup_replace_note_on else R.string.backup_replace_note_off),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else if (s.isArchive) {
                        Text(stringResource(R.string.backup_no_library), style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
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
                        Button(onClick = vm::confirm, enabled = s.selected.isNotEmpty()) { Text(stringResource(R.string.backup_apply)) }
                        OutlinedButton(onClick = vm::cancel) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }

        } } } }

        if (restoredCopy && ui is BackupUi.Idle) SettingsGroup(title = stringResource(R.string.backup_restored_title)) { item { SettingsCustomRow { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.backup_restored_body), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::chooseRestoredCopy) { Text(stringResource(R.string.backup_restored_use)) }
                    OutlinedButton(onClick = vm::dismissRestoredCopy) { Text(stringResource(R.string.backup_restored_dismiss)) }
                }
        } } } }

        SettingsGroup(title = stringResource(R.string.backup_auto_title)) {
            item {
                SettingsSwitchRow(stringResource(R.string.backup_auto_switch), autoOn, vm::setAutoBackupEnabled, note = stringResource(R.string.backup_auto_note))
            }
            if (autoOn) item { SettingsCustomRow { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    autoFolder?.let { Uri.decode(it).substringAfterLast(':').ifBlank { it } } ?: stringResource(R.string.backup_auto_no_folder),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickFolder.launch(null) }) { Text(stringResource(R.string.backup_auto_choose_folder)) }
                    Button(onClick = vm::backupNow, enabled = autoFolder != null && ui !is BackupUi.Working && ui !is BackupUi.Preview) {
                        Text(stringResource(R.string.backup_auto_now))
                    }
                }
                Text(
                    whenText(lastFolder)?.let { stringResource(R.string.backup_auto_last, it) } ?: stringResource(R.string.backup_auto_never),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } } }
        }

        SettingsGroup {
            item {
                SettingsSwitchRow(stringResource(R.string.backup_android_switch), androidCopy, vm::setAndroidCopy, note = stringResource(R.string.backup_android_note))
            }
            if (androidCopy) item {
                SettingsCustomRow {
                    Text(
                        whenText(lastAndroid)?.let { stringResource(R.string.backup_android_last, it) } ?: stringResource(R.string.backup_auto_never),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun partTitle(section: String): Int = when (section) {
    LibrarySection.PLAYLISTS -> R.string.backup_part_playlists
    LibrarySection.FAVORITES -> R.string.backup_part_favorites
    LibrarySection.HISTORY -> R.string.backup_part_history
    else -> R.string.backup_part_rules
}

@Composable
private fun TickRow(checked: Boolean, title: String, subtitle: String, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
