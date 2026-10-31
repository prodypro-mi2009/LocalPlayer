package com.localplayer.music.data.download

import com.localplayer.music.data.metadata.MetadataField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class YtDlpEngineTest {
    @Test fun parsesIdTitleArtistLines() {
        val out = "abc123\tKlima\tSidarta - Topic\nxyz\tAgapi\tNA\n\nbad id\tx\ty\nabc123\tDuplicate\tz\n"
        val songs = YtDlpEngine.parseListing(out)
        assertEquals(listOf(FoundSong("abc123", "Klima", "Sidarta"), FoundSong("xyz", "Agapi", "")), songs)
    }

    @Test fun missingTitleFallsBackToId() {
        assertEquals("q1", YtDlpEngine.parseListing("q1\tNA\tNA").single().title)
    }

    @Test fun artistCleaning() {
        assertEquals("", YtDlpEngine.cleanArtist(null))
        assertEquals("", YtDlpEngine.cleanArtist("NA"))
        assertEquals("Sigma", YtDlpEngine.cleanArtist(" Sigma - Topic "))
    }

    @Test fun fileNamesAreSafe() {
        assertEquals("A B C", YtDlpEngine.safeFileName("A/B:C", "id"))
        assertEquals("id", YtDlpEngine.safeFileName("???", "id"))
        assertEquals("Song", YtDlpEngine.safeFileName("Song...", "id"))
    }

    @Test fun albumLinkGivesTrackNumberAndAlbumName() {
        val out = "id1\tDon't Be A Fool\tShawn Mendes - Topic\t3\tIlluminate\tOLAK5uy_abc\tNA"
        assertEquals(FoundSong("id1", "Don't Be A Fool", "Shawn Mendes", 3, "Illuminate"), YtDlpEngine.parseListing(out).single())
    }

    @Test fun ordinaryPlaylistNameIsNotAnAlbum() {
        val song = YtDlpEngine.parseListing("id2\tA\tB\t2\tMy road trip mix\tPLxyz\tNA").single()
        assertEquals(2, song.trackNumber)
        assertEquals("", song.album)
    }

    @Test fun theSongsOwnAlbumWinsOverThePlaylistTitle() {
        val song = YtDlpEngine.parseListing("id3\tA\tB\t1\tMy mix\tPLxyz\tIlluminate").single()
        assertEquals("Illuminate", song.album)
    }

    @Test fun singleVideoHasNoTrackNumberOrAlbum() {
        val song = YtDlpEngine.parseListing("id4\tA\tB\tNA\tNA\tNA\tNA").single()
        assertEquals(null, song.trackNumber)
        assertEquals("", song.album)
    }

    @Test fun infoJsonGivesAlbumAndReleaseYear() {
        val json = """{"id":"x","album":"Illuminate","release_year":2016,"formats":[{"abr":128,"x":[1,2]}],"track_number":null}"""
        assertEquals(InfoMeta(album = "Illuminate", year = "2016", trackNumber = null), YtDlpEngine.parseInfo(json))
    }

    @Test fun infoJsonFallsBackToTheYearOfTheReleaseDate() {
        assertEquals("2016", YtDlpEngine.parseInfo("""{"release_date":"20160923"}""").year)
        assertEquals(InfoMeta(), YtDlpEngine.parseInfo("""{"album":"NA","title":"x"}"""))
        assertEquals(7, YtDlpEngine.parseInfo("""{"track_number":7}""").trackNumber)
    }

    @Test fun brokenInfoMeansNothingKnown() {
        assertEquals(InfoMeta(), YtDlpEngine.readInfo(java.io.File("/nonexistent/none.info.json")))
        val bad = runCatching { YtDlpEngine.parseInfo("not json") }
        assertEquals(true, bad.isFailure) // readInfo wraps this in runCatching
    }

    @Test fun tagChangesWriteOnlyWhatIsValid() {
        val all = DownloadTags.changes("Song", "Shawn Mendes", "Illuminate", "Pop", "2016", "3", albumArtistSame = true)
        assertEquals("Shawn Mendes", all[MetadataField.ALBUM_ARTIST])
        assertEquals("Illuminate", all[MetadataField.ALBUM])
        assertEquals("Pop", all[MetadataField.GENRE])
        assertEquals("2016", all[MetadataField.YEAR])
        assertEquals("3", all[MetadataField.TRACK_NUMBER])

        val minimal = DownloadTags.changes("Song", "", "", "", "20", "x", albumArtistSame = true)
        assertEquals(setOf(MetadataField.TITLE), minimal.keys)

        val noAlbumArtist = DownloadTags.changes("Song", "A", "", "", "", "", albumArtistSame = false)
        assertFalse(noAlbumArtist.containsKey(MetadataField.ALBUM_ARTIST))
        assertEquals("A", noAlbumArtist[MetadataField.ARTIST])
    }

    @Test
    fun albumPlaylistPrefixIsStripped() {
        assertEquals("Lilith Tape", YtDlpEngine.albumFor(null, "Album - Lilith Tape", "OLAK5uyABC"))
        assertEquals("X", YtDlpEngine.albumFor("NA", "Single - X", "OLAK5uyABC"))
        assertEquals("Y", YtDlpEngine.albumFor("", "EP – Y", "OLAK5uyABC"))
        assertEquals("Album", YtDlpEngine.stripAlbumPrefix("Album"))
        assertEquals("Albums of the year", YtDlpEngine.stripAlbumPrefix("Albums of the year"))
    }
}
