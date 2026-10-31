package com.localplayer.music.data.download

import com.localplayer.music.data.metadata.MetadataField

/** Which tag fields a downloaded song gets. Pure (no Android), so it is unit-tested on the JVM. */
object DownloadTags {
    private val YEAR = Regex("""\d{4}""")
    private val NUMBER = Regex("""\d{1,4}""")

    /**
     * The tag fields to write for one song. Only fields that have a valid value are written (a blank or malformed
     * year / track number is skipped, never written as garbage). [albumArtistSame] copies the artist into the
     * "album artist" tag.
     */
    fun changes(
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: String,
        track: String,
        albumArtistSame: Boolean,
    ): Map<MetadataField, String?> = buildMap {
        put(MetadataField.TITLE, title)
        if (artist.isNotBlank()) {
            put(MetadataField.ARTIST, artist)
            if (albumArtistSame) put(MetadataField.ALBUM_ARTIST, artist)
        }
        if (album.isNotBlank()) put(MetadataField.ALBUM, album)
        if (genre.isNotBlank()) put(MetadataField.GENRE, genre)
        if (YEAR.matches(year)) put(MetadataField.YEAR, year)
        if (NUMBER.matches(track)) put(MetadataField.TRACK_NUMBER, track)
    }
}
