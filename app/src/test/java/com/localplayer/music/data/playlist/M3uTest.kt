package com.localplayer.music.data.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uTest {
    private fun cur(uri: String, name: String, title: String, artist: String, sec: Int) =
        M3uCandidate(uri, name, title, artist, sec * 1000L)

    @Test fun parsesExtinfAndPaths() {
        val text = "﻿#EXTM3U\r\n#EXTINF:215,Daft Punk - One More Time\r\nMusic/One More Time.mp3\r\n\r\n#EXTINF:-1,Solo\r\nC:\\Music\\b.flac\r\nplain.m4a\r\n"
        val e = M3u.parse(text)
        assertEquals(3, e.size)
        assertEquals("Daft Punk", e[0].artist); assertEquals("One More Time", e[0].title); assertEquals(215, e[0].durationSec)
        assertEquals("Solo", e[1].title); assertNull(e[1].artist); assertEquals(0, e[1].durationSec)
        assertNull(e[2].title)
        assertEquals("One More Time.mp3", M3u.fileName(e[0].path)); assertEquals("b.flac", M3u.fileName(e[1].path))
    }

    @Test fun fileUriIsDecoded() {
        val e = M3u.parse("file:///sdcard/Music/a%20b.mp3\n").single()
        assertEquals("a b.mp3", M3u.fileName(e.path))
    }

    @Test fun roundTrip() {
        val out = M3u.write(listOf(M3uEntry("a.mp3", "T", "A", 100), M3uEntry("b.mp3", "U", null, 0)))
        val back = M3u.parse(out)
        assertEquals(2, back.size)
        assertEquals("A", back[0].artist); assertEquals(100, back[0].durationSec); assertEquals("U", back[1].title)
    }

    @Test fun matching() {
        val m = M3uMatcher(listOf(
            cur("u1", "One More Time.mp3", "One More Time", "Daft Punk", 320),
            cur("u2", "x.mp3", "Same", "A", 100), cur("u3", "y.mp3", "Same", "A", 200),
            cur("u4", "Song.flac", "Song", "B", 180),
        ))
        assertEquals("u1", m.match(M3uEntry("/old/One More Time.mp3", null, null, 0)))
        assertEquals("u4", m.match(M3uEntry("Song.mp3", null, null, 0)))            // other extension, same name
        assertEquals("u3", m.match(M3uEntry("zzz.mp3", "Same", "A", 200)))           // two songs share the title: duration decides
        assertNull(m.match(M3uEntry("zzz.mp3", "Same", "A", 0)))                     // ambiguous: not guessed
        assertNull(m.match(M3uEntry("nothing.mp3", "Nope", "Q", 10)))
    }
}
