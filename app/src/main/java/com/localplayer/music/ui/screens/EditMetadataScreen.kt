package com.localplayer.music.ui.screens

import com.localplayer.music.ui.components.SaveCancelPill
import android.Manifest
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.localplayer.music.ui.components.ArtworkImage
import com.localplayer.music.ui.components.artworkRef
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.localplayer.music.AppContainer
import com.localplayer.music.R
import com.localplayer.music.data.metadata.FailureKind
import com.localplayer.music.data.metadata.MetadataField
import com.localplayer.music.data.metadata.MetadataValidation
import com.localplayer.music.ui.EditEvent
import com.localplayer.music.ui.EditIssue
import com.localplayer.music.ui.EditMetadataViewModel
import com.localplayer.music.ui.components.ChunkyProgress
import com.localplayer.music.ui.components.FilledField
import com.localplayer.music.ui.components.SettingsCustomRow
import com.localplayer.music.ui.components.SettingsGroup
import com.localplayer.music.ui.components.SettingsNote
import com.localplayer.music.ui.components.SettingsScaffold
import com.localplayer.music.ui.nav.NavState

private fun FailureKind.messageRes(): Int = when (this) {
    FailureKind.INVALID_INPUT -> R.string.err_invalid_input
    FailureKind.WRITE_PERMISSION_DENIED -> R.string.err_permission
    FailureKind.USER_CANCELLED -> R.string.err_cancelled
    FailureKind.UNSUPPORTED_FORMAT -> R.string.err_format
    FailureKind.UNSUPPORTED_FIELD -> R.string.err_field
    FailureKind.FILE_UNAVAILABLE -> R.string.err_unavailable
    FailureKind.WRITER_ERROR -> R.string.err_writer
    FailureKind.VERIFICATION_FAILED -> R.string.err_verify
    FailureKind.INSUFFICIENT_STORAGE -> R.string.err_storage
    FailureKind.CORRUPT_FILE -> R.string.err_corrupt
    FailureKind.UNKNOWN -> R.string.err_unknown
}

private fun MetadataField.labelRes(): Int = when (this) {
    MetadataField.TITLE -> R.string.field_title
    MetadataField.ARTIST -> R.string.field_artist
    MetadataField.ALBUM -> R.string.field_album
    MetadataField.ALBUM_ARTIST -> R.string.field_album_artist
    MetadataField.GENRE -> R.string.field_genre
    MetadataField.YEAR -> R.string.field_year
    MetadataField.TRACK_NUMBER -> R.string.field_track
    MetadataField.DISC_NUMBER -> R.string.field_disc
    MetadataField.COMPOSER -> R.string.field_composer
    MetadataField.COMMENT -> R.string.field_comment
    MetadataField.REPLAYGAIN_TRACK -> R.string.field_replaygain_track
    MetadataField.REPLAYGAIN_ALBUM -> R.string.field_replaygain_album
    MetadataField.REPLAYGAIN_TRACK_PEAK -> R.string.field_replaygain_track_peak
    MetadataField.REPLAYGAIN_ALBUM_PEAK -> R.string.field_replaygain_album_peak
    MetadataField.LYRICS -> R.string.edit_lyrics
}

/**
 * Edit Metadata. Fields show what the FILE contains. Cancel leaves without any write or permission
 * request; Save writes the changed fields into the file, verifies by reading the file back, and only
 * then shows the success message.
 */
/**
 * Shared by the tag editor and the lyrics editor: the Android write-consent dialog (or the legacy storage
 * permission on Android 8-9) and the "saved" event.
 */
@Composable
fun EditEvents(viewModel: EditMetadataViewModel, onSaved: () -> Unit) {
    val context = LocalContext.current
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onAccessResult(result.resultCode == Activity.RESULT_OK)
    }
    val legacyPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onAccessResult(granted)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EditEvent.RequestConsent ->
                    consentLauncher.launch(IntentSenderRequest.Builder(event.sender).build())
                EditEvent.RequestLegacyPermission ->
                    legacyPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                EditEvent.Saved -> {
                    Toast.makeText(context, R.string.metadata_saved, Toast.LENGTH_SHORT).show()
                    onSaved()
                }
            }
        }
    }
}

