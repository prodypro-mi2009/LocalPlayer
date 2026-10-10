package com.localplayer.music.ui

import com.localplayer.music.ui.components.LibraryTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LibraryTabTest {
    @Test fun tabsAreExactlyTheRequiredOnesInOrder() {
        assertEquals(
            listOf("HOME", "SONGS", "ALBUMS", "ARTISTS", "PLAYLISTS", "GENRES"),
            LibraryTab.entries.map { it.name },
        )
    }

    @Test fun yearsIsNotAVisibleTab() {
        assertFalse(LibraryTab.entries.any { it.name.contains("YEAR") })
    }

    @Test fun badOrdinalFallsBackToHome() {
        assertEquals(LibraryTab.HOME, LibraryTab.fromOrdinal(99))
        assertEquals(LibraryTab.HOME, LibraryTab.fromOrdinal(-1))
        assertEquals(LibraryTab.GENRES, LibraryTab.fromOrdinal(5))
    }
}
