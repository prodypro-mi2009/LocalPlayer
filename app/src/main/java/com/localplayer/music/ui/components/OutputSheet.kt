package com.localplayer.music.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.localplayer.music.R
import com.localplayer.music.util.AudioOutput

/**
 * The app's own "Sound and output" window (same style as the settings cards): the media volume and the device the
 * sound goes to. Android does not let an app switch the output itself, so "Change output" opens the system's own picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val am = remember { context.getSystemService(AudioManager::class.java) }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableIntStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    // The hardware volume keys change it too while the window is open.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                volume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val device = remember { AudioOutput.currentName(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp),
        ) {
            Text(
                stringResource(R.string.output_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
            )
            SettingsGroup {
                item {
                    SettingsSliderRow(
                        title = stringResource(R.string.output_volume),
                        valueText = "${volume * 100 / max}%",
                        value = volume.toFloat(),
                        onChange = {
                            val v = it.toInt().coerceIn(0, max)
                            volume = v
                            am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                        },
                        range = 0f..max.toFloat(),
                        steps = max - 1,
                    )
                }
                item {
                    SettingsLinkRow(
                        title = stringResource(R.string.output_playing_on),
                        summary = device ?: stringResource(R.string.output_speaker),
                        onClick = {
                            onDismiss()
                            AudioOutput.openPicker(context)
                        },
                        trailing = { Text(stringResource(R.string.output_change), color = MaterialTheme.colorScheme.primary) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
