package com.localplayer.music.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Real files, real tag writes. Every test works on a COPY of a fixture, changes ONE field,
 * re-reads the actual file and checks that everything else (including artwork and lyrics) is intact.
 */
class FileTagEngineTest {
    private val values = mapOf(
        MetadataField.TITLE to "Song 2",
        MetadataField.ARTIST to "Rack",
        MetadataField.ALBUM to "Νέο Άλμπουμ, Vol. 2 & x",
        MetadataField.ALBUM_ARTIST to "Rack, X",
        MetadataField.GENRE to "Hip hop / Trap",
        MetadataField.YEAR to "2026",
        MetadataField.TRACK_NUMBER to "4",
        MetadataField.DISC_NUMBER to "2",
        MetadataField.COMPOSER to "Μουσική Σύνθεση",
        MetadataField.COMMENT to "new comment ; with, punctuation..",
        MetadataField.LYRICS to "[00:01.00]line one\n[00:02.50]γραμμή δύο\n[01:03.123]third",
    )

    @Before fun setUp() = FileTagEngine.configure(android = false)

    private fun copyOf(name: String): File {
        val source = File(requireNotNull(javaClass.getResource("/fixtures/$name")).toURI())
        val dir = Files.createTempDirectory("tagtest").toFile().apply { deleteOnExit() }
        return File(dir, name).also { source.copyTo(it); it.deleteOnExit() }
    }

    private fun forEachWritable(block: (name: String, container: AudioContainer) -> Unit) {
        block("t.mp3", AudioContainer.MP3)
        block("t.flac", AudioContainer.FLAC)
        block("t.m4a", AudioContainer.MP4)
    }

    @Test fun everyFieldIsWrittenToTheActualFileAndNothingElseChanges() = forEachWritable { name, container ->
        val original = FileTagEngine.read(copyOf(name), container)
        for (field in MetadataField.entries) {
            val file = copyOf(name)
            val changes = mapOf<MetadataField, String?>(field to values.getValue(field))
            FileTagEngine.write(file, container, changes)
            val reread = FileTagEngine.read(file, AudioSniffer.sniff(file, name))
            assertNull("$name $field", FileTagEngine.verify(original, reread, changes))
            assertEquals("$name $field", values[field], reread.value(field))
        }
    }

    @Test fun editingOneFieldKeepsArtworkLyricsAndOtherTags() = forEachWritable { name, container ->
        val file = copyOf(name)
        val before = FileTagEngine.read(file, container)
        assertTrue("$name fixture should have artwork", before.hasArtwork)
        FileTagEngine.write(file, container, mapOf(MetadataField.TITLE to "Song 2"))
        val after = FileTagEngine.read(file, container)
        assertEquals(before.artworkCount, after.artworkCount)
        assertEquals(before.lyrics, after.lyrics)
        assertEquals(before.artist, after.artist)
        assertEquals(before.album, after.album)
        assertEquals(before.genre, after.genre)
        assertEquals(before.year, after.year)
        assertEquals(before.trackNumber, after.trackNumber)
        assertEquals(before.discNumber, after.discNumber)
        assertEquals(before.composer, after.composer)
    }

    @Test fun artworkIsByteForByteIdenticalAfterEveryUnrelatedEdit() = forEachWritable { name, container ->
        val before = FileTagEngine.read(copyOf(name), container)
        assertTrue("$name needs fingerprints", before.artworkFingerprints.isNotEmpty())
        for (field in MetadataField.entries) {
            val file = copyOf(name)
            FileTagEngine.write(file, container, mapOf(field to values.getValue(field)))
            val after = FileTagEngine.read(file, container)
            assertEquals("$name $field artwork", before.artworkFingerprints, after.artworkFingerprints)
        }
    }

    @Test fun lyricsAreWrittenExactlyIncludingTimestampsAndGreek() = forEachWritable { name, container ->
        val file = copyOf(name)
        val lrc = "[ti:Title]\n[00:12.50]Πρώτη γραμμή\n[00:15.00][01:20.00]repeat\n"
        FileTagEngine.write(file, container, mapOf(MetadataField.LYRICS to lrc))
        assertEquals(lrc, FileTagEngine.read(file, container).lyrics)
    }

    @Test fun editingTheTagsKeepsExistingLyrics() = forEachWritable { name, container ->
        val file = copyOf(name)
        val before = FileTagEngine.read(file, container)
        FileTagEngine.write(file, container, mapOf(MetadataField.ARTIST to "Rack"))
        assertEquals(before.lyrics, FileTagEngine.read(file, container).lyrics)
    }

