package com.localplayer.music.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

class YtDlpSearchTest {
    @Test fun parsesSearchLines() {
        val out = "abc123XYZ_-\tCreep\tRadiohead - Topic\t238\nbad id!\tx\ty\t1\nzzz\tNo length\tNA\tNA\nabc123XYZ_-\tDuplicate\tz\t5\n"
        val hits = YtDlpEngine.parseSearch(out)
        assertEquals(2, hits.size)
        assertEquals(238_000L, hits[0].durationMs)
        assertEquals("Radiohead - Topic", hits[0].channel)
        assertEquals("", hits[1].channel)
        assertEquals(0L, hits[1].durationMs)
    }
}
