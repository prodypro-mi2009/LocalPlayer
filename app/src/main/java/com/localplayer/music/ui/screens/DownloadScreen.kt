package com.localplayer.music.ui.screens

import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.text.font.FontWeight
import com.localplayer.music.ui.components.ChunkyProgress
import com.localplayer.music.data.download.DownloadTags
import com.localplayer.music.ui.components.FilledField
import com.localplayer.music.ui.components.ExistingSongsDialog
import com.localplayer.music.ui.components.SettingsSwitchRow
import com.localplayer.music.ui.components.SettingsCustomRow
import com.localplayer.music.ui.components.SettingsGroup
import com.localplayer.music.ui.components.SettingsNote
import com.localplayer.music.ui.components.SettingsTopBar
import com.localplayer.music.ui.components.topFade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.localplayer.music.AppContainer
import com.localplayer.music.R
import com.localplayer.music.data.download.DownloadItem
import com.localplayer.music.data.download.DownloadPhase
import com.localplayer.music.data.download.ItemStatus
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.barPadding
import com.localplayer.music.ui.nav.NavState

/** Settings > Download from YouTube: paste a link, edit names/artist, choose the folder, download MP3. */
@Composable
fun DownloadScreen(nav: NavState, viewModel: LibraryViewModel, container: AppContainer) {
    val manager = container.downloads
    val state by manager.state.collectAsStateWithLifecycle()
    val folder by container.settings.downloadFolder.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val clipScope = androidx.compose.runtime.rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("") }
    var allArtist by rememberSaveable { mutableStateOf("") }
    var allAlbum by rememberSaveable { mutableStateOf("") }
    var allYear by rememberSaveable { mutableStateOf("") }
    var allGenre by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        manager.loadVersion()
        manager.loadExtras()
    }
    val cookiesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) manager.importCookies(uri)
    }
    val busy = state.phase == DownloadPhase.DOWNLOADING || state.phase == DownloadPhase.CHECKING
    // The songs found already in the library, while the "add to a playlist" question is open.
    var existingUris by remember { mutableStateOf<List<String>?>(null) }
    existingUris?.let { uris ->
        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
        val holdingAll by produceState(emptySet<Long>(), uris, playlists) { value = container.library.playlistsHoldingAll(uris) }
        ExistingSongsDialog(
            count = uris.size,
            playlists = playlists.orEmpty(),
            holdingAll = holdingAll,
            onPick = { playlist ->
                viewModel.addToPlaylist(playlist, uris)
                existingUris = null
            },
            onCreate = { name ->
                viewModel.createPlaylist(name, thenAdd = uris)
                existingUris = null
            },
            onDismiss = { existingUris = null },
        )
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // The library also learns about the folder (and keeps the permission), so the songs show up in it.
            viewModel.addLibraryFolder(uri)
            container.settings.setDownloadFolder(uri.toString())
        }
    }

    Column(Modifier.fillMaxSize()) {
        SettingsTopBar(title = stringResource(R.string.dl_title), onBack = { nav.pop() })
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        // Once the link has been read, glide down so the artist / album / year / genre fields (and Download) are in view.
        var hadItems by remember { mutableStateOf(state.items.isNotEmpty()) }
        LaunchedEffect(state.items.isNotEmpty()) {
            val has = state.items.isNotEmpty()
            if (has && !hadItems) listState.animateScrollToItem(1)
            hadItems = has
        }
        LazyColumn(
            Modifier.fillMaxSize().topFade(listState),
            state = listState,
            contentPadding = barPadding(extra = 24.dp, horizontal = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "link") {
                SettingsGroup {
                    item {
                        SettingsCustomRow {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledField(
                                    value = url,
                                    onValueChange = { url = it },
                                    label = stringResource(R.string.dl_link_hint),
                                    enabled = !busy,
                                    trailing = {
                                        IconButton(
                                            enabled = !busy,
                                            onClick = {
                                                clipScope.launch {
                                                    val pasted = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text
                                                    if (pasted != null) url = pasted.toString().trim()
                                                }
                                            },
                                        ) { Icon(AppIcons.ContentPaste, contentDescription = stringResource(R.string.dl_paste)) }
                                    },
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Button(onClick = { manager.check(url) }, enabled = !busy, modifier = Modifier.weight(1f).height(52.dp)) {
                                        Text(stringResource(R.string.dl_check))
                                    }
                                    if (state.items.isNotEmpty() && !busy) {
                                        FilledTonalButton(
                                            onClick = { manager.reset(); url = "" },
                                            modifier = Modifier.height(52.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                                        ) {
                                            Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.dl_new), maxLines = 1)
                                        }
                                    }
                                }
                                if (state.phase == DownloadPhase.CHECKING) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                        Text(stringResource(R.string.dl_checking), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                // The yt-dlp update result is shown next to the update button, not here.
                                state.messageRes?.takeIf { !isEngineMessage(it) }?.let {
                                    Text(
                                        stringResource(it),
                                        color = MaterialTheme.colorScheme.error,
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
                    }
                }
            }

            if (state.items.isNotEmpty()) {
                item(key = "all") {
                    val ticked = state.items.count { it.selected && it.status != ItemStatus.DONE }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            pluralStringResource(R.plurals.dl_found, state.items.size, state.items.size),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                        Text(
                            stringResource(R.string.dl_options_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                        SettingsGroup(title = stringResource(R.string.dl_bulk_title), note = stringResource(R.string.dl_bulk_note)) {
                            item { SettingsCustomRow { FilledField(allArtist, { allArtist = it }, stringResource(R.string.dl_song_artist), enabled = !busy) } }
                            item { SettingsCustomRow { FilledField(allAlbum, { allAlbum = it }, stringResource(R.string.dl_album), enabled = !busy) } }
                            item {
                                SettingsCustomRow {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        FilledField(
                                            allYear, { allYear = it.filter(Char::isDigit).take(4) }, stringResource(R.string.dl_year),
                                            modifier = Modifier.weight(1f), enabled = !busy,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        )
                                        FilledField(allGenre, { allGenre = it }, stringResource(R.string.dl_genre), modifier = Modifier.weight(1f), enabled = !busy)
                                    }
                                }
                            }
                            item {
                                SettingsCustomRow {
                                    // Apply on its own line; the two tick buttons share the line below at equal width,
                                    // so a longer "Apply to N ticked" label can never squeeze them out of shape.
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                manager.fillIn(allArtist, allAlbum, allYear, allGenre)
                                                // only what was typed is applied; start the next round with empty fields
                                                allArtist = ""; allAlbum = ""; allYear = ""; allGenre = ""
                                            },
                                            enabled = !busy,
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                        ) {
                                            Text(if (ticked > 0) stringResource(R.string.dl_apply_selected, ticked) else stringResource(R.string.dl_apply), maxLines = 1)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilledTonalButton(onClick = { manager.selectAll(true) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                                                Text(stringResource(R.string.dl_select_all), maxLines = 1)
                                            }
                                            FilledTonalButton(onClick = { manager.selectAll(false) }, enabled = !busy && ticked > 0, modifier = Modifier.weight(1f)) {
                                                Text(stringResource(R.string.dl_select_none), maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "go") {
                    val waiting = state.items.count { it.status != ItemStatus.DONE && !it.skipped }
                    val failed = state.items.count { it.status == ItemStatus.FAILED }
                    val done = state.items.count { it.status == ItemStatus.DONE }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.phase == DownloadPhase.DOWNLOADING) {
                            Button(onClick = manager::stop, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(stringResource(R.string.dl_stop)) }
                        } else if (waiting > 0) {
                            Button(onClick = manager::start, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                                Text(if (failed > 0 && done > 0) stringResource(R.string.dl_retry) else stringResource(R.string.dl_start, waiting))
                            }
                        }
                        val inFolder = state.items.count { it.skipped }
                        if (state.updatePlaylist && state.items.isNotEmpty()) {
                            Text(
                                stringResource(R.string.dl_in_folder_count, inFolder, waiting),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val existing = state.items.filter { it.skipped && it.libraryUri != null }.mapNotNull { it.libraryUri }.distinct()
                        if (existing.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = { existingUris = existing },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                            ) { Text(pluralStringResource(R.plurals.dl_add_existing, existing.size, existing.size), maxLines = 1) }
                        }
                        if (done > 0 || failed > 0) {
                            Text(stringResource(R.string.dl_summary, done, failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                items(state.items, key = { it.id }) { item ->
                    if (item.inFolder) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            Text(
                                stringResource(if (item.downloadAnyway) R.string.dl_will_download_again else R.string.dl_in_folder_badge),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { manager.setDownloadAnyway(item.id, !item.downloadAnyway) },
                                enabled = state.phase == DownloadPhase.READY && item.status != ItemStatus.DONE,
                            ) { Text(stringResource(if (item.downloadAnyway) R.string.dl_skip_again else R.string.dl_download_anyway)) }
                        }
                    }
                    SongCard(
                        song = item,
                        albumArtistSame = state.albumArtistSame,
                        editable = state.phase == DownloadPhase.READY && item.status != ItemStatus.DONE,
                        onTitle = { manager.setTitle(item.id, it) },
                        onArtist = { manager.setArtist(item.id, it) },
                        onAlbumArtist = { manager.setAlbumArtist(item.id, it) },
                        onTrack = { manager.setTrackNumber(item.id, it) },
                        onSelect = { manager.setSelected(item.id, it) },
                        onRemove = { manager.remove(item.id) },
                    )
                }
            }

            // Save location and the download engine live together, at the end of the page.
            item(key = "settings") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsGroup {
                        item {
                            SettingsCustomRow {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    FilledTonalButton(onClick = { picker.launch(null) }, enabled = !busy) { Text(stringResource(R.string.dl_location_change)) }
                                }
                            }
                        }
                    }
                    SettingsGroup(title = stringResource(R.string.dl_options_title), note = stringResource(R.string.dl_options_note)) {
                            item {
                                SettingsSwitchRow(
                                    title = stringResource(R.string.dl_update_playlist),
                                    checked = state.updatePlaylist,
                                    onChange = manager::setUpdatePlaylist,
                                    note = stringResource(R.string.dl_update_playlist_note),
                                    enabled = !busy,
                                )
                            }
                            if (state.updatePlaylist) {
                                item {
                                    SettingsSwitchRow(
                                        title = stringResource(R.string.dl_check_library),
                                        checked = state.checkLibrary,
                                        onChange = manager::setCheckLibrary,
                                        note = stringResource(R.string.dl_check_library_note),
                                        enabled = !busy,
                                    )
                                }
                            }
                            item {
                                SettingsSwitchRow(
                                    title = stringResource(R.string.dl_album_artist_same),
                                    checked = state.albumArtistSame,
                                    onChange = manager::setAlbumArtistSame,
                                    note = stringResource(R.string.dl_album_artist_same_note),
                                    enabled = !busy,
                                )
                            }
                            item {
                                SettingsSwitchRow(
                                    title = stringResource(R.string.dl_replaygain),
                                    checked = state.findReplayGain,
                                    onChange = manager::setFindReplayGain,
                                    note = stringResource(R.string.dl_replaygain_note),
                                    enabled = !busy,
                                )
                            }
                            if (state.findReplayGain) {
                                item {
                                    SettingsSwitchRow(
                                        title = stringResource(R.string.dl_replaygain_album),
                                        checked = state.findAlbumGain,
                                        onChange = manager::setFindAlbumGain,
                                        note = stringResource(R.string.dl_replaygain_album_note),
                                        enabled = !busy,
                                    )
                                }
                            }
                    }
                    SettingsGroup(title = stringResource(R.string.dl_engine_title)) {
                        item {
                            SettingsCustomRow {
                                Text(
                                    if (state.engineChannel == null) {
                                        stringResource(R.string.dl_engine_version, state.engineVersion ?: stringResource(R.string.dl_engine_builtin))
                                    } else {
                                        stringResource(
                                            R.string.dl_engine_version_channel,
                                            state.engineVersion.orEmpty(),
                                            stringResource(if (state.engineChannel == "nightly") R.string.dl_channel_nightly else R.string.dl_channel_stable),
                                        )
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    stringResource(R.string.dl_update_engine_note),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        item {
                            SettingsCustomRow {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalButton(onClick = manager::updateEngine, enabled = !busy && !state.updatingEngine) {
                                        Text(stringResource(R.string.dl_update_engine))
                                    }
                                    if (state.updatingEngine) {
                                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                        Text(stringResource(R.string.dl_updating), style = MaterialTheme.typography.bodyMedium)
                                    } else {
                                        state.messageRes?.takeIf { isEngineMessage(it) }?.let {
                                            val ok = it != R.string.dl_update_failed
                                            Text(
                                                stringResource(it),
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SettingsGroup(title = stringResource(R.string.dl_signin_title), note = stringResource(R.string.dl_signin_note)) {
                        item {
                            SettingsSwitchRow(
                                title = stringResource(R.string.dl_signin_nightly),
                                checked = state.ytNightly,
                                onChange = manager::setYtNightly,
                                note = stringResource(R.string.dl_signin_nightly_note),
                                enabled = !busy,
                            )
                        }
                        val wantsNightly = state.ytNightly
                        val isNightly = state.engineChannel == "nightly"
                        if (wantsNightly != isNightly) {
                            item {
                                SettingsCustomRow {
                                    Text(
                                        stringResource(if (wantsNightly) R.string.dl_signin_apply_nightly else R.string.dl_signin_apply_stable),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        if (isNightly) {
                            item {
                                SettingsCustomRow {
                                    FilledTonalButton(onClick = manager::backToStable, enabled = !busy && !state.updatingEngine) {
                                        Text(stringResource(R.string.dl_back_to_stable))
                                    }
                                }
                            }
                        }
                        item {
                            SettingsSwitchRow(
                                title = stringResource(R.string.dl_signin_clients),
                                checked = state.ytFallback,
                                onChange = manager::setYtFallback,
                                note = stringResource(R.string.dl_signin_clients_note),
                                enabled = !busy,
                            )
                        }
                        item {
                            SettingsSwitchRow(
                                title = stringResource(R.string.dl_signin_cookies),
                                checked = state.ytCookies,
                                onChange = manager::setYtCookies,
                                note = stringResource(R.string.dl_signin_cookies_note),
                                enabled = !busy,
                            )
                        }
                        if (state.ytCookies) {
                            item {
                                SettingsCustomRow {
                                    Text(
                                        stringResource(if (state.ytHasCookies) R.string.dl_cookies_have else R.string.dl_cookies_none),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        FilledTonalButton(onClick = { cookiesPicker.launch(arrayOf("*/*")) }, enabled = !busy) {
                                            Text(stringResource(R.string.dl_cookies_choose))
                                        }
                                        if (state.ytHasCookies) {
                                            TextButton(onClick = manager::clearCookies, enabled = !busy) {
                                                Text(stringResource(R.string.dl_cookies_remove))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SettingsNote(stringResource(R.string.dl_legal_note))
                }
            }
        }
    }
}

/** The messages that belong to the yt-dlp update (shown beside its button). */
private fun isEngineMessage(res: Int): Boolean =
    res == R.string.dl_updated || res == R.string.dl_updated_new || res == R.string.dl_update_failed

@Composable
private fun SongCard(
    song: DownloadItem,
    albumArtistSame: Boolean,
    editable: Boolean,
    onTitle: (String) -> Unit,
    onArtist: (String) -> Unit,
    onAlbumArtist: (String) -> Unit,
    onTrack: (String) -> Unit,
    onSelect: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    // Local text is the source of truth while typing; it is re-synced when "fill in" changes the stored value.
    var title by remember(song.id) { mutableStateOf(song.title) }
    var artist by remember(song.id) { mutableStateOf(song.artist) }
    var track by remember(song.id) { mutableStateOf(song.trackNumber) }
    var albumArtist by remember(song.id) { mutableStateOf(song.albumArtist) }
    LaunchedEffect(song.title) { if (title != song.title) title = song.title }
    LaunchedEffect(song.artist) { if (artist != song.artist) artist = song.artist }
    LaunchedEffect(song.albumArtist) { if (albumArtist != song.albumArtist) albumArtist = song.albumArtist }
    LaunchedEffect(song.trackNumber) { if (track != song.trackNumber) track = song.trackNumber }

    SettingsGroup {
        item {
            SettingsCustomRow {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (editable) {
                        Checkbox(checked = song.selected, onCheckedChange = onSelect, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    val progressText = when {
                        song.progress < 0.01f -> stringResource(R.string.dl_status_preparing)
                        song.progress >= 0.99f -> stringResource(R.string.dl_status_finishing)
                        else -> "${(song.progress * 100).toInt()}%"
                    }
                    val status = when (song.status) {
                        ItemStatus.WAITING -> stringResource(R.string.dl_status_waiting)
                        ItemStatus.RUNNING -> progressText
                        ItemStatus.DONE -> stringResource(R.string.dl_status_done)
                        ItemStatus.FAILED -> stringResource(R.string.dl_status_failed)
                    }
                    val (badge, onBadge) = when (song.status) {
                        ItemStatus.DONE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                        ItemStatus.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(shape = RoundedCornerShape(10.dp), color = badge) {
                        Text(status, style = MaterialTheme.typography.labelLarge, color = onBadge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    if (editable) {
                        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                            Icon(AppIcons.Close, contentDescription = stringResource(R.string.dl_remove), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        if (song.status == ItemStatus.RUNNING) {
            item { SettingsCustomRow { ChunkyProgress(if (song.progress < 0.01f) null else song.progress) } }
        }
        if (song.error != null || song.note != null) {
            item {
                SettingsCustomRow {
                    song.error?.let {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    song.note?.let {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(stringResource(R.string.dl_tags_warning, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        item { SettingsCustomRow { FilledField(title, { title = it; onTitle(it) }, stringResource(R.string.dl_song_title), enabled = editable) } }
        item { SettingsCustomRow { FilledField(artist, { artist = it; onArtist(it) }, stringResource(R.string.dl_song_artist), enabled = editable) } }
        item {
            SettingsCustomRow {
                val auto = DownloadTags.albumArtistFor(artist, "", albumArtistSame)
                FilledField(
                    albumArtist,
                    { albumArtist = it; onAlbumArtist(it) },
                    stringResource(R.string.dl_album_artist),
                    enabled = editable,
                    supportingText = when {
                        albumArtist.isNotBlank() -> null
                        auto.isNotEmpty() -> stringResource(R.string.dl_album_artist_auto, auto)
                        else -> stringResource(R.string.dl_album_artist_none)
                    },
                )
            }
        }
        item {
            SettingsCustomRow {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledField(
                        track,
                        { v -> val digits = v.filter(Char::isDigit).take(4); track = digits; onTrack(digits) },
                        stringResource(R.string.dl_track),
                        modifier = Modifier.width(140.dp),
                        enabled = editable,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    val details = listOf(song.album, song.year, song.genre).filter { it.isNotBlank() }.joinToString(" · ")
                    if (details.isNotEmpty()) {
                        Text(
                            details,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** "primary:Music/Songs" (the last part of a tree URI) shown as "Music/Songs". */
private fun folderLabel(treeUri: String): String {
    val id = treeUri.toUri().lastPathSegment ?: return treeUri
    return id.substringAfter(':', id).ifBlank { id }
}
