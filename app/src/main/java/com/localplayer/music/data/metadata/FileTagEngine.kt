package com.localplayer.music.data.metadata

import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.exceptions.CannotReadException
import org.jaudiotagger.audio.exceptions.CannotWriteException
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagException
import org.jaudiotagger.tag.TagOptionSingleton
import java.io.File
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Reads and writes real tags inside an audio FILE (a plain java.io.File working copy).
 * No Android classes and no database in here: this is the layer that makes the file the
 * source of truth. The audio stream is never decoded or re-encoded; jaudiotagger only
 * rewrites the tag area of the container.
 *
 * Format adapters (ID3 / Vorbis comments / MP4 atoms) are all reached through the same
 * jaudiotagger Tag API, with the per-format differences handled in [applyChange].
 */
object FileTagEngine {

    @Volatile
    private var configured = false

    /** [android] must be true inside the app (selects Android image classes). Plain JVM tests pass false. */
    @Synchronized
    fun configure(android: Boolean) {
        if (configured) return
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
        TagOptionSingleton.getInstance().apply {
            isAndroid = android
            // Keep ID3v1 exactly as it is (never create, delete or truncate it).
            isId3v1Save = false
            // Write genre as the exact text the user typed, not as a numeric ID3v1 genre code.
            isWriteMp3GenresAsText = true
        }
        configured = true
    }

    fun read(file: File, container: AudioContainer): TrackMetadata {
        check(configured) { "FileTagEngine.configure() must be called first" }
        val audioFile = open(file, container)
        val tag: Tag? = audioFile.tag
        val durationMs = try {
            (audioFile.audioHeader.preciseTrackLength * 1000.0).toLong()
        } catch (e: Exception) {
            0L
        }
        if (tag == null) {
            return TrackMetadata(container = container, durationMs = durationMs)
        }
        return TrackMetadata(
            container = container,
            title = text(tag, FieldKey.TITLE),
            artist = text(tag, FieldKey.ARTIST),
            album = text(tag, FieldKey.ALBUM),
            albumArtist = text(tag, FieldKey.ALBUM_ARTIST),
            genre = text(tag, FieldKey.GENRE),
            year = text(tag, FieldKey.YEAR),
            trackNumber = text(tag, FieldKey.TRACK),
            discNumber = text(tag, FieldKey.DISC_NO),
            composer = text(tag, FieldKey.COMPOSER),
            comment = text(tag, FieldKey.COMMENT),
            lyrics = text(tag, FieldKey.LYRICS),
            artists = all(tag, FieldKey.ARTIST),
            artworkCount = try {
                tag.artworkList.size
            } catch (e: Exception) {
                0
            },
            artworkFingerprints = artworkFingerprints(tag),
            durationMs = durationMs,
        )
    }

    /**
     * Minimal update: only the fields in [changes] are touched, everything else in the tag
     * (artwork, lyrics, unknown frames, other fields) is kept as it was read.
     */
    fun write(file: File, container: AudioContainer, changes: MetadataChanges) {
        check(configured) { "FileTagEngine.configure() must be called first" }
        if (!container.tagWritable) {
            throw MetadataException(
                FailureKind.UNSUPPORTED_FORMAT,
                "Metadata editing is not supported safely for ${container.label}.",
            )
        }
        val audioFile = open(file, container)
        val tag = audioFile.tagOrCreateAndSetDefault
        try {
            for ((field, value) in changes) applyChange(tag, field, value)
            audioFile.commit()
        } catch (e: MetadataException) {
            throw e
        } catch (e: CannotWriteException) {
            throw writerFailure(e)
        } catch (e: IOException) {
            throw writerFailure(e)
        } catch (e: TagException) {
            throw MetadataException(FailureKind.WRITER_ERROR, e.message ?: "Tag error", e)
        } catch (e: RuntimeException) {
            throw MetadataException(FailureKind.WRITER_ERROR, e.message ?: e.javaClass.simpleName, e)
        }
    }

    /**
     * Compares the file as re-read after writing with the file before writing.
     * Returns null when everything is as expected, otherwise a human-readable reason.
     */
    fun verify(before: TrackMetadata, after: TrackMetadata, changes: MetadataChanges): String? {
        for (field in MetadataField.entries) {
            val actual = after.value(field)
            if (changes.containsKey(field)) {
                val expected = changes[field]
                if (actual != expected) {
                    return "${field.name}: expected ${quote(expected)} but the file contains ${quote(actual)}"
                }
            } else if (actual != before.value(field)) {
                return "${field.name} changed unexpectedly (${quote(before.value(field))} -> ${quote(actual)})"
            }
        }
        if (after.artworkCount != before.artworkCount) {
            return "Artwork count changed (${before.artworkCount} -> ${after.artworkCount})"
        }
        if (after.artworkFingerprints != before.artworkFingerprints) {
            return "Embedded artwork is not byte-for-byte identical after the write"
        }
        if (kotlin.math.abs(after.durationMs - before.durationMs) > 150) {
            return "Audio duration changed (${before.durationMs} ms -> ${after.durationMs} ms)"
        }
        return null
    }

