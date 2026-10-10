package com.localplayer.music.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.localplayer.music.R
import com.localplayer.music.data.library.FolderAnalysis
import com.localplayer.music.ui.LibraryViewModel
import kotlinx.coroutines.launch

private enum class NewPlaylistStep { Choose, Name, ReadingFolder, FolderName, FolderError }

/**
 * "New playlist": a normal playlist, or a playlist built from one folder of the device. The folder is chosen
 * with the system folder picker; its supported music files are only REFERENCED (nothing is copied or moved) and
 * the playlist follows the folder after every library scan.
 */
@Composable
fun NewPlaylistFlow(viewModel: LibraryViewModel, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(NewPlaylistStep.Choose) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var analysis by remember { mutableStateOf<FolderAnalysis?>(null) }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            step = NewPlaylistStep.Choose
        } else {
            step = NewPlaylistStep.ReadingFolder
            scope.launch {
                val result = viewModel.analyzeFolder(uri)
                if (result == null) {
                    step = NewPlaylistStep.FolderError
                } else {
                    pickedUri = uri
                    analysis = result
                    step = NewPlaylistStep.FolderName
                }
            }
        }
    }

    when (step) {
        NewPlaylistStep.Choose -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.new_playlist)) },
            text = {
                Column {
                    TextButton(onClick = { step = NewPlaylistStep.Name }, modifier = Modifier.fillMaxWidth()) {
                        Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.new_playlist_regular), modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = { picker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(AppIcons.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.new_playlist_from_folder), modifier = Modifier.weight(1f))
                    }
                    Text(
                        stringResource(R.string.new_playlist_from_folder_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )

        NewPlaylistStep.Name -> NameDialog(
            title = stringResource(R.string.new_playlist),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onConfirm = {
                viewModel.createPlaylist(it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )

        NewPlaylistStep.ReadingFolder -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.new_playlist_from_folder)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.reading_folder))
                }
            },
            confirmButton = {},
        )

        NewPlaylistStep.FolderError -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.new_playlist_from_folder)) },
            text = { Text(stringResource(R.string.folder_unsupported)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        )

        NewPlaylistStep.FolderName -> {
            val found = analysis
            val uri = pickedUri
            if (found == null || uri == null) {
                onDismiss()
                return
            }
            var name by remember(found.folderKey) { mutableStateOf(found.label) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.new_playlist_from_folder)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.playlist_name)) },
                        )
                        Text(
                            stringResource(R.string.folder_playlist_summary, found.filesOnDisk, found.label),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        if (found.filesOnDisk > found.inLibrary) {
                            Text(
                                stringResource(R.string.folder_playlist_needs_access, found.filesOnDisk - found.inLibrary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Text(
                            stringResource(R.string.folder_playlist_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            viewModel.createFolderPlaylist(uri, name, found)
                            onDismiss()
                        },
                    ) { Text(stringResource(R.string.create)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}
