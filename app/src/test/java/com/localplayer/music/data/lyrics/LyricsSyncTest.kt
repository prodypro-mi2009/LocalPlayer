package com.localplayer.music.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsSyncTest {
    private val lines = listOf(LyricLine(1_000, "a"), LyricLine(5_000, "b"), LyricLine(9_000, "c"))

    @Test fun beforeTheFirstLineNothingIsActive() = assertEquals(-1, LyricsSync.activeIndex(lines, 0))
    @Test fun exactTimestampActivatesTheLine() {
        assertEquals(0, LyricsSync.activeIndex(lines, 1_000)); assertEquals(1, LyricsSync.activeIndex(lines, 5_000))
    }
    @Test fun justBeforeATimestampKeepsThePreviousLine() = assertEquals(0, LyricsSync.activeIndex(lines, 4_999))
    @Test fun lastLineStaysActiveUntilTheEnd() = assertEquals(2, LyricsSync.activeIndex(lines, 600_000))
    @Test fun seekForwardAndBackwardRecalculateImmediately() {
        assertEquals(2, LyricsSync.activeIndex(lines, 9_500)); assertEquals(0, LyricsSync.activeIndex(lines, 1_200))
    }
    @Test fun emptyLyrics() = assertEquals(-1, LyricsSync.activeIndex(emptyList(), 1_000))
    @Test fun timeUntilTheNextLine() {
        assertEquals(1_000L, LyricsSync.msUntilNextLine(lines, 0)); assertEquals(3_500L, LyricsSync.msUntilNextLine(lines, 1_500))
        assertNull(LyricsSync.msUntilNextLine(lines, 9_500))
    }
}
