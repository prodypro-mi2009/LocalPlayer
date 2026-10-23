package com.localplayer.music.data.download

import org.junit.Assert.assertEquals
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
}
