package com.localplayer.music.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.localplayer.music.AppContainer
import com.localplayer.music.R
import com.localplayer.music.data.lyrics.LyricsMatch
import com.localplayer.music.data.lyrics.RankedLyrics
import com.localplayer.music.data.metadata.MetadataField
import com.localplayer.music.ui.EditMetadataViewModel
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.LyricsSearchPhase
import com.localplayer.music.ui.LyricsSearchViewModel
import com.localplayer.music.ui.components.DetailTopBar
import com.localplayer.music.ui.components.LocalBottomBarInset
import com.localplayer.music.ui.nav.NavState

private fun mmss(sec: Double): String {
    val total = sec.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * Internet Search for the lyrics of ONE song (LRCLIB). The search starts when this screen opens, which only the
 * user's "Internet Search" button does. The result is shown first (list + text preview); nothing is written until the
 * user presses "Save to file". Saving reuses the existing verified tag-edit path (EditMetadataViewModel: consent,
 * write into the audio file, re-read, only then success) - there is no second lyrics system and no sidecar file.
 */
@Composable
fun LyricsSearchScreen(trackId: Long, nav: NavState, library: LibraryViewModel, container: AppContainer) {
    val search: LyricsSearchViewModel = viewModel(
        key = "lyrics-search-$trackId",
        factory = viewModelFactory { initializer { LyricsSearchViewModel(container, trackId) } },
    )
    val editor: EditMetadataViewModel = viewModel(
        key = "edit-lyrics-search-$trackId",
        factory = viewModelFactory { initializer { EditMetadataViewModel(container, trackId) } },
    )
    val s by search.state.collectAsStateWithLifecycle()
    val edit by editor.state.collectAsStateWithLifecycle()
    EditEvents(viewModel = editor, onSaved = {
        library.notifyLyricsChanged()
        nav.pop()
    })

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = stringResource(R.string.internet_search), onBack = { if (!edit.saving) nav.pop() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (s.title.isNotEmpty()) {
                Text(
                    listOf(s.title, s.artist).filter { it.isNotBlank() }.joinToString(" - "),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            when (val phase = s.phase) {
                LyricsSearchPhase.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                    Text(stringResource(R.string.ls_searching))
                }
                LyricsSearchPhase.NoResults -> Failure(R.string.ls_no_results, search::search)
                LyricsSearchPhase.OnlyInstrumental -> Failure(R.string.ls_only_instrumental, null)
                LyricsSearchPhase.NoTrack -> Failure(R.string.ls_no_track, null)
                LyricsSearchPhase.Offline -> Failure(R.string.ls_offline, search::search)
                LyricsSearchPhase.RateLimited -> Failure(R.string.ls_rate_limited, search::search)
                is LyricsSearchPhase.ServerError -> Failure(R.string.ls_server_error, search::search, phase.code.toString())
                is LyricsSearchPhase.Results -> {
                    Text(stringResource(R.string.ls_choose), style = MaterialTheme.typography.labelLarge)
                    phase.items.forEachIndexed { index, item ->
                        ResultRow(item, selected = index == s.selected, onClick = { search.select(index) })
                    }
                    val current = s.current
                    if (current != null) {
                        if (current.match == LyricsMatch.DIFFERENT_VERSION) {
                            Text(
                                stringResource(R.string.ls_different_warning),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (current.result.hasSynced && current.result.hasPlain) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = s.preferSynced,
                                    onClick = { search.setPreferSynced(true) },
                                    label = { Text(stringResource(R.string.ls_synced)) },
                                )
                                FilterChip(
                                    selected = !s.preferSynced,
                                    onClick = { search.setPreferSynced(false) },
                                    label = { Text(stringResource(R.string.ls_plain)) },
                                )
                            }
                        }
                        Text(
                            stringResource(if (s.savingSynced) R.string.ls_will_save_synced else R.string.ls_will_save_plain),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Text(
                                s.textToSave.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp),
                            )
                        }

                        // The file decides: unsupported formats are told clearly, never saved to a side file.
                        val canSave = !edit.loading && edit.canEdit && !edit.saving && s.textToSave != null
                        if (!edit.loading && !edit.canEdit) {
                            Text(
                                stringResource(R.string.ls_cannot_embed),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        edit.issue?.let { IssueText(it) }
                        Button(
                            onClick = {
                                s.textToSave?.let { text ->
                                    editor.onValueChange(MetadataField.LYRICS, text)
                                    editor.save()
                                }
                            },
                            enabled = canSave,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(if (edit.saving) R.string.ls_saving else R.string.ls_save_to_file))
                        }
                        if (edit.noChanges) {
                            Text(stringResource(R.string.ls_already_same), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            stringResource(R.string.ls_source_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp + LocalBottomBarInset.current))
        }
    }
}

@Composable
private fun Failure(message: Int, retry: (() -> Unit)?, arg: String? = null) {
    Text(
        if (arg == null) stringResource(message) else stringResource(message, arg),
        style = MaterialTheme.typography.bodyLarge,
    )
    if (retry != null) {
        OutlinedButton(onClick = retry) { Text(stringResource(R.string.ls_retry)) }
    }
}

@Composable
private fun ResultRow(item: RankedLyrics, selected: Boolean, onClick: () -> Unit) {
    val r = item.result
    OutlinedCard(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
            Column(Modifier.weight(1f)) {
                Text(listOf(r.trackName, r.artistName).joinToString(" - "), style = MaterialTheme.typography.bodyLarge)
                val details = listOfNotNull(
                    r.albumName?.takeIf { it.isNotBlank() },
                    r.durationSec.takeIf { it > 0 }?.let { mmss(it) },
                ).joinToString(" · ")
                if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall)
                val kinds = listOfNotNull(
                    if (r.hasSynced) stringResource(R.string.ls_synced) else null,
                    if (r.hasPlain) stringResource(R.string.ls_plain) else null,
                ).joinToString(" + ")
                val badge = stringResource(
                    when (item.match) {
                        LyricsMatch.GOOD -> R.string.ls_match_good
                        LyricsMatch.POSSIBLE -> R.string.ls_match_possible
                        LyricsMatch.DIFFERENT_VERSION -> R.string.ls_match_different
                    },
                )
                Text(
                    "$kinds · $badge",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.match == LyricsMatch.DIFFERENT_VERSION) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
