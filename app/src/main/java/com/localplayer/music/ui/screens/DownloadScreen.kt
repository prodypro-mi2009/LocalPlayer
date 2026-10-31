package com.localplayer.music.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.localplayer.music.ui.components.SettingsCustomRow
import com.localplayer.music.ui.components.SettingsGroup
import com.localplayer.music.ui.components.SettingsNote
import com.localplayer.music.ui.components.SettingsTopBar
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.KeyboardType
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
    var allAlbum by rememberSaveable { mutableStateOf("") }
    var allYear by rememberSaveable { mutableStateOf("") }
    var allGenre by rememberSaveable { mutableStateOf("") }
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
        SettingsTopBar(title = stringResource(R.string.dl_title), onBack = { nav.pop() })
        LazyColumn(
            Modifier.fillMaxSize(),
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
                                            onClick = { clipboard.getText()?.text?.let { url = it.trim() } },
                                        ) { Icon(AppIcons.ContentPaste, contentDescription = stringResource(R.string.dl_paste)) }
                                    },
                                )
                                Button(onClick = { manager.check(url) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                    Text(stringResource(R.string.dl_check))
                                }
                                if (state.phase == DownloadPhase.CHECKING) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                        Text(stringResource(R.string.dl_checking), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                if (state.items.isNotEmpty() && !busy) {
                                    TextButton(onClick = { manager.reset(); url = "" }) { Text(stringResource(R.string.dl_new)) }
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
                    }
                }
            }

            if (state.items.isNotEmpty()) {
                item(key = "all") {
                    val ticked = state.items.count { it.selected && it.status != ItemStatus.DONE }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.dl_found, state.items.size),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
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
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Button(
                                            onClick = {
                                                manager.fillIn(allArtist, allAlbum, allYear, allGenre)
                                                // only what was typed is applied; start the next round with empty fields
                                                allArtist = ""; allAlbum = ""; allYear = ""; allGenre = ""
                                            },
                                            enabled = !busy,
                                        ) {
                                            Text(if (ticked > 0) stringResource(R.string.dl_apply_selected, ticked) else stringResource(R.string.dl_apply))
                                        }
                                        FilledTonalButton(onClick = { manager.selectAll(true) }, enabled = !busy) { Text(stringResource(R.string.dl_select_all)) }
                                        FilledTonalButton(onClick = { manager.selectAll(false) }, enabled = !busy && ticked > 0) { Text(stringResource(R.string.dl_select_none)) }
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "go") {
                    val waiting = state.items.count { it.status != ItemStatus.DONE }
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
                        if (done > 0 || failed > 0) {
                            Text(stringResource(R.string.dl_summary, done, failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                items(state.items, key = { it.id }) { item ->
                    SongCard(
                        song = item,
                        editable = state.phase == DownloadPhase.READY && item.status != ItemStatus.DONE,
                        onTitle = { manager.setTitle(item.id, it) },
                        onArtist = { manager.setArtist(item.id, it) },
                        onTrack = { manager.setTrackNumber(item.id, it) },
                        onSelect = { manager.setSelected(item.id, it) },
                        onRemove = { manager.remove(item.id) },
                    )
                }
            }

            // Save location and the download engine live together, at the end of the page.
            item(key = "settings") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsGroup(title = stringResource(R.string.dl_engine_title)) {
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
                        item {
                            SettingsCustomRow {
                                Text(
                                    stringResource(R.string.dl_engine_version, state.engineVersion ?: stringResource(R.string.dl_engine_builtin)),
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

/** A filled text field with a soft background and no underline, matching the settings cards. */
@Composable
private fun FilledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        label = { Text(label) },
        trailingIcon = trailing,
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

/**
 * A thick, rounded progress bar with a little volume (a soft highlight on the upper half). The fill moves smoothly towards
 * each new value; with [progress] null (nothing reported yet) a segment sweeps along instead, so it never looks stuck at 0.
 */
@Composable
private fun ChunkyProgress(progress: Float?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val start = MaterialTheme.colorScheme.primary
    val end = MaterialTheme.colorScheme.tertiary
    Box(modifier.fillMaxWidth().height(14.dp).clip(shape).background(track)) {
        if (progress == null) {
            val sweep by rememberInfiniteTransition(label = "dlSweep").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
                label = "dlSweepX",
            )
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val w = size.width * 0.3f
                    val x = (size.width + w) * sweep - w
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(start, end), startX = x, endX = x + w),
                        topLeft = Offset(x, 0f),
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                },
            )
        } else {
            val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700, easing = LinearOutSlowInEasing), label = "dlProgress")
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(shown.coerceAtLeast(0.04f)).clip(shape)
                    .background(Brush.horizontalGradient(listOf(start, end))),
            ) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent))))
            }
        }
    }
}

@Composable
private fun SongCard(
    song: DownloadItem,
    editable: Boolean,
    onTitle: (String) -> Unit,
    onArtist: (String) -> Unit,
    onTrack: (String) -> Unit,
    onSelect: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    // Local text is the source of truth while typing; it is re-synced when "fill in" changes the stored value.
    var title by remember(song.id) { mutableStateOf(song.title) }
    var artist by remember(song.id) { mutableStateOf(song.artist) }
    var track by remember(song.id) { mutableStateOf(song.trackNumber) }
    LaunchedEffect(song.title) { if (title != song.title) title = song.title }
    LaunchedEffect(song.artist) { if (artist != song.artist) artist = song.artist }
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
    val id = Uri.parse(treeUri).lastPathSegment ?: return treeUri
    return id.substringAfter(':', id).ifBlank { id }
}
