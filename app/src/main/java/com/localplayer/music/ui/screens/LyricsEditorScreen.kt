package com.localplayer.music.ui.screens

import com.localplayer.music.ui.components.barInsetBelowKeyboard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.localplayer.music.ui.components.DetailTopBar
import com.localplayer.music.ui.components.SyncedLyricsList
import com.localplayer.music.ui.components.rememberActiveLyricIndex
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
    EditEvents(viewModel = editor, onSaved = {
        library.notifyLyricsChanged()
        nav.pop()
    })

    Column(Modifier.fillMaxSize().imePadding()) {
        DetailTopBar(title = stringResource(R.string.edit_lyrics), onBack = { if (!state.saving) nav.pop() })
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
        // Text mode: the whole lyrics as one multiline field (paste everything at once). Switching back parses it into rows.
        var textMode by remember { mutableStateOf(false) }
        var bulkText by remember { mutableStateOf("") }
        var header by remember(original) { mutableStateOf(document.header) }
        var timeError by remember { mutableStateOf<String?>(null) }
        val isCurrentSong = playerState.nowPlaying?.mediaId == trackId.toString()
        val position = rememberPlaybackPosition(playerState.isPlaying, playerState.nowPlaying?.mediaId, library::currentPositionMs)
        val editable = state.canEdit && !state.saving

        // Only the problem is shown; the "tags are kept" note is gone.
        if (!state.canEdit) {
            Text(
                stringResource(R.string.edit_unsupported),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Edits not saved yet (the search screen replaces this editor's content).
        val dirty = if (textMode) {
            bulkText != LyricsEditorModel.toText(document)
        } else {
            header != document.header || rows.toList() != document.lines
        }
        val openSearch = { nav.push(Screen.LyricsSearch(trackId)) }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Works for any song, also when it is not playing.
            AssistChip(
                onClick = { if (dirty) confirmSearch = true else openSearch() },
                enabled = !state.saving,
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(stringResource(R.string.lyrics_search_internet), maxLines = 1) },
            )
            FilterChip(
                selected = textMode,
                enabled = editable,
                onClick = {
                    if (!textMode) {
                        bulkText = LyricsEditorModel.toText(EditorDocument(header, rows.toList()))
                    } else {
                        val parsed = LyricsEditorModel.fromRaw(bulkText, nextId)
                        header = parsed.header
                        rows.clear(); rows.addAll(parsed.lines)
                        nextId = (parsed.lines.maxOfOrNull { it.id } ?: nextId) + 1
                    }
                    textMode = !textMode
                },
                label = { Text(stringResource(R.string.lyrics_text_mode), maxLines = 1) },
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
            OutlinedTextField(
                value = bulkText,
                onValueChange = { bulkText = it },
                enabled = editable,
                label = { Text(stringResource(R.string.lyrics_text_mode)) },
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
            )
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = row.timeText,
                                onValueChange = { rows[index] = row.copy(timeText = it) },
                                enabled = editable,
                                singleLine = true,
                                placeholder = { Text(stringResource(R.string.lyrics_time_hint)) },
                                modifier = Modifier.width(104.dp),
                            )
                            Text(" ")
                            OutlinedTextField(
                                value = row.text,
                                onValueChange = { rows[index] = row.copy(text = it) },
                                enabled = editable,
                                label = { Text(stringResource(R.string.lyrics_line_text)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(
                                onClick = { rows[index] = row.copy(timeText = LrcParser.formatTimestamp(position.longValue)) },
                                enabled = editable && isCurrentSong,
                            ) { Icon(AppIcons.Timer, contentDescription = stringResource(R.string.lyrics_stamp)) }
                            IconButton(
                                onClick = { rows.add(index - 1, rows.removeAt(index)) },
                                enabled = editable && index > 0,
                            ) { Icon(AppIcons.ArrowUp, contentDescription = stringResource(R.string.move_up)) }
                            IconButton(
                                onClick = { rows.add(index + 1, rows.removeAt(index)) },
                                enabled = editable && index < rows.lastIndex,
                            ) { Icon(AppIcons.ArrowDown, contentDescription = stringResource(R.string.move_down)) }
                            IconButton(onClick = { rows.removeAt(index) }, enabled = editable) {
                                Icon(AppIcons.Close, contentDescription = stringResource(R.string.remove))
                            }
                        }
                    }
                }
                item(key = "add") {
                    TextButton(
                        onClick = { rows.add(EditorLine(nextId++, "", "")) },
                        enabled = editable,
                    ) {
                        Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.lyrics_add_line))
                    }
                }
            }
        }

        timeError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        state.issue?.let { Column(Modifier.padding(horizontal = 16.dp)) { IssueText(it) } }
        if (state.noChanges) {
            Text(stringResource(R.string.no_changes), modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (state.saving) {
            Text(stringResource(R.string.saving_metadata), modifier = Modifier.padding(horizontal = 16.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + barInsetBelowKeyboard()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = { nav.pop() }, enabled = !state.saving, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cancel))
            }
            val badTimeTemplate = stringResource(R.string.lyrics_bad_timestamp)
            Button(
                onClick = {
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
                enabled = editable,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}