    // --- internals --------------------------------------------------------------------------

    private fun open(file: File, container: AudioContainer): AudioFile {
        if (container == AudioContainer.UNKNOWN || container == AudioContainer.AAC_ADTS) {
            throw MetadataException(
                FailureKind.UNSUPPORTED_FORMAT,
                "Tags of ${container.label} cannot be read for editing.",
            )
        }
        try {
            return AudioFileIO.read(file)
        } catch (e: CannotReadException) {
            throw MetadataException(FailureKind.CORRUPT_FILE, e.message ?: "Cannot read file", e)
        } catch (e: InvalidAudioFrameException) {
            throw MetadataException(FailureKind.CORRUPT_FILE, e.message ?: "Invalid audio frame", e)
        } catch (e: TagException) {
            throw MetadataException(FailureKind.CORRUPT_FILE, e.message ?: "Invalid tag", e)
        } catch (e: ReadOnlyFileException) {
            throw MetadataException(FailureKind.WRITER_ERROR, e.message ?: "Read-only file", e)
        } catch (e: IOException) {
            throw MetadataException(FailureKind.FILE_UNAVAILABLE, e.message ?: "I/O error", e)
        }
    }

    private fun applyChange(tag: Tag, field: MetadataField, value: String?) {
        val key = fieldKey(field)
        if (value == null) {
            try {
                tag.deleteField(key)
            } catch (e: RuntimeException) {
                throw unsupportedField(field, e)
            }
            return
        }
        // Track / disc numbers can be stored as "3/12": keep the total the user did not edit.
        val totalKey = when (field) {
            MetadataField.TRACK_NUMBER -> FieldKey.TRACK_TOTAL
            MetadataField.DISC_NUMBER -> FieldKey.DISC_TOTAL
            else -> null
        }
        val totalBefore = totalKey?.let { text(tag, it) }
        try {
            // ID3 may hold several USLT frames; setField can add a second one and getFirst would then keep
            // returning the OLD lyrics. Remove first so the saved text is the only one.
            if (field == MetadataField.LYRICS) tag.deleteField(key)
            tag.setField(key, value)
            if (totalKey != null && totalBefore != null && text(tag, totalKey) != totalBefore) {
                tag.setField(totalKey, totalBefore)
            }
        } catch (e: RuntimeException) {
            throw unsupportedField(field, e)
        } catch (e: TagException) {
            throw unsupportedField(field, e)
        }
    }

    private fun fieldKey(field: MetadataField): FieldKey = when (field) {
        MetadataField.TITLE -> FieldKey.TITLE
        MetadataField.ARTIST -> FieldKey.ARTIST
        MetadataField.ALBUM -> FieldKey.ALBUM
        MetadataField.ALBUM_ARTIST -> FieldKey.ALBUM_ARTIST
        MetadataField.GENRE -> FieldKey.GENRE
        MetadataField.YEAR -> FieldKey.YEAR
        MetadataField.TRACK_NUMBER -> FieldKey.TRACK
        MetadataField.DISC_NUMBER -> FieldKey.DISC_NO
        MetadataField.COMPOSER -> FieldKey.COMPOSER
        MetadataField.COMMENT -> FieldKey.COMMENT
        MetadataField.LYRICS -> FieldKey.LYRICS
    }

    /** Exact fingerprint of each embedded picture: MIME type, picture type and SHA-256 of the bytes. */
    private fun artworkFingerprints(tag: Tag): List<String> = try {
        tag.artworkList.map { artwork ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(artwork.binaryData).joinToString("") { "%02x".format(it) }
            "${artwork.mimeType}:${artwork.pictureType}:$hash"
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun text(tag: Tag, key: FieldKey): String? =
        try {
            tag.getFirst(key).ifEmpty { null }
        } catch (e: RuntimeException) {
            null
        }

    private fun all(tag: Tag, key: FieldKey): List<String> =
        try {
            tag.getAll(key).filter { it.isNotEmpty() }
        } catch (e: RuntimeException) {
            emptyList()
        }

    private fun unsupportedField(field: MetadataField, cause: Throwable) = MetadataException(
        FailureKind.UNSUPPORTED_FIELD,
        "This metadata field is not supported for this file format: ${field.name}",
        cause,
    )

    private fun writerFailure(e: Exception): MetadataException {
        val message = e.message ?: e.javaClass.simpleName
        val kind = if (message.contains("space", ignoreCase = true) || message.contains("ENOSPC")) {
            FailureKind.INSUFFICIENT_STORAGE
        } else {
            FailureKind.WRITER_ERROR
        }
        return MetadataException(kind, message, e)
    }

    private fun quote(value: String?) = if (value == null) "<none>" else "\"$value\""
}
