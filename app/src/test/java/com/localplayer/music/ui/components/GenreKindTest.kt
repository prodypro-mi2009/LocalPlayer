package com.localplayer.music.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenreKindTest {
    @Test fun commonNamesFindTheirPicture() {
        assertEquals(GenreKind.Rock, GenreKind.of("Rock"))
        assertEquals(GenreKind.Rock, GenreKind.of("Hard Rock"))
        assertEquals(GenreKind.AlternativeRock, GenreKind.of("Alternative Rock"))
        assertEquals(GenreKind.AlternativeRock, GenreKind.of("Indie"))
        assertEquals(GenreKind.Pop, GenreKind.of("Pop"))
        assertEquals(GenreKind.Pop, GenreKind.of("K-Pop"))
        assertEquals(GenreKind.HipHop, GenreKind.of("Hip-Hop"))
        assertEquals(GenreKind.HipHop, GenreKind.of("Hip Hop/Rap"))
        assertEquals(GenreKind.Rap, GenreKind.of("Rap"))
        assertEquals(GenreKind.Jazz, GenreKind.of("Jazz"))
        assertEquals(GenreKind.Metal, GenreKind.of("Heavy Metal"))
        assertEquals(GenreKind.Classical, GenreKind.of("Classical"))
        assertEquals(GenreKind.FunkDisco, GenreKind.of("Funk & Disco"))
        assertEquals(GenreKind.Electronic, GenreKind.of("Electronic/EDM"))
    }

    @Test fun theMoreSpecificGenreWins() {
        assertEquals(GenreKind.Trap, GenreKind.of("Greek Trap"))
        assertEquals(GenreKind.Metal, GenreKind.of("Metal Rock"))
        assertEquals(GenreKind.Rock, GenreKind.of("Classic Rock"))
        assertEquals(GenreKind.Electronic, GenreKind.of("Trip-Hop"))
        assertEquals(GenreKind.Electronic, GenreKind.of("Synthpop"))
        assertEquals(GenreKind.Laika, GenreKind.of("Greek Folk"))
        assertEquals(GenreKind.Entechno, GenreKind.of("Folk"))
        assertEquals(GenreKind.RnbSoul, GenreKind.of("Rhythm and Blues"))
    }

    @Test fun rnbAndSoulInTheirUsualSpellings() {
        assertEquals(GenreKind.RnbSoul, GenreKind.of("R&B"))
        assertEquals(GenreKind.RnbSoul, GenreKind.of("R&B/Soul"))
        assertEquals(GenreKind.RnbSoul, GenreKind.of("RnB"))
        assertEquals(GenreKind.RnbSoul, GenreKind.of("Neo Soul"))
    }

    @Test fun greekNamesAndAccentsWork() {
        assertEquals(GenreKind.Laika, GenreKind.of("Λαϊκά"))
        assertEquals(GenreKind.Laika, GenreKind.of("Laiko"))
        assertEquals(GenreKind.Entechno, GenreKind.of("Έντεχνο"))
        assertEquals(GenreKind.Rap, GenreKind.of("Ραπ"))
        assertEquals(GenreKind.Classical, GenreKind.of("Κλασική"))
    }

    @Test fun unknownOrEmptyGivesNothing() {
        assertNull(GenreKind.of(null))
        assertNull(GenreKind.of(""))
        assertNull(GenreKind.of("Soundtrack"))
        assertNull(GenreKind.of("Podcast"))
    }
}
