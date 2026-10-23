package com.localplayer.music.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcLibMatcherTest {
    private fun r(id: Long, title: String, artist: String, dur: Double, plain: String? = "la", synced: String? = null, inst: Boolean = false) =
        LrcLibResult(id, title, artist, "Album", dur, inst, plain, synced)

    @Test fun sameSongSyncedFirstDifferentVersionLastUnrelatedDropped() {
        val results = listOf(
            r(1, "Believer", "Imagine Dragons", 204.0, synced = null),
            r(2, "Believer", "Imagine Dragons", 204.4, synced = "[00:01.00]hi"),
            r(3, "Believer (Live)", "Imagine Dragons", 260.0, synced = "[00:01.00]x"),
            r(4, "Believer", "Someone Else", 204.0),
            r(5, "Other Song", "Imagine Dragons", 204.0),
            r(6, "Believer", "Imagine Dragons", 204.0, plain = null, synced = null),
            r(7, "Believer", "Imagine Dragons", 204.0, inst = true),
        )
        val ranked = LrcLibMatcher.rank(results, "Believer", "Imagine Dragons", 204_000)
        assertEquals(listOf(2L, 1L, 3L), ranked.map { it.result.id })
        assertEquals(LyricsMatch.GOOD, ranked[0].match)
        assertEquals(LyricsMatch.DIFFERENT_VERSION, ranked[2].match)
    }

    @Test fun unknownLengthIsPossibleNotGood() {
        val ranked = LrcLibMatcher.rank(listOf(r(1, "Song", "Art", 200.0)), "Song", "Art", 0)
        assertEquals(LyricsMatch.POSSIBLE, ranked.single().match)
    }

    @Test fun missingArtistOnOurSideStillMatchesByTitle() {
        val ranked = LrcLibMatcher.rank(listOf(r(1, "Geito", "Anyone", 180.0)), "Geito", null, 180_000)
        assertEquals(1, ranked.size)
    }

    @Test fun featuringTextAndPunctuationDoNotBreakMatching() {
        val ranked = LrcLibMatcher.rank(listOf(r(1, "Hello - Remastered", "Adele", 295.0)), "Hello (feat. X)", "ADELE", 295_000)
        assertEquals(1, ranked.size)
    }

    @Test fun noResultsAndInstrumentalOnly() {
        assertTrue(LrcLibMatcher.rank(emptyList(), "A", "B", 1000).isEmpty())
        assertTrue(LrcLibMatcher.onlyInstrumental(listOf(r(1, "A", "B", 10.0, inst = true))))
        assertTrue(!LrcLibMatcher.onlyInstrumental(emptyList()))
    }

    @Test fun greekTitlesMatch() {
        val ranked = LrcLibMatcher.rank(listOf(r(1, "Γειτονιά", "Ονείρατα", 200.0)), "γειτονιά", "Ονείρατα", 201_000)
        assertEquals(LyricsMatch.GOOD, ranked.single().match)
    }

    @Test fun greekAccentsAndFinalSigmaDoNotBreakMatching() {
        val ranked = LrcLibMatcher.rank(listOf(r(1, "ΑΛΛΟΥ", "Τοκελ", 200.0)), "Αλλού", "Τόκελ", 200_000)
        assertEquals(LyricsMatch.GOOD, ranked.single().match)
        assertEquals(LrcLibMatcher.normalize("Νύχτας"), LrcLibMatcher.normalize("ΝΥΧΤΑΣ"))
    }

    @Test fun bracketOnlyTitleStillMatches() {
        val ranked = LrcLibMatcher.rank(listOf(r(1, "(Intro)", "Art", 60.0)), "(Intro)", "Art", 60_000)
        assertEquals(1, ranked.size)
    }

    @Test fun cleanTitleDropsBrackets() {
        assertEquals("Hello", LrcLibMatcher.cleanTitle("Hello (feat. X) [Remastered]"))
        assertEquals("(Intro)", LrcLibMatcher.cleanTitle("(Intro)"))
    }
}
