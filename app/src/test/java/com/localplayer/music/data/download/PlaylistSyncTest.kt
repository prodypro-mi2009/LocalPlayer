package com.localplayer.music.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSyncTest {
    private val keys = PlaylistSync.existingKeys(
        listOf("Don't Play Nice.mp3", "Therapy (2).mp3", "Νίκος Μακρόπουλος - Ti Kaname.flac", "cover.jpg", "notes.txt"),
    )

    @Test
    fun matchesIgnoringCaseAndPunctuation() {
        assertTrue(PlaylistSync.isPresent("dont play nice", keys))
        assertTrue(PlaylistSync.isPresent("Don't Play Nice", keys))
    }

    @Test
    fun aCopySuffixCountsAsTheSameSong() {
        assertTrue(PlaylistSync.isPresent("Therapy", keys))
    }

    @Test
    fun otherFilesAndOtherSongsDoNotMatch() {
        assertFalse(PlaylistSync.isPresent("cover", keys))
        assertFalse(PlaylistSync.isPresent("KAKO", keys))
        assertFalse(PlaylistSync.isPresent("", keys))
    }

    @Test
    fun libraryNeedsTheSameTitleAndOverlappingArtist() {
        val index = PlaylistSync.libraryIndex(
            listOf(
                PlaylistSync.LibrarySong("u1", "Don't Play Nice", "Unroyal Unroyal"),
                PlaylistSync.LibrarySong("u2", "Intro", "Saske"),
                PlaylistSync.LibrarySong("u3", "Palia", ""),
            ),
        )
        assertTrue(PlaylistSync.isInLibrary("Dont play nice", "Unroyal", index))
        assertTrue(PlaylistSync.isInLibrary("Intro", "Saske & Someone", index))
        assertFalse(PlaylistSync.isInLibrary("Intro", "Marzi", index))
        assertTrue(PlaylistSync.isInLibrary("Palia", "Anyone", index))
        assertTrue(PlaylistSync.isInLibrary("Intro", "", index))
        assertFalse(PlaylistSync.isInLibrary("Unknown song", "Saske", index))
        assertEquals("u2", PlaylistSync.libraryMatch("Intro", "Saske", index))
        assertEquals(null, PlaylistSync.libraryMatch("Intro", "Marzi", index))
    }

    @Test
    fun greekTitlesWork() {
        assertTrue(PlaylistSync.isPresent("Νίκος Μακρόπουλος - Ti Kaname", keys))
    }

    @Test
    fun cleanNameMatchesTheFileNameTheAppWrites() {
        assertEquals("A B C", PlaylistSync.cleanName("A/B:C"))
        assertTrue(PlaylistSync.isPresent("Why? Not*", PlaylistSync.existingKeys(listOf("Why Not.mp3"))))
    }
}
