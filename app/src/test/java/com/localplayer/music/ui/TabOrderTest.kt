package com.localplayer.music.ui

import com.localplayer.music.ui.components.LibraryTab
import com.localplayer.music.ui.components.TabOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabOrderTest {
    private val default = LibraryTab.entries.toList()

    @Test fun nullBlankAndGarbageGiveDefault() {
        assertEquals(default, TabOrder.parse(null))
        assertEquals(default, TabOrder.parse(""))
        assertEquals(default, TabOrder.parse("nonsense,YEARS"))
    }

    @Test fun serializeRoundTrips() {
        val order = listOf(LibraryTab.SONGS, LibraryTab.HOME, LibraryTab.GENRES, LibraryTab.ARTISTS, LibraryTab.PLAYLISTS, LibraryTab.ALBUMS)
        assertEquals(order, TabOrder.parse(TabOrder.serialize(order)))
    }

    @Test fun missingTabsAreAppendedAndDuplicatesAndUnknownDropped() {
        val parsed = TabOrder.parse("GENRES,YEARS,GENRES,SONGS")
        assertEquals(listOf(LibraryTab.GENRES, LibraryTab.SONGS), parsed.take(2))
        assertEquals(LibraryTab.entries.size, parsed.size)
        assertEquals(LibraryTab.entries.toSet(), parsed.toSet())
    }

    @Test fun songsIsAlwaysPresent() {
        assertTrue(LibraryTab.SONGS in TabOrder.parse("HOME"))
    }

    @Test fun moveUpAndDownAndBounds() {
        assertEquals(LibraryTab.SONGS, TabOrder.move(default, 1, -1)[0])
        assertEquals(LibraryTab.HOME, TabOrder.move(default, 0, +1)[1])
        assertEquals(default, TabOrder.move(default, 0, -1))
        assertEquals(default, TabOrder.move(default, default.lastIndex, +1))
    }
}
