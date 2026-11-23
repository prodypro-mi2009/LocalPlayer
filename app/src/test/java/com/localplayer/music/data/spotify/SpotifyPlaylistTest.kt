package com.localplayer.music.data.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyPlaylistTest {
    private val page = """
        <html><head><title>Mix | Spotify</title></head><body>
        <script id="__NEXT_DATA__" type="application/json">
        {"props":{"pageProps":{"state":{"data":{"entity":{"type":"playlist","name":"My Mix","title":"My Mix",
        "trackList":[
          {"uri":"spotify:track:aaa","title":"Erotas","subtitle":"SNIK","duration":201000},
          {"uri":"spotify:track:bbb","title":"Αλλού","subtitle":"Μαζωνάκης, Άλλος","duration":180000},
          {"uri":"spotify:track:aaa","title":"Erotas","subtitle":"SNIK","duration":201000}
        ]}}}}}}
        </script></body></html>
    """.trimIndent()

    @Test fun readsNameAndSongsOnce() {
        val playlist = SpotifyPlaylistReader.parseEmbedHtml(page)!!
        assertEquals("My Mix", playlist.name)
        assertEquals(2, playlist.tracks.size)
        assertEquals(SpotifyTrack("Erotas", "SNIK", 201000L), playlist.tracks[0])
        assertEquals("Μαζωνάκης, Άλλος", playlist.tracks[1].artist)
    }

    @Test fun pagesWithoutSongsAreUnreadable() {
        assertNull(SpotifyPlaylistReader.parseEmbedHtml("<html>nothing</html>"))
        assertNull(SpotifyPlaylistReader.parseEmbedHtml("""<script id="__NEXT_DATA__" type="application/json">{"a":1}</script>"""))
        assertNull(SpotifyPlaylistReader.parseEmbedHtml("""<script id="__NEXT_DATA__" type="application/json">not json</script>"""))
    }

    @Test fun findsThePlaylistIdInLinks() {
        val id = "37i9dQZF1DXcBWIGoYBM5M"
        assertEquals(id, SpotifyPlaylistReader.playlistId("https://open.spotify.com/playlist/$id?si=abc123"))
        assertEquals(id, SpotifyPlaylistReader.playlistId("https://open.spotify.com/intl-gr/playlist/$id"))
        assertEquals(id, SpotifyPlaylistReader.playlistId("spotify:playlist:$id"))
        assertEquals(id, SpotifyPlaylistReader.playlistId("  $id "))
        assertNull(SpotifyPlaylistReader.playlistId("https://example.com/hello"))
        assertNull(SpotifyPlaylistReader.playlistId("https://open.spotify.com/track/$id"))
    }

    @Test fun matchesByTitleAndSharedArtist() {
        val library = listOf(
            LibraryEntry("u1", "Erotas (Remastered)", listOf("SNIK"), 200_000),
            LibraryEntry("u2", "Αλλου", listOf("Μαζωνάκης"), 180_500),
            LibraryEntry("u3", "Erotas", listOf("Someone Else"), 200_000),
        )
        val tracks = listOf(
            SpotifyTrack("Erotas", "SNIK", 201_000),
            SpotifyTrack("Αλλού", "Μαζωνάκης, Άλλος", 180_000),
            SpotifyTrack("Unknown Song", "Nobody", null),
        )
        val result = SpotifyMatcher.match(tracks, library)
        assertEquals("u1", result[0].libraryUri)
        assertEquals("u2", result[1].libraryUri)
        assertNull(result[2].libraryUri)
    }

    @Test fun sameTitleByAnotherArtistIsNotAMatch() {
        val library = listOf(LibraryEntry("u3", "Erotas", listOf("Someone Else"), 200_000))
        assertNull(SpotifyMatcher.match(listOf(SpotifyTrack("Erotas", "SNIK", null)), library)[0].libraryUri)
    }
}