@Composable
fun EditMetadataScreen(trackId: Long, nav: NavState, container: AppContainer) {
    val viewModel: EditMetadataViewModel = viewModel(
        key = "edit-metadata-$trackId",
        factory = viewModelFactory { initializer { EditMetadataViewModel(container, trackId) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picked by viewModel.cover.collectAsStateWithLifecycle()
    val coverRemoved by viewModel.coverRemoved.collectAsStateWithLifecycle()
    val finding by viewModel.finding.collectAsStateWithLifecycle()
    val precision by viewModel.precision.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.chooseCover(uri)
    }

    EditEvents(viewModel = viewModel, onSaved = { nav.pop() })

    SettingsScaffold(
        title = stringResource(R.string.edit_title),
        onBack = { if (!state.saving) nav.pop() },
        modifier = Modifier.imePadding(),
        bottomBar = if (state.loading || state.original == null) null else ({
            // The pill is only there while something can be saved (a changed field, a new or removed cover, or a save running).
            val dirty = state.original?.let { MetadataValidation.changesFrom(it, state.values).isNotEmpty() } == true ||
                picked != null || coverRemoved
            Column {
                if (state.saving) {
                    Text(
                        stringResource(R.string.saving_metadata),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    ChunkyProgress(null, Modifier.padding(16.dp))
                }
                SaveCancelPill(
                    onCancel = { nav.pop() },
                    onSave = viewModel::save,
                    cancelEnabled = !state.saving,
                    saveEnabled = state.canEdit && !state.saving,
                    visible = dirty || state.saving,
                )
            }
        }),
    ) {
        if (state.loading) {
            SettingsGroup { item { SettingsCustomRow {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Text(stringResource(R.string.loading))
                }
            } } }
            return@SettingsScaffold
        }
        if (state.original == null) {
            state.issue?.let { issue -> SettingsGroup { item { SettingsCustomRow { IssueText(issue) } } } }
            return@SettingsScaffold
        }

        SettingsGroup {
            item {
                SettingsCustomRow {
                    Text(state.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.format_label, state.formatLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(if (state.canEdit) R.string.keeps_hidden_tags else R.string.edit_unsupported),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.canEdit) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        // Cover: choose / remove here, written together with the tags by the one Save below.
        val hasCover = (state.original?.artworkCount ?: 0) > 0
        val canCover = state.canEdit && !state.saving
        SettingsGroup(
            title = stringResource(R.string.edit_cover),
            note = stringResource(if (state.canEdit) R.string.cover_note else R.string.cover_unsupported),
        ) {
            item {
                SettingsCustomRow {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val shown = picked
                        when {
                            shown != null -> Image(
                                bitmap = shown.preview,
                                contentDescription = stringResource(R.string.cover_new),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(200.dp).clip(RoundedCornerShape(24.dp)),
                            )
                            coverRemoved -> Text(stringResource(R.string.cover_will_remove), color = MaterialTheme.colorScheme.error)
                            hasCover -> ArtworkImage(artwork = state.track?.artworkRef(), size = 200.dp, cornerRadius = 24.dp)
                            else -> Text(stringResource(R.string.cover_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = canCover,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.cover_choose)) }
                        if (picked != null || coverRemoved) {
                            OutlinedButton(onClick = viewModel::clearCoverChoice, enabled = canCover, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.undo))
                            }
                        } else if (hasCover) {
                            OutlinedButton(onClick = viewModel::removeCover, enabled = canCover, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.cover_remove))
                            }
                        }
                    }
                }
            }
        }
        SettingsGroup {
            for (field in MetadataField.tagScreenFields) {
                item {
                    SettingsCustomRow {
                        FilledField(
                            value = state.values[field].orEmpty(),
                            onValueChange = { viewModel.onValueChange(field, it) },
                            label = stringResource(field.labelRes()),
                            enabled = state.canEdit && !state.saving && state.capability?.writableFields?.contains(field) == true,
                            singleLine = field != MetadataField.COMMENT,
                            isError = state.fieldErrors[field] != null,
                            supportingText = state.fieldErrors[field],
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (field == MetadataField.TRACK_NUMBER || field == MetadataField.DISC_NUMBER) {
                                    KeyboardType.Number
                                } else {
                                    KeyboardType.Text
                                },
                            ),
                        )
                    }
                }
            }
        }
        if (state.capability?.writableFields?.containsAll(MetadataField.gainFields) == true) {
            SettingsGroup(
                note = stringResource(R.string.rg_find_note) + when (precision) {
                    true -> "\n" + stringResource(R.string.rg_precision_float)
                    false -> "\n" + stringResource(R.string.rg_precision_16)
                    null -> ""
                },
            ) {
                item {
                    SettingsCustomRow {
                        FilledTonalButton(
                            onClick = viewModel::findGain,
                            enabled = state.canEdit && !state.saving && !finding,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            if (finding) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                stringResource(if (finding) R.string.rg_finding else R.string.rg_find),
                                modifier = Modifier.padding(start = if (finding) 10.dp else 0.dp),
                            )
                        }
                    }
                }
            }
        }
        state.track?.let { track ->
            // Its own little form with its own Save: this only touches the library (never the file), so it works
            // without any change in the tag fields above.
            val saved = track.primaryArtist.orEmpty()
            var draft by remember(saved) { mutableStateOf(saved) }
            SettingsGroup(title = stringResource(R.string.primary_artist_title), note = stringResource(R.string.primary_artist_note)) {
                item {
                    SettingsCustomRow {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledField(
                                value = draft,
                                onValueChange = { draft = it },
                                label = stringResource(R.string.primary_artist_title),
                                modifier = Modifier.weight(1f),
                                enabled = !state.saving,
                            )
                            Button(
                                onClick = { viewModel.setPrimaryArtist(draft.trim().ifEmpty { null }) },
                                enabled = !state.saving && draft.trim() != saved.trim(),
                                modifier = Modifier.height(56.dp),
                            ) { Text(stringResource(R.string.save)) }
                        }
                    }
                }
            }
        }
        state.issue?.let { issue -> SettingsGroup { item { SettingsCustomRow { IssueText(issue) } } } }
        if (state.noChanges) SettingsNote(stringResource(R.string.no_changes))
    }
}

@Composable
fun IssueText(issue: EditIssue) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            stringResource(issue.kind.messageRes()),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (issue.detail.isNotBlank()) {
            Text(
                stringResource(R.string.error_details, issue.detail),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
