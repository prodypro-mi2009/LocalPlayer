package com.localplayer.music.data.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeMatcherTest {
    private val track = SpotifyTrack("Creep", "Radiohead", 238_000L)

    private fun hit(title: String, channel: String = "Radiohead", seconds: Long = 238) = YoutubeHit("id$title", title, channel, seconds * 1000)

    @Test fun sameSongIsAccepted() {
        assertTrue(YoutubeMatcher.isSame(track, hit("Radiohead - Creep")))
        assertTrue(YoutubeMatcher.isSame(track, hit("Creep", channel = "Radiohead - Topic")))
    }

    @Test fun otherArtistIsRejected() {
        assertFalse(YoutubeMatcher.isSame(track, hit("Creep (cover)", channel = "Some Guy")))
        assertFalse(YoutubeMatcher.isSame(track, hit("Creep", channel = "Stone Temple Pilots")))
    }

    @Test fun versionMustAgreeBothWays() {
        assertFalse(YoutubeMatcher.isSame(track, hit("Radiohead - Creep (Live at Glastonbury)")))
        assertFalse(YoutubeMatcher.isSame(track, hit("Radiohead - Creep (Acoustic)")))
        val live = SpotifyTrack("Creep - Live", "Radiohead", 238_000L)
        assertTrue(YoutubeMatcher.isSame(live, hit("Radiohead - Creep (Live)")))
        assertFalse(YoutubeMatcher.isSame(live, hit("Radiohead - Creep")))
    }

    @Test fun remasteredAndOfficialWordsAreNotVersions() {
        val remastered = SpotifyTrack("Creep - Remastered 2009", "Radiohead", 238_000L)
        assertTrue(YoutubeMatcher.isSame(remastered, hit("Radiohead - Creep (Official Video)")))
        assertEquals(emptySet<String>(), YoutubeMatcher.versions("Creep - Remastered 2009"))
    }

    @Test fun lengthMustMatchWhenKnown() {
        assertFalse(YoutubeMatcher.isSame(track, hit("Radiohead - Creep", seconds = 300)))
        assertTrue(YoutubeMatcher.isSame(track, hit("Radiohead - Creep", seconds = 241)))
        assertTrue(YoutubeMatcher.isSame(track, YoutubeHit("x", "Radiohead - Creep", "Radiohead", 0)))
    }

    @Test fun topicChannelWinsAndNothingIsGuessed() {
        val video = hit("Radiohead - Creep (Official Video)", seconds = 239)
        val topic = hit("Creep", channel = "Radiohead - Topic", seconds = 238)
        assertEquals(topic, YoutubeMatcher.best(track, listOf(video, topic)))
        assertNull(YoutubeMatcher.best(track, listOf(hit("Totally different song"))))
        assertNull(YoutubeMatcher.best(track, emptyList()))
    }

    @Test fun severalArtistsOneIsEnough() {
        val duet = SpotifyTrack("Song", "Alpha, Beta", null)
        assertTrue(YoutubeMatcher.isSame(duet, hit("Beta - Song", channel = "Label")))
    }

    @Test fun greekTitlesWork() {
        val t = SpotifyTrack("Αγάπη", "Νίκος", 200_000L)
        assertTrue(YoutubeMatcher.isSame(t, hit("Νίκος - Αγάπη", channel = "x", seconds = 200)))
    }
}
