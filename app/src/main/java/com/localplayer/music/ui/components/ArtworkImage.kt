package com.localplayer.music.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.localplayer.music.LocalPlayerApp
import com.localplayer.music.playback.ArtworkRef

/** Square artwork with a neutral placeholder. Loads a downsampled bitmap only while it is on screen. */
@Composable
fun ArtworkImage(
    artwork: ArtworkRef?,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 6.dp,
) {
    val repository = (LocalContext.current.applicationContext as LocalPlayerApp).container.artwork
    val targetPx = with(LocalDensity.current) { size.roundToPx() }
    // State is keyed by the artwork reference: when the track changes, the old bitmap is dropped
    // immediately (never shown for the new track), and the previous load is cancelled by
    // LaunchedEffect, so a slow response for track A can never overwrite track B.
    var bitmap by remember(artwork, targetPx) {
        mutableStateOf<Bitmap?>(
            artwork?.let { repository.peek(it.trackId, it.lastModified, targetPx) },
        )
    }
    LaunchedEffect(artwork, targetPx) {
        if (artwork != null && bitmap == null) {
            bitmap = repository.load(artwork.trackId, artwork.sourceUri, artwork.lastModified, targetPx)
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = AppIcons.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2),
            )
        }
    }
}
