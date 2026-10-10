package com.localplayer.music.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localplayer.music.R
import com.localplayer.music.playback.NowPlaying
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.ArtworkImage

@Composable
fun MiniPlayer(
    now: NowPlaying,
    isPlaying: Boolean,
    hasNext: Boolean,
    durationMs: Long,
    positionMs: () -> Long,
    onOpen: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val position = rememberPlaybackPosition(isPlaying, now.mediaId, positionMs)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.open_player), onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkImage(artwork = now.artwork, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = now.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = now.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = AppIcons.SkipPrevious,
                    contentDescription = stringResource(R.string.previous_track),
                )
            }
            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    imageVector = if (isPlaying) AppIcons.Pause else AppIcons.Play,
                    contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                )
            }
            IconButton(onClick = onNext, enabled = hasNext) {
                Icon(
                    imageVector = AppIcons.SkipNext,
                    contentDescription = stringResource(R.string.next_track),
                )
            }
        }
        LinearProgressIndicator(
            progress = {
                if (durationMs > 0) (position.longValue.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
        )
    }
}