    @Test fun userTextIsWrittenExactly() = forEachWritable { name, container ->
        val file = copyOf(name)
        val changes: MetadataChanges = mapOf(
            MetadataField.ARTIST to "Rack feat. X",
            MetadataField.ALBUM_ARTIST to "Rack, X",
            MetadataField.TITLE to "  padded  title ",
            MetadataField.COMMENT to "line1\nline2",
        )
        FileTagEngine.write(file, container, changes)
        val after = FileTagEngine.read(file, container)
        assertEquals("Rack feat. X", after.artist)
        assertEquals("Rack, X", after.albumArtist)
        assertEquals("  padded  title ", after.title)
        assertEquals("line1\nline2", after.comment)
    }

    @Test fun clearingAFieldRemovesItFromTheFile() = forEachWritable { name, container ->
        val file = copyOf(name)
        FileTagEngine.write(file, container, mapOf(MetadataField.COMPOSER to null))
        assertNull(FileTagEngine.read(file, container).composer)
    }

    @Test fun trackTotalIsKeptWhenOnlyTheTrackNumberChanges() {
        val file = copyOf("t.flac")
        FileTagEngine.write(file, AudioContainer.FLAC, mapOf(MetadataField.TRACK_NUMBER to "5"))
        val audio = org.jaudiotagger.audio.AudioFileIO.read(file)
        assertEquals("12", audio.tag.getFirst(org.jaudiotagger.tag.FieldKey.TRACK_TOTAL))
    }

    @Test fun portabilityTheChangeLivesInTheFileNotInAnyDatabase() {
        // Write, then read the bytes again from a brand-new copy of the written file with a fresh
        // reader call: no app state is involved, only the file.
        val file = copyOf("t.mp3")
        FileTagEngine.write(file, AudioContainer.MP3, mapOf(MetadataField.ARTIST to "New Artist"))
        val transferred = File(file.parentFile, "transferred.mp3").also { file.copyTo(it) }
        assertEquals("New Artist", FileTagEngine.read(transferred, AudioContainer.MP3).artist)
    }

    @Test fun rawAacIsRefusedHonestly() {
        val file = copyOf("t.aac")
        val container = AudioSniffer.sniff(file, "t.aac")
        assertEquals(AudioContainer.AAC_ADTS, container)
        try {
            FileTagEngine.write(file, container, mapOf(MetadataField.TITLE to "x"))
            fail("raw AAC must not be written")
        } catch (e: MetadataException) {
            assertEquals(FailureKind.UNSUPPORTED_FORMAT, e.kind)
        }
    }

    @Test fun verificationCatchesAnUnexpectedChange() {
        val file = copyOf("t.mp3")
        val before = FileTagEngine.read(file, AudioContainer.MP3)
        val tampered = before.copy(title = "different")
        assertTrue(FileTagEngine.verify(before, tampered, emptyMap())!!.contains("TITLE"))
        assertTrue(FileTagEngine.verify(before, before, mapOf(MetadataField.TITLE to "wanted"))!!.contains("expected"))
    }

    // --- lyrics: the file is the source of truth ------------------------------------------------

    @Test fun lyricsReplaceDeleteAndRewriteOnTheRealFile() = forEachWritable { name, container ->
        val file = copyOf(name)
        val original = FileTagEngine.read(file, container)

        // replace: the NEW text is what a re-read returns (never the old one)
        val synced = "[00:01.00]one\n[00:02.00]two\n[00:03.00]three"
        FileTagEngine.write(file, container, mapOf(MetadataField.LYRICS to synced))
        assertEquals("$name replace", synced, FileTagEngine.read(file, container).lyrics)

        // delete ONE line (= write the shorter text)
        val shorter = "[00:01.00]one\n[00:03.00]three"
        FileTagEngine.write(file, container, mapOf(MetadataField.LYRICS to shorter))
        assertEquals("$name delete a line", shorter, FileTagEngine.read(file, container).lyrics)

        // saving the same content a second time changes nothing
        FileTagEngine.write(file, container, mapOf(MetadataField.LYRICS to shorter))
        assertEquals("$name same twice", shorter, FileTagEngine.read(file, container).lyrics)

        // delete everything: the field is gone, it does not come back
        FileTagEngine.write(file, container, mapOf(MetadataField.LYRICS to null))
        val cleared = FileTagEngine.read(file, container)
        assertNull("$name cleared", cleared.lyrics)

        // plain lyrics afterwards
        FileTagEngine.write(file, container, mapOf(MetadataField.LYRICS to "plain\n\nΕλληνικά"))
        val plain = FileTagEngine.read(file, container)
        assertEquals("$name plain", "plain\n\nΕλληνικά", plain.lyrics)

        // everything unrelated survived all of that
        assertEquals(original.artworkFingerprints, plain.artworkFingerprints)
        assertEquals(original.title, plain.title)
        assertEquals(original.artist, plain.artist)
        assertTrue(kotlin.math.abs(original.durationMs - plain.durationMs) <= 150)
    }
}
