package com.localplayer.music.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.music.R
import com.localplayer.music.data.library.AddedFolder
import com.localplayer.music.data.library.FolderEntry
import com.localplayer.music.data.library.FolderKeys
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.ArtworkImage
import com.localplayer.music.ui.components.ConfirmDialog
import com.localplayer.music.ui.components.DetailTopBar
import com.localplayer.music.ui.components.EmptyState
import com.localplayer.music.ui.components.SectionHeader
import com.localplayer.music.ui.components.artworkRef
import com.localplayer.music.ui.components.barPadding
import com.localplayer.music.ui.nav.NavState

/** "Internal storage" / "SD card 1234-abcd" for a folder key's volume. */
@Composable
fun volumeLabel(key: String): String {
    val volume = FolderKeys.volumeOf(key)
    return if (volume == FolderKeys.PRIMARY_VOLUME) stringResource(R.string.storage_internal)
    else stringResource(R.string.storage_card, volume)
}

/** "Internal storage / Music / Rock" */
@Composable
fun folderDisplay(key: String): String {
    val path = FolderKeys.relativePathOf(key)
    return if (path.isEmpty()) volumeLabel(key) else "${volumeLabel(key)} / ${path.replace("/", " / ")}"
}

/** Songs the user hid. Nothing here deletes a file; "Show again" puts the song back everywhere. */
@Composable
fun HiddenSongsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val hidden by viewModel.hiddenTracks.collectAsStateWithLifecycle()
    val list = hidden
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(
            title = stringResource(R.string.hidden_songs),
            onBack = { nav.pop() },
            actions = {
                if (!list.isNullOrEmpty()) {
                    TextButton(onClick = viewModel::unhideAll) { Text(stringResource(R.string.show_all_again)) }
                }
            },
        )
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(stringResource(R.string.hidden_songs_empty), Modifier.fillMaxSize())
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = barPadding(extra = 8.dp)) {
                item(key = "note") {
                    Text(
                        stringResource(R.string.hidden_songs_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(list, key = { it.id }) { song ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkImage(artwork = song.artworkRef(), size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                song.artist ?: stringResource(R.string.unknown_artist),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { viewModel.unhideTrack(song.uri) }) {
                            Icon(AppIcons.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.show_again))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Library folders: add a folder through the system picker (persisted permission), see which folders are in the
 * library, switch folders off/on, remove the access of an added folder and rescan.
 */
@Composable
fun LibraryFoldersScreen(nav: NavState, viewModel: LibraryViewModel) {
    val state by viewModel.folderState.collectAsStateWithLifecycle()
    val scan by viewModel.scanState.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<AddedFolder?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.addLibraryFolder(uri)
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = stringResource(R.string.library_folders), onBack = { nav.pop() })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = barPadding(extra = 16.dp)) {
            item(key = "intro") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.library_folders_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { picker.launch(null) }, modifier = Modifier.weight(1f)) {
                            Icon(AppIcons.FolderAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.add_folder))
                        }
                        OutlinedButton(onClick = viewModel::rescan, enabled = !scan.isScanning, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.rescan_folders))
                        }
                    }
                    if (scan.isScanning) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                        Text(
                            stringResource(R.string.folders_scanning),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            item(key = "added-header") { SectionHeader(stringResource(R.string.added_folders)) }
            val folders = state
            if (folders == null) return@LazyColumn
            if (folders.added.isEmpty()) {
                item(key = "added-empty") {
                    Text(
                        stringResource(R.string.no_added_folders),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            items(folders.added, key = { "added-" + it.treeUri }) { added ->
                AddedFolderRow(added, onRemove = { removing = added })
            }

            item(key = "all-header") { SectionHeader(stringResource(R.string.folders_in_library)) }
            if (folders.folders.isEmpty()) {
                item(key = "all-empty") {
                    Text(
                        stringResource(R.string.no_folders_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            items(folders.folders, key = { "folder-" + it.key }) { entry ->
                FolderSwitchRow(entry, onChange = { included -> viewModel.setFolderExcluded(entry.key, !included) })
            }
        }
    }

    removing?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.remove_folder_access),
            body = stringResource(R.string.remove_folder_access_body, target.label),
            confirmLabel = stringResource(R.string.remove),
            onConfirm = {
                viewModel.removeLibraryFolder(target.treeUri)
                removing = null
            },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun AddedFolderRow(folder: AddedFolder, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Folder, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    folderDisplay(folder.key),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.track_count, folder.trackCount) + " · " +
                        stringResource(if (folder.accessible) R.string.folder_access_ok else R.string.folder_access_lost),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (folder.accessible) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = onRemove) { Text(stringResource(R.string.remove_folder_access_short)) }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun FolderSwitchRow(entry: FolderEntry, onChange: (Boolean) -> Unit) {
    // A folder under an excluded parent cannot be switched on by itself: the parent decides.
    val lockedByParent = entry.excluded && entry.excludedBy != entry.key
    val included = !entry.excluded
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = included,
                enabled = !lockedByParent,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                folderDisplay(entry.key),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = when {
                lockedByParent -> stringResource(R.string.folder_excluded_by, folderDisplay(entry.excludedBy.orEmpty()))
                entry.excluded -> stringResource(R.string.folder_excluded)
                else -> stringResource(R.string.track_count, entry.trackCount)
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = included, onCheckedChange = null, enabled = !lockedByParent)
    }
}
