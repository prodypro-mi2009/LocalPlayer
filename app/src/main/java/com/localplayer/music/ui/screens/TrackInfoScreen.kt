package com.localplayer.music.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localplayer.music.R
import com.localplayer.music.data.library.FolderKeys
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.SettingsCustomRow
import com.localplayer.music.ui.components.SettingsGroup
import com.localplayer.music.ui.components.SettingsScaffold
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.util.formatDuration
import java.text.DateFormat
import java.util.Date

/** Everything the Details screen shows about one song. Unknown values are null and shown as "-". */
data class TrackInfo(
    val title: String,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val genre: String?,
    val year: Int?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long,
    val sizeBytes: Long,
    val format: String?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
    val dateAdded: Long,
    val lastModified: Long,
    val path: String,
) {
    companion object {
        /** A readable path: "/storage/emulated/0/Music/Rock/a.mp3" for the main storage, "/storage/<volume>/..." otherwise. */
        fun pathOf(folderKey: String?, displayName: String): String {
            if (folderKey == null) return displayName
            val volume = FolderKeys.volumeOf(folderKey)
            val relative = FolderKeys.relativePathOf(folderKey)
            val root = if (volume == "external_primary" || volume == "primary") "/storage/emulated/0" else "/storage/$volume"
            return listOf(root, relative, displayName).filter { it.isNotEmpty() }.joinToString("/")
        }
    }
}

/** Song > Info: the tags, the file and the technical values, in the app's own grouped style. */
@Composable
fun TrackInfoScreen(trackId: Long, nav: NavState, viewModel: LibraryViewModel) {
    val info by produceState<TrackInfo?>(initialValue = null, trackId) { value = viewModel.trackInfo(trackId) }
    val context = LocalContext.current
    SettingsScaffold(title = stringResource(R.string.track_info_title), onBack = { nav.pop() }) {
        val i = info ?: return@SettingsScaffold
        val dash = "-"
        val dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
        fun date(ms: Long): String = if (ms > 0) dates.format(Date(ms)) else dash

        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(i.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            val sub = listOfNotNull(i.artist, i.album).joinToString(" • ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsGroup(title = stringResource(R.string.ti_group_tags)) {
            item { InfoRow(stringResource(R.string.ti_title), i.title) }
            item { InfoRow(stringResource(R.string.ti_artist), i.artist ?: dash) }
            item { InfoRow(stringResource(R.string.ti_album), i.album ?: dash) }
            item { InfoRow(stringResource(R.string.ti_album_artist), i.albumArtist ?: dash) }
            item { InfoRow(stringResource(R.string.ti_genre), i.genre ?: dash) }
            item { InfoRow(stringResource(R.string.ti_year), i.year?.toString() ?: dash) }
            item {
                InfoPair(
                    stringResource(R.string.ti_track_no), i.trackNumber?.toString() ?: dash,
                    stringResource(R.string.ti_disc_no), i.discNumber?.toString() ?: dash,
                )
            }
        }
        SettingsGroup(title = stringResource(R.string.ti_group_file)) {
            item {
                InfoPair(
                    stringResource(R.string.ti_duration), if (i.durationMs > 0) formatDuration(i.durationMs) else dash,
                    stringResource(R.string.ti_size), if (i.sizeBytes > 0) Formatter.formatShortFileSize(context, i.sizeBytes) else dash,
                )
            }
            item { InfoRow(stringResource(R.string.ti_format), i.format ?: dash) }
            item {
                InfoPair(
                    stringResource(R.string.ti_bitrate), i.bitrateKbps?.let { "$it kb/s" } ?: dash,
                    stringResource(R.string.ti_sample_rate), i.sampleRateHz?.let { "$it Hz" } ?: dash,
                )
            }
            item { InfoRow(stringResource(R.string.ti_date_added), date(i.dateAdded)) }
            item { InfoRow(stringResource(R.string.ti_date_modified), date(i.lastModified)) }
            item { InfoRow(stringResource(R.string.ti_path), i.path) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    SettingsCustomRow {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
private fun InfoPair(labelA: String, valueA: String, labelB: String, valueB: String) {
    SettingsCustomRow {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(labelA, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(valueA, style = MaterialTheme.typography.bodyLarge)
            }
            Column(Modifier.weight(1f)) {
                Text(labelB, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(valueB, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
