package com.localplayer.music.data.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortingTest {
    private fun album(title: String?, artist: String? = null, year: Int? = null, n: Int = 1) =
        AlbumItem(title.orEmpty(), title, artist, year, n, 0L, null)

    @Test fun latinAndGreekSections() {
        assertEquals('B', AlphabetIndex.sectionOf("beyoncé"))
        assertEquals('E', AlphabetIndex.sectionOf("Élan"))
        assertEquals('Α', AlphabetIndex.sectionOf("άλμπουμ"))
        assertEquals('Σ', AlphabetIndex.sectionOf("ςυ"))
        assertEquals('Ω', AlphabetIndex.sectionOf("Ώρα"))
        assertEquals('#', AlphabetIndex.sectionOf("99 Problems"))
        assertEquals('#', AlphabetIndex.sectionOf(null))
        assertEquals('#', AlphabetIndex.sectionOf(""))
    }

    @Test fun firstIndexIsInDisplayOrderAndUsesFirstOccurrence() {
        val map = AlphabetIndex.firstIndexBySection(listOf("Alpha", "Apple", "Βήτα", "Zed", "1st"))
        assertEquals(listOf('A', 'Z', 'Β', '#'), map.keys.toList())
        assertEquals(0, map['A'])
        assertEquals(3, map['Z'])
    }

    @Test fun albumsByTitleIgnoreCaseAndAccentsUnknownLast() {
        val sorted = LibrarySorting.albums(
            listOf(album(null), album("Zeta"), album("άλφα"), album("Beta")),
            AlbumSort.TITLE,
        )
        // Latin letters sort before Greek ones; accents and case are ignored.
        assertEquals(listOf("Beta", "Zeta", "άλφα", null), sorted.map { it.title })
    }

    @Test fun albumsByYearNewestFirstNoYearLast() {
        val sorted = LibrarySorting.albums(
            listOf(album("A", year = 1999), album("B"), album("C", year = 2010)),
            AlbumSort.YEAR,
        )
        assertEquals(listOf("C", "A", "B"), sorted.map { it.title })
    }

    @Test fun byCountDescendingThenName() {
        val sorted = LibrarySorting.genres(
            listOf(GenreItem("a", "Rock", 3), GenreItem("b", "Jazz", 3), GenreItem("c", "Pop", 9)),
            NameCountSort.SONG_COUNT,
        )
        assertEquals(listOf("Pop", "Jazz", "Rock"), sorted.map { it.name })
    }
}
