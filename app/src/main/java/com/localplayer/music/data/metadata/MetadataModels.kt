package com.localplayer.music.data.metadata

/** The editable file-metadata fields. Every one of them lives INSIDE the audio file. */
enum class MetadataField {
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    GENRE,
    YEAR,
    TRACK_NUMBER,
    DISC_NUMBER,
    COMPOSER,
    COMMENT,
    /** Embedded lyrics (plain text or LRC). Edited in the lyrics editor, not in the tag screen. */
    LYRICS,
    ;

    companion object {
        /** The fields of the "Edit tag" screen (everything except lyrics). */
        val tagScreenFields: List<MetadataField> get() = entries.filter { it != LYRICS }
    }
}

/** Tag container / file format as detected from the file content (not just the extension). */
enum class AudioContainer(
    val label: String,
    /** Extension a working copy needs so the tag library picks the right reader. */
    val workingExtension: String,
    val tagWritable: Boolean,
) {
    MP3("MP3 (ID3 tags)", "mp3", true),
    FLAC("FLAC (Vorbis comments)", "flac", true),
    MP4("MP4 / M4A (iTunes atoms)", "m4a", true),
    AAC_ADTS("Raw AAC (ADTS)", "aac", false),
    UNKNOWN("Unknown format", "bin", false),
}

/**
 * Metadata exactly as stored in the file. Null = the file has no such field.
 * Text is never trimmed or normalized here.
 */
data class TrackMetadata(
    val container: AudioContainer,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val trackNumber: String? = null,
    val discNumber: String? = null,
    val composer: String? = null,
    val comment: String? = null,
    val lyrics: String? = null,
    /** Structured multi-value ARTIST entries when the file stores several (e.g. Vorbis ARTIST x2). */
    val artists: List<String> = emptyList(),
    val artworkCount: Int = 0,
    /** "mime:pictureType:sha256" of every embedded picture, to prove artwork is byte-for-byte unchanged. */
    val artworkFingerprints: List<String> = emptyList(),
    val durationMs: Long = 0L,
) {
    val hasArtwork: Boolean get() = artworkCount > 0

    fun value(field: MetadataField): String? = when (field) {
        MetadataField.TITLE -> title
        MetadataField.ARTIST -> artist
        MetadataField.ALBUM -> album
        MetadataField.ALBUM_ARTIST -> albumArtist
        MetadataField.GENRE -> genre
        MetadataField.YEAR -> year
        MetadataField.TRACK_NUMBER -> trackNumber
        MetadataField.DISC_NUMBER -> discNumber
        MetadataField.COMPOSER -> composer
        MetadataField.COMMENT -> comment
        MetadataField.LYRICS -> lyrics
    }
}

/** Fields to change. A null value means "remove this field from the file". Absent keys are never touched. */
typealias MetadataChanges = Map<MetadataField, String?>

/** What can really be written for a given container. */
data class WriteCapability(
    val container: AudioContainer,
    val writableFields: Set<MetadataField>,
    val reason: String?,
) {
    val canWrite: Boolean get() = writableFields.isNotEmpty()

    companion object {
        fun forContainer(container: AudioContainer): WriteCapability =
            if (container.tagWritable) {
                WriteCapability(container, MetadataField.entries.toSet(), null)
            } else {
                WriteCapability(container, emptySet(), "Metadata editing is not supported safely for ${container.label}.")
            }
    }
}

enum class FailureKind {
    INVALID_INPUT,
    WRITE_PERMISSION_DENIED,
    USER_CANCELLED,
    UNSUPPORTED_FORMAT,
    UNSUPPORTED_FIELD,
    FILE_UNAVAILABLE,
    WRITER_ERROR,
    VERIFICATION_FAILED,
    INSUFFICIENT_STORAGE,
    CORRUPT_FILE,
    UNKNOWN,
}

class MetadataException(
    val kind: FailureKind,
    val detail: String,
    cause: Throwable? = null,
) : Exception("$kind: $detail", cause)
