package com.localplayer.music.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.localplayer.music.AppContainer
import com.localplayer.music.R
import com.localplayer.music.data.metadata.MetadataField
import com.localplayer.music.ui.BulkEditViewModel
import com.localplayer.music.ui.components.DetailTopBar
import com.localplayer.music.ui.components.barInsetBelowKeyboard
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.ui.nav.Screen

private val bulkFields = listOf(
    MetadataField.ARTIST to R.string.field_artist,
    MetadataField.ALBUM_ARTIST to R.string.field_album_artist,
    MetadataField.ALBUM to R.string.field_album,
    MetadataField.GENRE to R.string.field_genre,
    MetadataField.YEAR to R.string.field_year,
)

/** Album > Edit tags of all songs: the same artist / album / genre / year for every song of the album. */
@Composable
fun BulkEditScreen(screen: Screen.BulkEditAlbum, nav: NavState, container: AppContainer) {
    val vm: BulkEditViewModel = viewModel(
        key = "bulk-edit-${screen.groupKey}",
        factory = viewModelFactory { initializer { BulkEditViewModel(container, screen.groupKey) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        vm.onConsentResult(result.resultCode == android.app.Activity.RESULT_OK)
    }
    LaunchedEffect(vm) { vm.consent.collect { consentLauncher.launch(IntentSenderRequest.Builder(it).build()) } }

    val count = state.tracks?.size ?: 0
    Column(Modifier.fillMaxSize().imePadding()) {
        DetailTopBar(title = stringResource(R.string.bulk_edit_title), onBack = { nav.pop() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(screen.title.ifEmpty { stringResource(R.string.unknown_album) }, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.bulk_edit_note, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for ((field, label) in bulkFields) {
                OutlinedTextField(
                    value = state.values[field].orEmpty(),
                    onValueChange = { vm.onValueChange(field, it) },
                    label = { Text(stringResource(label)) },
                    singleLine = true,
                    enabled = !state.running,
                    isError = field in state.fieldErrors,
                    supportingText = state.fieldErrors[field]?.let { { Text(it) } },
                    keyboardOptions = if (field == MetadataField.YEAR) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.nothingToSave) Text(stringResource(R.string.bulk_edit_nothing), color = MaterialTheme.colorScheme.error)
            if (state.cancelled) Text(stringResource(R.string.err_cancelled), color = MaterialTheme.colorScheme.error)
            if (state.running && count > 0) {
                LinearProgressIndicator(progress = { (state.done + state.failed).toFloat() / count }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.bulk_edit_progress, state.done + state.failed, count), style = MaterialTheme.typography.bodySmall)
            }
            if (state.finished) {
                Text(stringResource(R.string.bulk_edit_result, state.done, state.failed), style = MaterialTheme.typography.bodyMedium)
                if (state.firstError.isNotEmpty()) {
                    Text(state.firstError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Button(onClick = vm::save, enabled = !state.running && count > 0, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.bulk_edit_save, count))
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(barInsetBelowKeyboard()))
        }
    }
}
