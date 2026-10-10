package com.localplayer.music.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.localplayer.music.data.db.TrackListItem

/** Identifies the embedded artwork of one track. */
data class ArtworkRef(val trackId: Long, val sourceUri: String, val lastModified: Long)

object MediaItemFactory {
    private const val EXTRA_SOURCE_URI = "source_uri"
    private const val EXTRA_LAST_MODIFIED = "last_modified"
    private const val EXTRA_HAS_ARTWORK = "has_artwork"
    private const val EXTRA_PLAY_NEXT = "play_next"
    private const val EXTRA_OCCURRENCE = "occurrence_id"

    /** URI scheme understood by [ArtworkBitmapLoader]; used so the media notification can show embedded art. */
    const val ARTWORK_SCHEME = "trackart"

    fun create(track: TrackListItem, unknownArtist: String, unknownAlbum: String): MediaItem {
        val extras = Bundle().apply {
            putString(EXTRA_SOURCE_URI, track.uri)
            putLong(EXTRA_LAST_MODIFIED, track.lastModified)
            putBoolean(EXTRA_HAS_ARTWORK, track.hasArtwork)
            // Every queue entry is its own occurrence from the moment it is created, so the same file queued
            // several times can always be told apart (remove / Undo / reorder work on the exact entry).
            putString(EXTRA_OCCURRENCE, QueueOccurrence.newId())
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist ?: unknownArtist)
            .setAlbumTitle(track.album ?: unknownAlbum)
            .setDurationMs(track.durationMs)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(extras)
        if (track.hasArtwork) {
            metadata.setArtworkUri(
                Uri.parse(
                    "$ARTWORK_SCHEME://${track.id}?v=${track.lastModified}&src=${Uri.encode(track.uri)}",
                ),
            )
        }
        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(track.uri)
            .setMediaMetadata(metadata.build())
            .build()
    }

    /** The stable track URI stored in the item. */
    fun trackUri(item: MediaItem): String? = item.mediaMetadata.extras?.getString(EXTRA_SOURCE_URI)

    /** A copy of an item received from the player that can be sent back to it (items from the controller lack their URI). */
    fun playable(item: MediaItem): MediaItem? {
        val uri = trackUri(item) ?: return null
        return item.buildUpon().setUri(uri).build()
    }

    /** A copy of [item] flagged as "inserted by Play next", so repeated Play next calls queue up in order. */
    fun markPlayNext(item: MediaItem): MediaItem {
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle.EMPTY).apply { putBoolean(EXTRA_PLAY_NEXT, true) }
        val metadata = item.mediaMetadata.buildUpon().setExtras(extras).build()
        return item.buildUpon().setMediaMetadata(metadata).build()
    }

    /** A copy of [item] tagged with a unique id for this queue entry (targeted Undo). */
    fun withOccurrence(item: MediaItem, occurrenceId: String): MediaItem {
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle.EMPTY).apply { putString(EXTRA_OCCURRENCE, occurrenceId) }
        val metadata = item.mediaMetadata.buildUpon().setExtras(extras).build()
        return item.buildUpon().setMediaMetadata(metadata).build()
    }

    fun occurrenceId(item: MediaItem): String? = item.mediaMetadata.extras?.getString(EXTRA_OCCURRENCE)

    fun isPlayNext(item: MediaItem): Boolean = item.mediaMetadata.extras?.getBoolean(EXTRA_PLAY_NEXT, false) == true

    fun artworkRef(item: MediaItem): ArtworkRef? {
        val extras = item.mediaMetadata.extras ?: return null
        if (!extras.getBoolean(EXTRA_HAS_ARTWORK)) return null
        val trackId = item.mediaId.toLongOrNull() ?: return null
        val source = extras.getString(EXTRA_SOURCE_URI) ?: return null
        return ArtworkRef(trackId, source, extras.getLong(EXTRA_LAST_MODIFIED))
    }
}
