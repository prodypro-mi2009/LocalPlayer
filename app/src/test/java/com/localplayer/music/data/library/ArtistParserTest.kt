package com.localplayer.music.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistParserTest {
    private fun primary(
        artist: String?,
        albumArtist: String? = null,
        structured: List<String> = emptyList(),
        groups: Set<String> = emptySet(),
    ) = ArtistParser.primaryArtist(artist, albumArtist, structured, groups)

    @Test fun singleArtist() = assertEquals("Artist A", primary("Artist A"))
    @Test fun feat() = assertEquals("Artist A", primary("Artist A feat. Artist B"))
    @Test fun featNoDot() = assertEquals("Artist A", primary("Artist A feat Artist B"))
    @Test fun ft() = assertEquals("Artist A", primary("Artist A ft. Artist B"))
    @Test fun featuring() = assertEquals("Artist A", primary("Artist A featuring Artist B"))
    @Test fun featUpperCase() = assertEquals("Artist A", primary("Artist A FEAT. Artist B"))
    @Test fun featInParentheses() = assertEquals("Artist A", primary("Artist A (feat. Artist B)"))
    @Test fun featInBrackets() = assertEquals("Artist A", primary("Artist A [ft. Artist B]"))
    @Test fun ampersand() = assertEquals("Artist A", primary("Artist A & Artist B"))
    @Test fun lowerX() = assertEquals("Artist A", primary("Artist A x Artist B"))
    @Test fun upperX() = assertEquals("Artist A", primary("Artist A X Artist B"))
    @Test fun comma() = assertEquals("Artist A", primary("Artist A, Artist B"))
    @Test fun semicolon() = assertEquals("Artist A", primary("Artist A; Artist B"))
    @Test fun structuredWins() = assertEquals("First", primary("Second & Third", structured = listOf("First", "Second")))
    @Test fun structuredSingleValueFallsBackToString() = assertEquals("Artist A", primary("Artist A feat. B", structured = listOf("Artist A feat. B")))
    @Test fun emptyArtist() = assertNull(primary(""))
    @Test fun nullArtist() = assertNull(primary(null))
    @Test fun blankArtist() = assertNull(primary("   "))

    // Names that must NOT be split.
    @Test fun earthWindFire() = assertEquals("Earth, Wind & Fire", primary("Earth, Wind & Fire"))
    @Test fun simonGarfunkel() = assertEquals("Simon & Garfunkel", primary("Simon & Garfunkel"))
    @Test fun tylerTheCreator() = assertEquals("Tyler, The Creator", primary("Tyler, The Creator"))
    @Test fun commaJunior() = assertEquals("Sammy Davis, Jr.", primary("Sammy Davis, Jr."))
    @Test fun malcolmX() = assertEquals("Malcolm X", primary("Malcolm X"))
    @Test fun softCellIsNotFt() = assertEquals("Soft Cell", primary("Soft Cell"))
    @Test fun acDc() = assertEquals("AC/DC", primary("AC/DC"))
    @Test fun unknownGroupFromAlbumArtist() = assertEquals("Brand & Name", primary("Brand & Name", albumArtist = "brand & name"))
    @Test fun knownGroupSet() = assertEquals("Some & Band", primary("Some & Band", groups = setOf(TextNormalizer.groupKey("Some & Band")!!)))
    @Test fun featStillSplitsEvenWithAlbumArtistEqual() = assertEquals("Rack", primary("Rack feat. X", albumArtist = "Rack feat. X"))
    @Test fun greekNames() = assertEquals("Μάλαμας", primary("Μάλαμας feat. Παπακωνσταντίνου"))
    @Test fun greekSingle() = assertEquals("Ελευθερία Αρβανιτάκη", primary("Ελευθερία Αρβανιτάκη"))
    @Test fun creditIsReturnedUnmodified() = assertEquals("Rack's  Band", primary("Rack's  Band"))
}
