package com.localplayer.music.data.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

/** Reads tags from a local file with the platform MediaMetadataRetriever. No network involved. */
class MetadataReader(private val context: Context) {

    data class Result(
        val title: String?,
        val artist: String?,
        val albumArtist: String?,
        val album: String?,
        val genre: String?,
        val year: Int?,
        val trackNumber: Int?,
        val discNumber: Int?,
        val durationMs: Long,
        val hasArtwork: Boolean,
    )

    /**
     * Returns null when the file cannot be opened or has no audio track
     * (corrupt file, unsupported content).
     * Individual missing tags never cause a failure.
     */
    fun read(uri: Uri): Result? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            if (hasAudio != null && hasAudio != "yes") return null

            fun tag(key: Int): String? =
                try {
                    retriever.extractMetadata(key)?.trim()?.ifBlank { null }
                } catch (e: Exception) {
                    null
                }

            val yearText = tag(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?: tag(MediaMetadataRetriever.METADATA_KEY_DATE)
            val artwork = try {
                retriever.embeddedPicture != null
            } catch (e: Exception) {
                false
            }

            Result(
                title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                albumArtist = tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                genre = tag(MediaMetadataRetriever.METADATA_KEY_GENRE),
                year = parseYear(yearText),
                trackNumber = parseLeadingInt(tag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)),
                discNumber = parseLeadingInt(tag(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)),
                durationMs = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                hasArtwork = artwork,
            )
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Nothing useful to do.
            }
        }
    }

    private fun parseYear(text: String?): Int? {
        if (text == null) return null
        val match = Regex("(\\d{4})").find(text) ?: return null
        return match.value.toIntOrNull()
    }

    /** "3/12" -> 3, "03" -> 3, garbage -> null. */
    private fun parseLeadingInt(text: String?): Int? =
        text?.substringBefore('/')?.trim()?.toIntOrNull()
}
