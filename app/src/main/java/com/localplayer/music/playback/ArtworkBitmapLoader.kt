package com.localplayer.music.playback

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.localplayer.music.data.artwork.ArtworkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Lets the media notification / lock screen show embedded artwork without any network access.
 * Handles the private `trackart://<trackId>?v=<lastModified>&src=<encoded file uri>` scheme.
 */
@OptIn(UnstableApi::class)
class ArtworkBitmapLoader(
    private val artwork: ArtworkRepository,
    private val scope: CoroutineScope,
) : BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        return if (bitmap != null) {
            Futures.immediateFuture(bitmap)
        } else {
            Futures.immediateFailedFuture(IllegalArgumentException("Undecodable artwork"))
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        val trackId = uri.host?.toLongOrNull()
        val source = uri.getQueryParameter("src")
        val modified = uri.getQueryParameter("v")?.toLongOrNull() ?: 0L
        if (uri.scheme != MediaItemFactory.ARTWORK_SCHEME || trackId == null || source == null) {
            future.setException(IllegalArgumentException("Unsupported artwork uri"))
            return future
        }
        scope.launch {
            val bitmap = artwork.load(trackId, source, modified, NOTIFICATION_ARTWORK_PX)
            if (bitmap != null) {
                future.set(bitmap)
            } else {
                future.setException(IllegalStateException("No artwork available"))
            }
        }
        return future
    }

    private companion object {
        const val NOTIFICATION_ARTWORK_PX = 512
    }
}
