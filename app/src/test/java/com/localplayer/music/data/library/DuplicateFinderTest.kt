package com.localplayer.music.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateFinderTest {
    private fun t(id: Long, title: String, artist: String?, ms: Long, size: Long) =
        DupTrack(id, "u$id", title, artist, null, ms, size, "n$id", id)

    @Test fun groupsSameRecording() {
        val g = DuplicateFinder.find(listOf(
            t(1, "Song", "A", 200_000, 5_000), t(2, " song ", "a", 201_500, 9_000),
            t(3, "Song", "A", 260_000, 5_000),            // other version: not a duplicate
            t(4, "Other", "A", 200_000, 5_000),
        ))
        assertEquals(1, g.size)
        assertEquals(setOf(1L, 2L), g[0].tracks.map { it.id }.toSet())
        assertEquals(2L, g[0].keep.id)                    // biggest file kept
        assertFalse(g[0].sameFile)
    }

    @Test fun sameFileWhenSizesEqual() {
        val g = DuplicateFinder.find(listOf(t(1, "S", "A", 100_000, 777), t(2, "S", "A", 100_000, 777)))
        assertTrue(g.single().sameFile)
        assertEquals(1L, g.single().keep.id)
    }

    @Test fun differentArtistsAreNot() {
        assertTrue(DuplicateFinder.find(listOf(t(1, "S", "A", 100_000, 1), t(2, "S", "B", 100_000, 1))).isEmpty())
    }
}
