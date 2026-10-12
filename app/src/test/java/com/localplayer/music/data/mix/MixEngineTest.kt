package com.localplayer.music.data.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MixEngineTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun song(i: Int, artist: String? = "artist${i % 10}", album: String? = "album$i", genre: String? = "g${i % 3}", added: Long = 0) =
        MixCandidate(i.toLong(), "uri$i", "Title $i", artist, album, genre, 200_000, added)

    private val library = (1..200).map { song(it) }

    @Test fun deterministicForASeedAndDifferentForAnother() {
        val s = MixSettings(strategy = MixStrategy.RANDOM)
        val a = MixEngine.generate(library, emptyMap(), s, now, 1)
        val b = MixEngine.generate(library, emptyMap(), s, now, 1)
        val c = MixEngine.generate(library, emptyMap(), s, now, 2)
        assertEquals(a.uris, b.uris)
        assertTrue(a.uris != c.uris)
        assertEquals(30, a.uris.size)
        assertEquals(a.uris.size, a.uris.toSet().size)
    }

    @Test fun mostPlayedPutsTheMostPlayedFirstAndIgnoresUnplayed() {
        val stats = mapOf(
            "uri5" to MixStats(50, 80, now - 5 * day), "uri7" to MixStats(20, 20, now - 3 * day),
            "uri9" to MixStats(2, 2, now - 9 * day),
        )
        val r = MixEngine.generate(library, stats, MixSettings(strategy = MixStrategy.MOST_PLAYED, variety = 0), now, 3)
        assertEquals(listOf("uri5", "uri7", "uri9"), r.uris.take(3))
        assertEquals(MixNotice.PARTIAL_HISTORY, r.notice)
        assertEquals(30, r.uris.size)
    }

    @Test fun mostPlayedWithoutAnyHistoryFallsBackAndSaysSo() {
        val r = MixEngine.generate(library, emptyMap(), MixSettings(strategy = MixStrategy.MOST_PLAYED), now, 3)
        assertEquals(MixNotice.NO_HISTORY, r.notice)
        assertEquals(30, r.uris.size)
    }

    @Test fun forgottenRanksLikedButNeglectedAboveNeverPlayed() {
        val stats = mapOf(
            "uri1" to MixStats(0, 40, now - 400 * day),   // loved once, not heard for over a year
            "uri2" to MixStats(0, 1, now - 2 * day),      // heard two days ago
        )
        val r = MixEngine.generate(library.take(60), stats, MixSettings(strategy = MixStrategy.FORGOTTEN, size = 10, variety = 0, avoidRecentDays = 0), now, 4)
        val i1 = r.uris.indexOf("uri1")
        assertTrue("uri1 should be near the top", i1 in 0..2)
        assertFalse("recently heard song must not be a forgotten pick", "uri2" in r.uris)
    }

    @Test fun recentlyHeardSongsAreAvoided() {
        val stats = (1..10).associate { "uri$it" to MixStats(5, 5, now - day / 2) }
        val r = MixEngine.generate(library, stats, MixSettings(strategy = MixStrategy.RANDOM, avoidRecentDays = 2), now, 5)
        assertTrue(r.uris.none { it in stats })
    }

    @Test fun avoidFilterNeverLeavesTheMixShort() {
        val tiny = (1..12).map { song(it) }
        val stats = tiny.associate { it.uri to MixStats(3, 3, now - day / 4) }
        val r = MixEngine.generate(tiny, stats, MixSettings(strategy = MixStrategy.RANDOM, size = 10, avoidRecentDays = 7), now, 6)
        assertEquals(10, r.uris.size)
    }

    @Test fun varietyCapsOneArtistButStillFillsTheMix() {
        val oneArtist = (1..40).map { song(it, artist = "same") } + (41..60).map { song(it) }
        val stats = oneArtist.associate { it.uri to MixStats(10, 10, now - 20 * day) }
        val high = MixEngine.generate(oneArtist, stats, MixSettings(strategy = MixStrategy.SMART, size = 20, variety = 100, avoidRecentDays = 0), now, 7)
        assertTrue(high.uris.count { it in (1..40).map { n -> "uri$n" } } <= 6)
        assertEquals(20, high.uris.size)
    }

    @Test fun sameArtistIsNotPlacedBackToBackWhenAvoidable() {
        val r = MixEngine.generate(library, emptyMap(), MixSettings(strategy = MixStrategy.RANDOM, variety = 0), now, 8)
        val artists = r.uris.map { u -> library.first { it.uri == u }.artistKey }
        assertTrue(artists.zipWithNext().count { it.first == it.second } == 0)
    }

    @Test fun missingMetadataNeverBlocksASong() {
        val bare = (1..40).map { song(it, artist = null, album = null, genre = null) }
        val stats = bare.take(20).associate { it.uri to MixStats(5, 5, now - 30 * day) }
        for (strategy in MixStrategy.entries) {
            val r = MixEngine.generate(bare, stats, MixSettings(strategy = strategy, size = 25, variety = 100), now, 9)
            assertEquals(strategy.name, 25, r.uris.size)
        }
    }

    @Test fun smartMixUsesHistoryAndGenresVary() {
        val stats = library.take(120).associate { it.uri to MixStats((it.id % 7).toInt() + 1, 10, now - (it.id % 30 + 2) * day) }
        val r = MixEngine.generate(library, stats, MixSettings(strategy = MixStrategy.SMART, variety = 100, avoidRecentDays = 0), now, 10)
        assertEquals(MixNotice.NONE, r.notice)
        val genres = r.uris.map { u -> library.first { it.uri == u }.genreKey }.toSet()
        assertTrue(genres.size >= 3)
    }

    @Test fun smartWithTooLittleHistoryFallsBack() {
        val r = MixEngine.generate(library, mapOf("uri1" to MixStats(1, 1, now - 5 * day)), MixSettings(), now, 11)
        assertEquals(MixNotice.NO_HISTORY, r.notice)
        assertEquals(30, r.uris.size)
    }

    @Test fun duplicatesOfTheSameSongAppearOnce() {
        val dupes = listOf(
            MixCandidate(1, "u1", "Believer", "imagine dragons", "a", null, 204_000, 0),
            MixCandidate(2, "u2", "believer ", "Imagine Dragons", "b", null, 204_500, 0),
        ) + (3..30).map { song(it) }
        val r = MixEngine.generate(dupes, emptyMap(), MixSettings(strategy = MixStrategy.RANDOM, size = 100), now, 12)
        assertEquals(1, r.uris.count { it == "u1" || it == "u2" })
        assertEquals(MixNotice.FEWER_THAN_REQUESTED, r.notice)
    }

    @Test fun emptyLibraryIsReportedNotCrashed() {
        val r = MixEngine.generate(emptyList(), emptyMap(), MixSettings(), now, 1)
        assertEquals(MixNotice.EMPTY_LIBRARY, r.notice)
        assertTrue(r.uris.isEmpty())
    }

    @Test fun settingsSurviveSerializationAndGarbage() {
        val s = MixSettings(MixStrategy.FORGOTTEN, 55, 90, 20, 80, 3)
        assertEquals(s, MixSettings.parse(s.serialize()))
        assertEquals(MixSettings(), MixSettings.parse("junk"))
        assertEquals(MixSettings(), MixSettings.parse(null))
        assertEquals(100, MixSettings.parse("smart;9999;30;40;60;1").size)
        assertEquals(10, MixSettings.parse("smart;1;30;40;60;1").size)
    }

    @Test fun snapshotRoundTripsAndRejectsGarbage() {
        val snap = MixRefresh.Snapshot("smart;30;30;40;60;1", now, 42, listOf("content://a/1", "content://a/2"), MixNotice.PARTIAL_HISTORY)
        val back = MixRefresh.Snapshot.parse(snap.serialize())!!
        assertEquals(snap.uris, back.uris); assertEquals(42, back.playsAtGeneration)
        assertEquals(MixNotice.PARTIAL_HISTORY, back.notice); assertEquals(snap.settingsKey, back.settingsKey)
        assertEquals(null, MixRefresh.Snapshot.parse("garbage"))
        assertEquals(null, MixRefresh.Snapshot.parse(null))
        assertEquals(emptyList<String>(), MixRefresh.Snapshot.parse("k|1|2|NONE")!!.uris)
    }

    @Test fun refreshRulesAreCheapAndExplicit() {
        val s = MixSettings()
        val uris = (1..30).map { "u$it" }
        val snap = MixRefresh.Snapshot(s.serialize(), now, 100, uris)
        val all = uris.toSet()
        assertFalse(MixRefresh.needsRefresh(snap, s, now + 1000, 105, all, false))
        assertTrue(MixRefresh.needsRefresh(snap, s, now + 1000, 110, all, false))
        assertTrue(MixRefresh.needsRefresh(snap, s.copy(size = 40), now + 1000, 100, all, false))
        assertTrue(MixRefresh.needsRefresh(snap, s, now + 25 * 3_600_000L, 100, all, false))
        assertTrue(MixRefresh.needsRefresh(snap, s, now + 1000, 100, all.drop(12).toSet(), false))
        assertFalse(MixRefresh.needsRefresh(snap, s, now + 1000, 100, all.drop(5).toSet(), false))
        assertTrue(MixRefresh.needsRefresh(snap, s, now + 1000, 100, all, true))
        assertTrue(MixRefresh.needsRefresh(null, s, now, 0, all, false))
    }
}
