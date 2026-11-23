package com.localplayer.music.data.download

import com.localplayer.music.data.library.ArtistParser
import com.localplayer.music.data.metadata.MetadataField

/** Which tag fields a downloaded song gets. Pure (no Android), so it is unit-tested on the JVM. */
object DownloadTags {
    private val YEAR = Regex("""\d{4}""")
    private val NUMBER = Regex("""\d{1,4}""")

    /**
     * The tag fields to write for one song. Only fields that have a valid value are written (a blank or malformed
     * year / track number is skipped, never written as garbage). A non-blank [albumArtist] is written as the
     * "album artist" tag (see [albumArtistFor]).
     */
    fun changes(
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: String,
        track: String,
        albumArtist: String,
    ): Map<MetadataField, String?> = buildMap {
        put(MetadataField.TITLE, title)
        if (artist.isNotBlank()) {
            put(MetadataField.ARTIST, artist)
        }
        if (albumArtist.isNotBlank()) put(MetadataField.ALBUM_ARTIST, albumArtist.trim())
        if (album.isNotBlank()) put(MetadataField.ALBUM, album)
        if (genre.isNotBlank()) put(MetadataField.GENRE, genre)
        if (YEAR.matches(year)) put(MetadataField.YEAR, year)
        if (NUMBER.matches(track)) put(MetadataField.TRACK_NUMBER, track)
    }

    /**
     * The album artist to write. What the user typed for the song wins. Otherwise, with [same] on, it is the artist; when
     * the credit names several artists ("A feat. B", "A; B", "A & B") only the first (primary) one. Blank = not written.
     */
    fun albumArtistFor(artist: String, custom: String, same: Boolean): String {
        if (custom.isNotBlank()) return custom.trim()
        if (!same || artist.isBlank()) return ""
        return ArtistParser.primaryArtist(artist.trim())?.trim().orEmpty().ifEmpty { artist.trim() }
    }
}
