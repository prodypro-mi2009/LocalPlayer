package com.localplayer.music.playback

import androidx.core.net.toUri
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
    private const val EXTRA_RANK = "shuffle_rank"
    private const val EXTRA_ADDED_BY = "jam_added_by"

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
            metadata.setArtworkUri("$ARTWORK_SCHEME://${track.id}?v=${track.lastModified}&src=${Uri.encode(track.uri)}".toUri())
        }
        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(track.uri)
            .setMediaMetadata(metadata.build())
            .build()
    }

    /**
     * A song a Jam guest sent to this phone: a temporary file that is not part of the library. [addedBy] is the
     * guest's name; its presence is what marks the item as a Jam song.
     */
    fun createJam(
        file: java.io.File,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        addedBy: String,
        unknownArtist: String,
        unknownAlbum: String,
    ): MediaItem {
        val uri = Uri.fromFile(file).toString()
        val extras = Bundle().apply {
            putString(EXTRA_SOURCE_URI, uri)
            putString(EXTRA_ADDED_BY, addedBy)
            putString(EXTRA_OCCURRENCE, QueueOccurrence.newId())
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist.ifBlank { unknownArtist })
            .setAlbumTitle(album.ifBlank { unknownAlbum })
            .setDurationMs(durationMs.takeIf { it > 0L })
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId("jam:" + file.nameWithoutExtension)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    /** Name of the Jam guest who added this item; null for the host's own songs. */
    fun addedBy(item: MediaItem): String? = item.mediaMetadata.extras?.getString(EXTRA_ADDED_BY)

    /** The library URI of the item (history, favourites, statistics); null for a Jam song, which is not in the library. */
    fun libraryUri(item: MediaItem): String? = if (addedBy(item) != null) null else trackUri(item)

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

    /** A copy of [item] remembering the queue position it had when Shuffle was turned on. */
    fun withRank(item: MediaItem, rank: Int): MediaItem {
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle.EMPTY).apply { putInt(EXTRA_RANK, rank) }
        val metadata = item.mediaMetadata.buildUpon().setExtras(extras).build()
        return item.buildUpon().setMediaMetadata(metadata).build()
    }

    fun withoutRank(item: MediaItem): MediaItem {
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle.EMPTY).apply { remove(EXTRA_RANK) }
        val metadata = item.mediaMetadata.buildUpon().setExtras(extras).build()
        return item.buildUpon().setMediaMetadata(metadata).build()
    }

    /** A copy without the Play next flag and without a shuffle rank (a queue that is being replaced starts clean). */
    fun clean(item: MediaItem): MediaItem {
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle.EMPTY).apply {
            remove(EXTRA_RANK)
            remove(EXTRA_PLAY_NEXT)
        }
        val metadata = item.mediaMetadata.buildUpon().setExtras(extras).build()
        return item.buildUpon().setMediaMetadata(metadata).build()
    }

    /** -1 when the item has no rank. */
    fun rank(item: MediaItem): Int = item.mediaMetadata.extras?.getInt(EXTRA_RANK, -1) ?: -1

    /** Rebuilds a saved queue entry: same occurrence id, Play next flag and rank as when it was saved. */
    fun restored(
        track: TrackListItem,
        unknownArtist: String,
        unknownAlbum: String,
        occurrenceId: String,
        playNext: Boolean,
        rank: Int,
    ): MediaItem {
        var item = withOccurrence(create(track, unknownArtist, unknownAlbum), occurrenceId)
        if (playNext) item = markPlayNext(item)
        if (rank >= 0) item = withRank(item, rank)
        return item
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
