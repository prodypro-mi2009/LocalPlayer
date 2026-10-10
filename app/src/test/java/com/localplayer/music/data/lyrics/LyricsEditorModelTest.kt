package com.localplayer.music.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsEditorModelTest {
    private fun raw(doc: EditorDocument) = (LyricsEditorModel.toRaw(doc) as LyricsEditorModel.Result.Ok).raw

    @Test fun unchangedSyncedLyricsRoundTripExactly() {
        val original = "[ti:Song]\n[ar:Rack]\n[00:12.500]Hello\n[00:15.00]Γειά\n[00:20.5]x"
        assertEquals(original, raw(LyricsEditorModel.fromRaw(original)))
    }
    @Test fun unchangedPlainLyricsRoundTripExactly() {
        val original = "line one\n\nline three"
        assertEquals(original, raw(LyricsEditorModel.fromRaw(original)))
    }
    @Test fun multipleTimestampsBecomeSeparateRows() {
        val doc = LyricsEditorModel.fromRaw("[00:10.00][01:20.00]Chorus")
        assertEquals(listOf("00:10.00", "01:20.00"), doc.lines.map { it.timeText })
    }
    @Test fun editingTextAndTimestamp() {
        val doc = LyricsEditorModel.fromRaw("[00:10.00]old")
        val edited = doc.copy(lines = listOf(doc.lines[0].copy(timeText = "00:11.25", text = "new")))
        assertEquals("[00:11.25]new", raw(edited))
    }
    @Test fun invalidTimestampIsReportedWithItsLine() {
        val doc = EditorDocument(emptyList(), listOf(EditorLine(1, "00:05.00", "a"), EditorLine(2, "5 sec", "b")))
        val result = LyricsEditorModel.toRaw(doc) as LyricsEditorModel.Result.BadTimestamp
        assertEquals(2, result.lineNumber)
    }
    @Test fun emptyDocumentRemovesTheField() = assertNull(raw(EditorDocument(emptyList(), emptyList())))
    @Test fun previewIsSortedAndSkipsPlainRows() {
        val doc = EditorDocument(emptyList(), listOf(EditorLine(1, "00:09.00", "b"), EditorLine(2, "", "plain"), EditorLine(3, "00:01.00", "a")))
        assertEquals(listOf("a", "b"), LyricsEditorModel.previewLines(doc).map { it.text })
    }
    @Test fun stampFromPlaybackPosition() {
        val doc = EditorDocument(emptyList(), listOf(EditorLine(1, "", "line")))
        val stamped = doc.copy(lines = listOf(doc.lines[0].copy(timeText = LrcParser.formatTimestamp(83_456))))
        assertEquals("[01:23.45]line", raw(stamped)); assertTrue(LrcParser.parseTimestamp("01:23.45") == 83_450L)
    }
}

class LyricsEditorSaveSemanticsTest {
    private fun raw(doc: EditorDocument) = (LyricsEditorModel.toRaw(doc) as LyricsEditorModel.Result.Ok).raw

    @Test fun deletingOneLineRemovesOnlyThatLine() {
        val doc = LyricsEditorModel.fromRaw("[00:01.00]a\n[00:02.00]b\n[00:03.00]c")
        val without = doc.copy(lines = doc.lines.filterNot { it.text == "b" })
        assertEquals("[00:01.00]a\n[00:03.00]c", raw(without))
    }

    @Test fun deletingEveryLineClearsTheFieldEvenWithHeaderTags() {
        val doc = LyricsEditorModel.fromRaw("[ti:Song]\n[00:01.00]a")
        assertNull(raw(doc.copy(lines = emptyList())))
    }

    @Test fun textModeRoundTripsAndKeepsMalformedLinesVerbatim() {
        val original = "[ti:Song]\n[00:01.00]a\n[00:xx.00]broken\nplain"
        val text = LyricsEditorModel.toText(LyricsEditorModel.fromRaw(original))
        assertEquals(original, text)
        assertEquals(original, raw(LyricsEditorModel.fromRaw(text)))
    }

    @Test fun pastedBlockBecomesRowsInOrder() {
        val doc = LyricsEditorModel.fromRaw("[00:02.00]b\n[00:01.00]a")
        assertEquals(listOf("b", "a"), doc.lines.map { it.text }) // order is preserved, never re-sorted
    }

    @Test fun savingTheSameContentTwiceGivesTheSameRaw() {
        val doc = LyricsEditorModel.fromRaw("[00:01.00]a\n[00:03.00]c")
        assertEquals(raw(doc), raw(LyricsEditorModel.fromRaw(raw(doc))))
    }
}
