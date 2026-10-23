package com.localplayer.music.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.music.AppContainer
import com.localplayer.music.R
import com.localplayer.music.data.download.DownloadItem
import com.localplayer.music.data.download.DownloadPhase
import com.localplayer.music.data.download.ItemStatus
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.DetailTopBar
import com.localplayer.music.ui.components.barPadding
import com.localplayer.music.ui.nav.NavState

/** Settings > Download from YouTube: paste a link, edit names/artist, choose the folder, download MP3. */
@Composable
fun DownloadScreen(nav: NavState, viewModel: LibraryViewModel, container: AppContainer) {
    val manager = container.downloads
    val state by manager.state.collectAsStateWithLifecycle()
    val folder by container.settings.downloadFolder.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var url by rememberSaveable { mutableStateOf("") }
    var allArtist by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { manager.loadVersion() }
    val busy = state.phase == DownloadPhase.DOWNLOADING || state.phase == DownloadPhase.CHECKING
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // The library also learns about the folder (and keeps the permission), so the songs show up in it.
            viewModel.addLibraryFolder(uri)
            container.settings.setDownloadFolder(uri.toString())
        }
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = stringResource(R.string.dl_title), onBack = { nav.pop() })
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = barPadding(extra = 24.dp, horizontal = 16.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "link") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !busy,
                        label = { Text(stringResource(R.string.dl_link_hint)) },
                        trailingIcon = {
                            IconButton(
                                enabled = !busy,
                                onClick = { clipboard.getText()?.text?.let { url = it.trim() } },
                            ) { Icon(AppIcons.ContentPaste, contentDescription = stringResource(R.string.dl_paste)) }
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { manager.check(url) }, enabled = !busy) { Text(stringResource(R.string.dl_check)) }
                        if (state.phase == DownloadPhase.CHECKING) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.dl_checking), style = MaterialTheme.typography.bodyMedium)
                        }
                        if (state.items.isNotEmpty() && !busy) {
                            TextButton(onClick = { manager.reset(); url = "" }) { Text(stringResource(R.string.dl_new)) }
                        }
                    }
                    state.messageRes?.let {
                        val ok = it == R.string.dl_updated || it == R.string.dl_updated_new
                        Text(
                            stringResource(it),
                            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    state.detail?.let {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        stringResource(R.string.dl_format_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "location") {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.dl_save_location), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                folder?.let { folderLabel(it) } ?: stringResource(R.string.dl_location_default),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (folder != null) {
                            TextButton(onClick = { container.settings.setDownloadFolder(null) }, enabled = !busy) {
                                Text(stringResource(R.string.dl_location_reset))
                            }
                        }
                        OutlinedButton(onClick = { picker.launch(null) }, enabled = !busy) { Text(stringResource(R.string.dl_location_change)) }
                    }
                }
            }

            if (state.items.isNotEmpty()) {
                item(key = "all") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.dl_found, state.items.size), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = allArtist,
                                onValueChange = { allArtist = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                enabled = !busy,
                                label = { Text(stringResource(R.string.dl_artist_all)) },
                            )
                            Button(onClick = { manager.setArtistForAll(allArtist.trim()) }, enabled = !busy) {
                                Text(stringResource(R.string.dl_apply))
                            }
                        }
                        Text(
                            stringResource(R.string.dl_artist_all_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item(key = "go") {
                    val waiting = state.items.count { it.status != ItemStatus.DONE }
                    val failed = state.items.count { it.status == ItemStatus.FAILED }
                    val done = state.items.count { it.status == ItemStatus.DONE }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (state.phase == DownloadPhase.DOWNLOADING) {
                            Button(onClick = manager::stop) { Text(stringResource(R.string.dl_stop)) }
                        } else if (waiting > 0) {
                            Button(onClick = manager::start, enabled = !busy) {
                                Text(if (failed > 0 && done > 0) stringResource(R.string.dl_retry) else stringResource(R.string.dl_start, waiting))
                            }
                        }
                        if (done > 0 || failed > 0) {
                            Text(stringResource(R.string.dl_summary, done, failed), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                items(state.items, key = { it.id }) { item ->
                    SongCard(
                        item = item,
                        editable = state.phase == DownloadPhase.READY && item.status != ItemStatus.DONE,
                        onTitle = { manager.setTitle(item.id, it) },
                        onArtist = { manager.setArtist(item.id, it) },
                        onRemove = { manager.remove(item.id) },
                    )
                }
            }

            item(key = "engine") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        stringResource(R.string.dl_engine_version, state.engineVersion ?: stringResource(R.string.dl_engine_builtin)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(stringResource(R.string.dl_update_engine_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = manager::updateEngine, enabled = !busy && !state.updatingEngine) {
                            Text(stringResource(R.string.dl_update_engine))
                        }
                        if (state.updatingEngine) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.dl_updating), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text(stringResource(R.string.dl_legal_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SongCard(
    item: DownloadItem,
    editable: Boolean,
    onTitle: (String) -> Unit,
    onArtist: (String) -> Unit,
    onRemove: () -> Unit,
) {
    // Local text is the source of truth while typing; it is re-synced when "Apply to all" changes the stored value.
    var title by remember(item.id) { mutableStateOf(item.title) }
    var artist by remember(item.id) { mutableStateOf(item.artist) }
    LaunchedEffect(item.title) { if (title != item.title) title = item.title }
    LaunchedEffect(item.artist) { if (artist != item.artist) artist = item.artist }

    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val status = when (item.status) {
                    ItemStatus.WAITING -> stringResource(R.string.dl_status_waiting)
                    ItemStatus.RUNNING -> "${(item.progress * 100).toInt()}%"
                    ItemStatus.DONE -> stringResource(R.string.dl_status_done)
                    ItemStatus.FAILED -> stringResource(R.string.dl_status_failed)
                }
                Text(
                    status,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.status == ItemStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (editable) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(AppIcons.Close, contentDescription = stringResource(R.string.dl_remove), modifier = Modifier.size(18.dp))
                    }
                }
            }
            item.error?.let {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (item.status == ItemStatus.RUNNING) {
                LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; onTitle(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = editable,
                label = { Text(stringResource(R.string.dl_song_title)) },
            )
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it; onArtist(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = editable,
                label = { Text(stringResource(R.string.dl_song_artist)) },
            )
        }
    }
}

/** "primary:Music/Songs" (the last part of a tree URI) shown as "Music/Songs". */
private fun folderLabel(treeUri: String): String {
    val id = Uri.parse(treeUri).lastPathSegment ?: return treeUri
    return id.substringAfter(':', id).ifBlank { id }
}
