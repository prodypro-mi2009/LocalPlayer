package com.localplayer.music.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackIndexingTest {
    private fun derive(artist: String?, albumArtist: String? = null, album: String? = "Album", override: String? = null) =
        TrackIndexing.derive("Song", artist, albumArtist, album, "Pop", emptySet(), override)

    @Test fun collaborationIsFiledUnderTheFirstArtist() {
        val d = derive("Rack feat. Artist B")
        assertEquals("Rack", d.primaryArtist)
        assertEquals(TextNormalizer.groupKey("Rack"), d.primaryArtistKey)
    }

    @Test fun overrideChangesFilingButNotTheAutomaticResult() {
        val d = derive("Rack feat. Artist B", override = "Artist B")
        assertEquals("Artist B", d.primaryArtist)
        assertEquals("Rack", d.autoPrimaryArtist)
    }

    @Test fun overrideDoesNotSplitTheAlbum() {
        assertEquals(derive("Rack feat. B").albumGroupKey, derive("Rack feat. B", override = "B").albumGroupKey)
    }

    @Test fun sameAlbumTitleByDifferentArtistsIsADifferentAlbum() =
        assertNotEquals(derive("Artist A").albumGroupKey, derive("Artist B").albumGroupKey)

    @Test fun albumArtistKeepsCompilationsTogether() =
        assertEquals(derive("Artist A", "Various").albumGroupKey, derive("Artist B", "various").albumGroupKey)

    @Test fun emptyArtistHasNoPrimaryArtist() {
        val d = derive("")
        assertNull(d.primaryArtist)
        assertNull(d.primaryArtistKey)
    }

    @Test fun genreKeysIgnoreCaseAndWhitespace() {
        val a = TrackIndexing.derive("s", "a", null, null, " POP ", emptySet(), null).genreKey
        val b = TrackIndexing.derive("s", "a", null, null, "pop", emptySet(), null).genreKey
        assertEquals(a, b)
    }

    @Test fun yearAndNumberParsing() {
        assertEquals(2025, TrackIndexing.parseYear("2025-03-01"))
        assertNull(TrackIndexing.parseYear("n/a"))
        assertEquals(3, TrackIndexing.parseLeadingInt("3/12"))
        assertNull(TrackIndexing.parseLeadingInt("x"))
    }
}
