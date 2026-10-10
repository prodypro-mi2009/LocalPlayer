package com.localplayer.music.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test fun normalLines() {
        val p = LrcParser.parse("[00:12.50]Hello\n[00:15.00]World")
        assertTrue(p.isSynced)
        assertEquals(listOf(LyricLine(12_500, "Hello"), LyricLine(15_000, "World")), p.lines)
        assertEquals("Hello\nWorld", p.plainText)
    }
    @Test fun centisecondsMillisecondsAndNoFraction() {
        val p = LrcParser.parse("[00:01.5]a\n[00:02.50]b\n[00:03.123]c\n[00:04]d")
        assertEquals(listOf(1_500L, 2_500L, 3_123L, 4_000L), p.lines.map { it.timestampMs })
    }
    @Test fun minutesOverTen() = assertEquals(754_100L, LrcParser.parse("[12:34.10]x").lines.single().timestampMs)
    @Test fun multipleTimestampsOnOneLine() {
        val p = LrcParser.parse("[00:10.00][01:20.00]Chorus")
        assertEquals(listOf(LyricLine(10_000, "Chorus"), LyricLine(80_000, "Chorus")), p.lines)
    }
    @Test fun unsortedInputIsSorted_andEqualTimesKeepOrder() {
        val p = LrcParser.parse("[00:20.00]c\n[00:05.00]a\n[00:05.00]b")
        assertEquals(listOf("a", "b", "c"), p.lines.map { it.text })
    }
    @Test fun metadataTags() {
        val p = LrcParser.parse("[ar:Artist]\n[ti:Title]\n[al:Album]\n[by:me]\n[00:01.00]x")
        assertEquals("Artist", p.tags["ar"]); assertEquals("Title", p.tags["ti"])
        assertEquals("Album", p.tags["al"]); assertEquals("me", p.tags["by"])
        assertEquals("x", p.plainText)
    }
    @Test fun positiveOffsetMakesLyricsEarlier() {
        val p = LrcParser.parse("[offset:+500]\n[00:10.00]x")
        assertEquals(500L, p.offsetMs); assertEquals(9_500L, p.lines.single().timestampMs)
    }
    @Test fun negativeOffsetMakesLyricsLater() =
        assertEquals(10_500L, LrcParser.parse("[offset:-500]\n[00:10.00]x").lines.single().timestampMs)
    @Test fun offsetNeverGoesBelowZero() =
        assertEquals(0L, LrcParser.parse("[offset:5000]\n[00:01.00]x").lines.single().timestampMs)
    @Test fun plainLyricsAreNotSynced() {
        val p = LrcParser.parse("line one\nline two")
        assertFalse(p.isSynced); assertEquals("line one\nline two", p.plainText)
    }
    @Test fun emptyAndNull() {
        assertTrue(LrcParser.parse(null).isEmpty); assertTrue(LrcParser.parse("  \n ").isEmpty)
    }
    @Test fun malformedLinesNeverThrow() {
        val p = LrcParser.parse("[00:xx]a\n[:12]b\n[99999:99:99]c\n[]\n[00:05.00")
        assertFalse(p.isSynced)
    }
    @Test fun emptyTextLineKeepsItsTimestamp() {
        val p = LrcParser.parse("[00:05.00]\n[00:06.00]x")
        assertEquals(listOf(LyricLine(5_000, ""), LyricLine(6_000, "x")), p.lines)
    }
    @Test fun windowsLineEndings() =
        assertEquals(listOf("a", "b"), LrcParser.parse("[00:01.00]a\r\n[00:02.00]b\r\n").lines.map { it.text })
    @Test fun enhancedWordTimestampsAreStrippedFromTheText() =
        assertEquals("Hello world", LrcParser.parse("[00:01.00]<00:01.00>Hello <00:01.50>world").lines.single().text)
    @Test fun greekText() =
        assertEquals("Το ξέρω πως δε μ' αγαπάς", LrcParser.parse("[00:00.86] Το ξέρω πως δε μ' αγαπάς").lines.single().text)
    @Test fun lrclibStyleSample() {
        val p = LrcParser.parse("[00:00.86] Το ξέρω πως δε μ' αγαπάς\n[00:03.12] Παραμύθια μη πουλάς\n[03:02.74] (Τι θέλεις από μένα;)")
        assertEquals(3, p.lines.size); assertEquals(182_740L, p.lines.last().timestampMs)
    }
    @Test fun timestampParsingAndFormatting() {
        assertEquals(75_250L, LrcParser.parseTimestamp("01:15.25"))
        assertNull(LrcParser.parseTimestamp("01:75.00")); assertNull(LrcParser.parseTimestamp("abc"))
        assertEquals("01:15.25", LrcParser.formatTimestamp(75_250)); assertEquals("00:00.00", LrcParser.formatTimestamp(-5))
    }
}
