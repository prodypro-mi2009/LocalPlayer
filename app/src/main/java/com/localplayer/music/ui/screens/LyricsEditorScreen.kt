package com.localplayer.music.ui.screens

import com.localplayer.music.ui.components.SaveCancelPill
import com.localplayer.music.ui.components.TopCurvedPanel
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import com.localplayer.music.ui.components.ChunkyProgress
import com.localplayer.music.ui.components.FilledField
import com.localplayer.music.ui.components.SettingsTopBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.localplayer.music.data.lyrics.EditorDocument
import com.localplayer.music.data.lyrics.EditorLine
import com.localplayer.music.data.lyrics.LrcParser
import com.localplayer.music.data.lyrics.LyricsEditorModel
import com.localplayer.music.data.metadata.MetadataField
import com.localplayer.music.ui.EditMetadataViewModel
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.ui.nav.Screen
import com.localplayer.music.ui.player.rememberPlaybackPosition

/**
 * Lyrics editor for plain text AND synchronized (LRC) lyrics. Each row has an optional time and a text.
 * A time can be typed, or taken from the real playback position (Media3) with one tap. Save writes the
 * lyrics into the audio file, verifies by re-reading the file, and only then reports success.
 * Lines you do not touch are written back exactly as they were.
 */
@Composable
fun LyricsEditorScreen(
    trackId: Long,
    nav: NavState,
    library: LibraryViewModel,
    container: AppContainer,
) {
    val editor: EditMetadataViewModel = viewModel(
        key = "edit-lyrics-$trackId",
        factory = viewModelFactory { initializer { EditMetadataViewModel(container, trackId) } },
    )
    val state by editor.state.collectAsStateWithLifecycle()
    val playerState by library.playerState.collectAsStateWithLifecycle()
    // This screen stays alive under the search screen, so it must re-read the file when lyrics are saved elsewhere.
    val revision by library.lyricsRevision.collectAsStateWithLifecycle()
    var seenRevision by remember { mutableIntStateOf(revision) }
    LaunchedEffect(revision) {
        if (revision != seenRevision) {
            seenRevision = revision
            editor.reload()
        }
    }
    EditEvents(viewModel = editor, onSaved = {
        library.notifyLyricsChanged()
        nav.pop()
    })

    Column(Modifier.fillMaxSize().imePadding()) {
        SettingsTopBar(stringResource(R.string.edit_lyrics)) { if (!state.saving) nav.pop() }
        if (state.loading) {
            Column(Modifier.fillMaxSize().padding(24.dp)) { CircularProgressIndicator() }
            return@Column
        }
        val original = state.original
        if (original == null) {
            state.issue?.let { IssueText(it) }
            return@Column
        }

        val document = remember(original) { LyricsEditorModel.fromRaw(original.lyrics) }
        val rows = remember(original) { mutableStateListOf<EditorLine>().apply { addAll(document.lines) } }
        var nextId by remember(original) { mutableLongStateOf((document.lines.maxOfOrNull { it.id } ?: 0L) + 1) }
        var confirmSearch by remember { mutableStateOf(false) }
        var showShift by remember { mutableStateOf(false) }
        var shiftText by remember { mutableStateOf("0.5") }
        // Text mode: the whole lyrics as one multiline field (paste everything at once). Switching back parses it into rows.
        var textMode by remember { mutableStateOf(false) }
        var bulkText by remember { mutableStateOf("") }
        var header by remember(original) { mutableStateOf(document.header) }
        var timeError by remember { mutableStateOf<String?>(null) }
        val isCurrentSong = playerState.nowPlaying?.mediaId == trackId.toString()
        val position = rememberPlaybackPosition(playerState.isPlaying, playerState.nowPlaying?.mediaId, library::currentPositionMs)
        val editable = state.canEdit && !state.saving

        // Edits not saved yet (the search screen replaces this editor's content). Also decides if Cancel / Save show.
        val dirty = if (textMode) {
            bulkText != LyricsEditorModel.toText(document)
        } else {
            header != document.header || rows.toList() != document.lines
        }

        TopCurvedPanel(Modifier.weight(1f)) {
        Column(Modifier.fillMaxSize()) {
        // Only the problem is shown; the "tags are kept" note is gone.
        if (!state.canEdit) {
            Text(
                stringResource(R.string.edit_unsupported),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        val openSearch = { nav.push(Screen.LyricsSearch(trackId)) }
        val toggleText = {
            if (!textMode) {
                bulkText = LyricsEditorModel.toText(EditorDocument(header, rows.toList()))
            } else {
                val parsed = LyricsEditorModel.fromRaw(bulkText, nextId)
                header = parsed.header
                rows.clear(); rows.addAll(parsed.lines)
                nextId = (parsed.lines.maxOfOrNull { it.id } ?: nextId) + 1
            }
            textMode = !textMode
        }
        val addLine = { rows.add(EditorLine(nextId++, "", "")); Unit }
        val shiftAll = { deltaMs: Long ->
            rows.indices.forEach { i ->
                val t = LrcParser.parseTimestamp(rows[i].timeText.trim())
                if (t != null) rows[i] = rows[i].copy(timeText = LrcParser.formatTimestamp(maxOf(0L, t + deltaMs)))
            }
        }

        // With no lines yet the three actions are shown as big buttons in the middle instead.
        if (textMode || rows.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Works for any song, also when it is not playing.
            AssistChip(
                onClick = { if (dirty) confirmSearch = true else openSearch() },
                enabled = !state.saving,
                shape = RoundedCornerShape(50),
                border = null,
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.height(40.dp),
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(stringResource(R.string.lyrics_search_internet), maxLines = 1) },
            )
            FilterChip(
                selected = textMode,
                enabled = editable,
                shape = RoundedCornerShape(50),
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.height(40.dp),
                onClick = toggleText,
                label = { Text(stringResource(R.string.lyrics_text_mode), maxLines = 1) },
            )
            if (!textMode && rows.any { it.timeText.isNotBlank() }) {
                AssistChip(
                    onClick = { showShift = true },
                    enabled = editable,
                    shape = RoundedCornerShape(50),
                    border = null,
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.height(40.dp),
                    label = { Text(stringResource(R.string.lyrics_shift_all), maxLines = 1) },
                )
            }
        }
        }

        if (showShift) {
            // Seconds with decimals (0.35 = 350 ms); a comma is accepted too.
            val amountMs = shiftText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 }?.let { Math.round(it * 1000) }
            AlertDialog(
                onDismissRequest = { showShift = false },
                title = { Text(stringResource(R.string.lyrics_shift_all)) },
                text = {
                    FilledField(
                        value = shiftText,
                        onValueChange = { shiftText = it },
                        label = stringResource(R.string.lyrics_shift_amount),
                        enabled = true,
                    )
                },
                confirmButton = {
                    TextButton(enabled = amountMs != null, onClick = { shiftAll(amountMs ?: 0L); showShift = false }) {
                        Text(stringResource(R.string.lyrics_shift_later))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { showShift = false }) { Text(stringResource(R.string.cancel)) }
                        TextButton(enabled = amountMs != null, onClick = { shiftAll(-(amountMs ?: 0L)); showShift = false }) {
                            Text(stringResource(R.string.lyrics_shift_earlier))
                        }
                    }
                },
            )
        }

        if (confirmSearch) {
            AlertDialog(
                onDismissRequest = { confirmSearch = false },
                title = { Text(stringResource(R.string.lyrics_search_unsaved_title)) },
                text = { Text(stringResource(R.string.lyrics_search_unsaved_message)) },
                confirmButton = {
                    TextButton(onClick = { confirmSearch = false; openSearch() }) {
                        Text(stringResource(R.string.lyrics_search_unsaved_continue))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmSearch = false }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }

        if (textMode) {
            FilledField(
                value = bulkText,
                onValueChange = { bulkText = it },
                label = stringResource(R.string.lyrics_text_mode),
                enabled = editable,
                singleLine = false,
                modifier = Modifier.weight(1f).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
            )
        } else if (rows.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.lyrics_empty_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = { if (dirty) confirmSearch = true else openSearch() },
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.lyrics_search_internet), modifier = Modifier.padding(start = 8.dp))
                    }
                    FilledTonalButton(onClick = toggleText, enabled = editable, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(stringResource(R.string.lyrics_paste_text))
                    }
                    FilledTonalButton(onClick = addLine, enabled = editable, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.lyrics_add_line), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 140.dp)) {
                itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledField(
                                value = row.timeText,
                                onValueChange = { rows[index] = row.copy(timeText = it) },
                                label = stringResource(R.string.lyrics_time_hint),
                                enabled = editable,
                                modifier = Modifier.width(112.dp),
                            )
                            FilledField(
                                value = row.text,
                                onValueChange = { rows[index] = row.copy(text = it) },
                                label = stringResource(R.string.lyrics_line_text),
                                enabled = editable,
                                singleLine = false,
                                modifier = Modifier.weight(1f),
                            )
                            // Takes the time from the real playback position; this is how plain lines become synchronized.
                            IconButton(
                                onClick = { rows[index] = row.copy(timeText = LrcParser.formatTimestamp(position.longValue)) },
                                enabled = editable && isCurrentSong,
                            ) { Icon(AppIcons.Timer, contentDescription = stringResource(R.string.lyrics_stamp)) }
                        }
                    }
                }
                item(key = "add") {
                    FilledTonalButton(
                        onClick = addLine,
                        enabled = editable,
                    ) {
                        Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.lyrics_add_line))
                    }
                }
            }
        }

        }
        // Messages and the floating Cancel / Save pill sit over the bottom of the list, which scrolls under them.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            timeError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            state.issue?.let { Column(Modifier.padding(horizontal = 16.dp)) { IssueText(it) } }
            if (state.noChanges) {
                Text(stringResource(R.string.no_changes), modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (state.saving) {
                Text(stringResource(R.string.saving_metadata), modifier = Modifier.padding(horizontal = 16.dp))
                ChunkyProgress(null, Modifier.padding(16.dp))
            }
            val badTimeTemplate = stringResource(R.string.lyrics_bad_timestamp)
            SaveCancelPill(
                onCancel = { nav.pop() },
                onSave = {
                    val toSave = if (textMode) LyricsEditorModel.fromRaw(bulkText, nextId) else EditorDocument(header, rows.toList())
                    when (val result = LyricsEditorModel.toRaw(toSave)) {
                        is LyricsEditorModel.Result.BadTimestamp -> {
                            timeError = badTimeTemplate.format(result.lineNumber, result.text)
                        }
                        is LyricsEditorModel.Result.Ok -> {
                            timeError = null
                            editor.onValueChange(MetadataField.LYRICS, result.raw.orEmpty())
                            editor.save()
                        }
                    }
                },
                cancelEnabled = !state.saving,
                saveEnabled = editable,
                visible = dirty || state.saving,
            )
        }
        }
    }
}
