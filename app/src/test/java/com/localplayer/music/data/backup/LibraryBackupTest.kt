package com.localplayer.music.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LibraryBackupTest {
    private val refs = listOf(
        TrackRef("content://media/1", "a.mp3", "Song A", "Art", 200_000, 5_000),
        TrackRef("content://media/2", "b.mp3", "Song B", "Art", 100_000, 4_000),
    )

    private fun sample() = LibraryData(
        refs,
        listOf(PlaylistData("Mix", 1, 2, null, null, true, "1-2.jpg", listOf(PlaylistItemData(1, 1, 5), PlaylistItemData(0, 0, 4)))),
        listOf(FavoriteData(0, 9)), listOf(HistoryData(1, 77)), listOf(DayData(20261027, 0, 1000, 2, 5)),
        listOf(OverrideData(0, "X")), listOf(HiddenData(1, 3)), listOf(ExcludedData("v:Music/", 4)),
    )

    @Test fun codec_roundTrips_andOrdersPlaylistItems() {
        val back = LibraryBackupCodec.decode(LibraryBackupCodec.encode(sample(), "1.0", 1L))
        assertEquals(2, back.tracks.size)
        assertEquals(listOf(0, 1), back.playlists.single().items.map { it.track })
        assertEquals("1-2.jpg", back.playlists.single().cover)
        assertEquals(20261027, back.days.single().day)
        assertEquals("X", back.overrides.single().primaryArtist)
        assertEquals("v:Music/", back.excluded.single().prefix)
    }

    @Test fun codec_dropsRowsThatPointOutsideTheSongTable_andRejectsForeignFiles() {
        val text = LibraryBackupCodec.encode(sample(), "1", 1L).replace("\"favorites\":[[0,9]]", "\"favorites\":[[99,9],[0,9]]")
        assertEquals(1, LibraryBackupCodec.decode(text).favorites.size)
        for (bad in listOf("", "{}", "[]", "{\"format\":\"other\"}", "{\"format\":\"localplayer-library\",\"version\":99}")) {
            try { LibraryBackupCodec.decode(bad); fail("accepted: $bad") } catch (e: BackupFormat.InvalidBackup) { }
        }
    }

    @Test fun matcher_neverTrustsAUriThatNowBelongsToAnotherSong() {
        val current = listOf(
            CurrentTrack("content://media/77", "a.mp3", "Song A", "Art", 200_500, 5_000),     // same song, other phone
            CurrentTrack("content://media/2", "zzz.mp3", "Other", "X", 1, 1),                  // same URI, different song
            CurrentTrack("content://media/5", "dup.mp3", "Dup", "Art", 1000, 1),
            CurrentTrack("content://media/6", "dup.mp3", "Dup", "Art", 1000, 1),
        )
        val m = TrackMatcher(current)
        assertEquals("content://media/77", m.match(refs[0]))
        assertNull(m.match(refs[1]))
        assertNull(m.match(TrackRef("x", "dup.mp3", "Dup", "Art", 1000, 1)))            // ambiguous: never guessed
        assertEquals("content://media/77", m.match(TrackRef("x", "q.mp3", "Song A", "art", 201_000, 0)))   // title + artist + duration
        assertEquals("content://media/5", TrackMatcher(current.take(3)).match(TrackRef("content://media/5", "dup.mp3", "Dup", "Art", 1000, 1)))
    }

    @Test fun matcher_sameUriWithAgreeingNameIsAccepted() {
        val m = TrackMatcher(listOf(CurrentTrack("content://media/1", "A.MP3", "Song A", "Art", 1, 1)))
        assertEquals("content://media/1", m.match(refs[0]))
    }

    @Test fun preview_countsUnmatchedPerSection() {
        val data = sample()
        val resolved = listOf<String?>("content://media/77", null)
        val p = LibraryPlan.preview(data, resolved).associateBy { it.section }
        assertEquals(1, p.getValue(LibrarySection.PLAYLISTS).total); assertEquals(1, p.getValue(LibrarySection.PLAYLISTS).unmatched)
        assertEquals(0, p.getValue(LibrarySection.FAVORITES).unmatched)
        assertEquals(2, p.getValue(LibrarySection.HISTORY).total); assertEquals(1, p.getValue(LibrarySection.HISTORY).unmatched)
        assertEquals(3, p.getValue(LibrarySection.RULES).total); assertEquals(1, p.getValue(LibrarySection.RULES).unmatched)
        assertTrue(LibraryPlan.preview(LibraryData(refs, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()), resolved).isEmpty())
    }

    // ---- archive with library data and covers ----

    private fun tmp() = File.createTempFile("lpb", "").let { it.delete(); it.mkdirs(); it }

    @Test fun archive_carriesLibraryAndCovers_andRejectsTampering() {
        val settings = BackupFormat.encodeSettings(mapOf("dynamicColor" to true), "1", 1L, false, false, true)
        val libJson = LibraryBackupCodec.encode(sample(), "1", 1L)
        val cover = ByteArray(300) { it.toByte() }
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, settings, "1", 1L, listOf(BackupArchive.Asset(BackupFormat.coverPath("1-2.jpg")) { ByteArrayInputStream(cover) }), libJson)
        val parsed = BackupArchive.read(ByteArrayInputStream(out.toByteArray()), tmp())
        assertEquals(libJson, parsed.library)
        assertTrue(parsed.covers.getValue("1-2.jpg").readBytes().contentEquals(cover))

        // an archive without library data (older app versions) still reads
        val old = ByteArrayOutputStream(); BackupArchive.write(old, settings, "1", 1L, emptyList())
        assertNull(BackupArchive.read(ByteArrayInputStream(old.toByteArray()), tmp()).library)
    }

    @Test fun archive_refusesUnsafeEntryNames() {
        for (name in listOf("assets/covers/../evil.jpg", "assets/covers/x.png", "assets/covers/a/b.jpg", "library.json.bak")) {
            val z = ByteArrayOutputStream()
            ZipOutputStream(z).use { it.putNextEntry(ZipEntry(name)); it.write(1); it.closeEntry() }
            try { BackupArchive.read(ByteArrayInputStream(z.toByteArray()), tmp()); fail("accepted $name") } catch (e: BackupFormat.InvalidBackup) { }
        }
        assertTrue(BackupFormat.isCoverName("12-1700000.jpg"))
        assertFalse(BackupFormat.isCoverName("a/b.jpg"))
    }

    // ---- automatic backup rules ----

    @Test fun autoBackup_isDueOncePerWeekAndOnlyWhenEnabled() {
        val week = AutoBackupRules.INTERVAL_MS
        assertFalse(AutoBackupRules.isDue(false, week * 2, 0, 0))
        assertTrue(AutoBackupRules.isDue(true, 1000, 0, 0))                          // never made one
        assertFalse(AutoBackupRules.isDue(true, week, 1000, 0))                      // 6.99 days
        assertTrue(AutoBackupRules.isDue(true, week + 1000, 1000, 0))
        assertFalse(AutoBackupRules.isDue(true, week * 2, 1000, week * 2 - 1000))    // failed a moment ago: wait
        assertTrue(AutoBackupRules.isDue(true, week * 2, 1000, week * 2 - AutoBackupRules.RETRY_MS))
        assertTrue(AutoBackupRules.isDue(true, 500, 10_000, 0))                      // clock went backwards
    }

    @Test fun autoBackup_keepsTheNewestThreeFilesOfOurs() {
        val names = listOf(
            "LocalPlayer-auto-20261001-0900.lpbackup", "LocalPlayer-auto-20261008-0900.lpbackup",
            "LocalPlayer-auto-20261015-0900.lpbackup", "LocalPlayer-auto-20261022-0900.lpbackup", "holiday.jpg", "mine.lpbackup",
        )
        assertEquals(listOf("LocalPlayer-auto-20261001-0900.lpbackup"), AutoBackupRules.toDelete(names))
    }
}
