package com.localplayer.music.ui.components

import android.Manifest
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.music.R
import com.localplayer.music.ui.RecoveryEvent
import com.localplayer.music.ui.RecoveryViewModel
import java.text.DateFormat
import java.util.Date

/** The question "restore the original?" after an interrupted tag save, plus the system write-consent it may need. */
@Composable
fun RecoveryPrompt(viewModel: RecoveryViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onAccessResult(it.resultCode == Activity.RESULT_OK)
    }
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onAccessResult(it)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RecoveryEvent.RequestConsent -> consent.launch(IntentSenderRequest.Builder(event.sender).build())
                RecoveryEvent.RequestLegacyPermission -> legacy.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                is RecoveryEvent.Message -> Toast.makeText(context, event.res, Toast.LENGTH_LONG).show()
            }
        }
    }
    val item = state.item ?: return
    AlertDialog(
        onDismissRequest = { if (!state.busy) viewModel.later() },
        title = { Text(stringResource(R.string.recovery_title)) },
        text = { Text(stringResource(
            R.string.recovery_message,
            item.displayName,
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.startedAt)),
        )) },
        confirmButton = {
            TextButton(onClick = viewModel::restore, enabled = !state.busy) { Text(stringResource(R.string.recovery_restore)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::later, enabled = !state.busy) { Text(stringResource(R.string.recovery_later)) }
        },
    )
}
