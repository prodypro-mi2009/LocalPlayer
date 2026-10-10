package com.localplayer.music.ui.screens

import com.localplayer.music.ui.components.barInsetBelowKeyboard
import android.Manifest
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.localplayer.music.ui.EditEvent
import com.localplayer.music.ui.EditIssue
import com.localplayer.music.ui.EditMetadataViewModel
import com.localplayer.music.ui.components.DetailTopBar
import com.localplayer.music.ui.components.PrimaryArtistDialog
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

    EditEvents(viewModel = viewModel, onSaved = { nav.pop() })

    Column(Modifier.fillMaxSize().imePadding()) {
        DetailTopBar(title = stringResource(R.string.edit_title), onBack = { if (!state.saving) nav.pop() })
        if (state.loading) {
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.loading), modifier = Modifier.padding(top = 12.dp))
            }
            return@Column
        }
        if (state.original == null) {
            state.issue?.let { IssueText(it) }
            return@Column
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                state.displayName,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
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
            for (field in MetadataField.tagScreenFields) {
                val error = state.fieldErrors[field]
                OutlinedTextField(
                    value = state.values[field].orEmpty(),
                    onValueChange = { viewModel.onValueChange(field, it) },
                    label = { Text(stringResource(field.labelRes())) },
                    enabled = state.canEdit && !state.saving,
                    singleLine = field != MetadataField.COMMENT,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (field == MetadataField.TRACK_NUMBER || field == MetadataField.DISC_NUMBER) {
                            KeyboardType.Number
                        } else {
                            KeyboardType.Text
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.track?.let { track ->
                var showDialog by remember { mutableStateOf(false) }
                Text(
                    stringResource(R.string.primary_artist_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    stringResource(R.string.primary_artist_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { showDialog = true }) {
                    Text(track.primaryArtist ?: stringResource(R.string.unknown_artist))
                }
                if (showDialog) {
                    PrimaryArtistDialog(
                        current = track.primaryArtist ?: track.artist,
                        onSave = {
                            viewModel.setPrimaryArtist(it)
                            showDialog = false
                        },
                        onReset = {
                            viewModel.setPrimaryArtist(null)
                            showDialog = false
                        },
                        onDismiss = { showDialog = false },
                    )
                }
            }
            state.issue?.let { IssueText(it) }
            if (state.noChanges) {
                Text(stringResource(R.string.no_changes), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (state.saving) {
            Text(
                stringResource(R.string.saving_metadata),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + barInsetBelowKeyboard()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = { nav.pop() }, enabled = !state.saving, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = viewModel::save,
                enabled = state.canEdit && !state.saving,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.save))
            }
        }
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
