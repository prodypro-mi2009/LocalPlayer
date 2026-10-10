package com.localplayer.music.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.localplayer.music.AppContainer
import com.localplayer.music.R
import com.localplayer.music.ui.EditMetadataViewModel
import com.localplayer.music.ui.components.ArtworkImage
import com.localplayer.music.ui.components.ConfirmDialog
import com.localplayer.music.ui.components.DetailTopBar
import com.localplayer.music.ui.components.artworkRef
import com.localplayer.music.ui.components.barInsetBelowKeyboard
import com.localplayer.music.ui.nav.NavState

/**
 * Edit cover: choose a picture (system photo picker, no storage permission) and write it INTO the audio file as the
 * embedded cover, or remove the embedded cover. It uses the same verified write as the tag editor: backup, edit a
 * working copy, write, read the file back and compare the cover byte for byte; the original is restored on failure.
 */
@Composable
fun EditCoverScreen(trackId: Long, nav: NavState, container: AppContainer) {
    val editor: EditMetadataViewModel = viewModel(
        key = "edit-cover-$trackId",
        factory = viewModelFactory { initializer { EditMetadataViewModel(container, trackId) } },
    )
    val state by editor.state.collectAsStateWithLifecycle()
    val choice by editor.cover.collectAsStateWithLifecycle()
    var confirmRemove by remember { mutableStateOf(false) }

    EditEvents(viewModel = editor, onSaved = { nav.pop() })

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) editor.chooseCover(uri)
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = stringResource(R.string.edit_cover), onBack = { if (!state.saving) nav.pop() })
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
        val hasCover = (state.original?.artworkCount ?: 0) > 0
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Text(
                stringResource(if (state.canEdit) R.string.cover_note else R.string.cover_unsupported),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.canEdit) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )

            val picked = choice
            Text(
                stringResource(if (picked != null) R.string.cover_new else R.string.cover_current),
                style = MaterialTheme.typography.labelLarge,
            )
            if (picked != null) {
                Image(
                    bitmap = picked.preview,
                    contentDescription = stringResource(R.string.cover_new),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(240.dp).clip(RoundedCornerShape(24.dp)),
                )
            } else if (hasCover) {
                ArtworkImage(artwork = state.track?.artworkRef(), size = 240.dp, cornerRadius = 24.dp)
            } else {
                Text(stringResource(R.string.cover_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = state.canEdit && !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.cover_choose)) }
            if (hasCover && picked == null) {
                OutlinedButton(
                    onClick = { confirmRemove = true },
                    enabled = state.canEdit && !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.cover_remove)) }
            }
            state.issue?.let { IssueText(it) }
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
                onClick = editor::saveCover,
                enabled = state.canEdit && !state.saving && choice != null,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.save)) }
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.cover_remove),
            body = stringResource(R.string.cover_remove_body),
            confirmLabel = stringResource(R.string.remove),
            onConfirm = {
                confirmRemove = false
                editor.removeCover()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}
