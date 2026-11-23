package com.localplayer.music.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WriteJournalTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun journal() = WriteJournal(File(tmp.root, "recovery").apply { mkdirs() })

    @Test fun beginThenPendingRoundTrips() {
        val j = journal()
        val dir = j.newDir()
        j.begin(dir, "content://media/external/audio/media/7", "Song = é.mp3", "aaa", "bbb", now = 42L)
        val e = j.pending().single()
        assertEquals(dir.name, e.id)
        assertEquals("content://media/external/audio/media/7", e.uri)
        assertEquals("Song = é.mp3", e.name)
        assertEquals("aaa", e.backupSha)
        assertEquals("bbb", e.newSha)
        assertEquals(42L, e.startedAt)
    }

    @Test fun folderWithoutJournalIsNotPending() {
        val j = journal()
        j.newDir()
        assertTrue(j.pending().isEmpty())
    }

    @Test fun finishRemovesTheFolder() {
        val j = journal()
        val dir = j.newDir()
        j.begin(dir, "u", "n", "a", "b")
        j.finish(dir.name)
        assertFalse(dir.exists())
        assertNull(j.find(dir.name))
    }

    @Test fun classifyTellsCompletedUnchangedAndDamaged() {
        val e = WriteJournal.Entry("id", "u", "n", backupSha = "old", newSha = "new", startedAt = 0)
        assertEquals(WriteJournal.Verdict.COMPLETED, WriteJournal.classify("new", e))
        assertEquals(WriteJournal.Verdict.UNCHANGED, WriteJournal.classify("old", e))
        assertEquals(WriteJournal.Verdict.DAMAGED, WriteJournal.classify("other", e))
    }

    @Test fun orphansAreRemovedOnlyWhenOldAndNeverIfJournaled() {
        val j = journal()
        val orphan = j.newDir()
        val kept = j.newDir()
        j.begin(kept, "u", "n", "a", "b")
        // Fresh orphan: left alone (a save may be running).
        j.cleanOrphans()
        assertTrue(orphan.exists())
        // Old orphan is removed; the journaled folder stays however old it is.
        j.cleanOrphans(now = System.currentTimeMillis() + 60 * 60 * 1000L)
        assertFalse(orphan.exists())
        assertTrue(kept.exists())
    }

    @Test fun corruptJournalIsIgnored() {
        val j = journal()
        val dir = j.newDir()
        File(dir, WriteJournal.JOURNAL).writeText("uri=only-this")
        assertTrue(j.pending().isEmpty())
    }

    @Test fun finishOthersRemovesOlderCopiesOfTheSameFileOnly() {
        val j = journal()
        val old = j.newDir().also { j.begin(it, "file-A", "n", "a", "b", now = 1L) }
        val other = j.newDir().also { j.begin(it, "file-B", "n", "a", "b", now = 2L) }
        val current = j.newDir().also { j.begin(it, "file-A", "n", "a", "b", now = 3L) }
        j.finishOthers("file-A", current.name)
        assertFalse(old.exists())
        assertTrue(other.exists())
        assertTrue(current.exists())
    }

    @Test fun keepNewestOffersTheFiveNewestAndDeletesNothing() {
        val j = journal()
        repeat(8) { i -> j.begin(j.newDir(), "u$i", "n", "a", "b", now = i.toLong()) }
        val kept = j.keepNewest(j.pending())
        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), kept.map { it.startedAt })
        // The older recovery copies stay on disk: they may be the only copy of a damaged file.
        assertEquals(8, j.pending().size)
    }

    @Test fun keepNewestWithFewEntriesDeletesNothing() {
        val j = journal()
        repeat(3) { i -> j.begin(j.newDir(), "u$i", "n", "a", "b", now = i.toLong()) }
        assertEquals(3, j.keepNewest(j.pending()).size)
        assertEquals(3, j.pending().size)
    }

    @Test fun fileChangedMuchLaterIsNotDamagedButChangedLater() {
        val e = WriteJournal.Entry("id", "u", "n", backupSha = "old", newSha = "new", startedAt = 1_000_000L)
        val later = 1_000_000L + WriteJournal.CHANGED_LATER_AFTER_MS + 1
        assertEquals(WriteJournal.Verdict.CHANGED_LATER, WriteJournal.classify("other", e, later))
        // Written around the save itself: still damaged.
        assertEquals(WriteJournal.Verdict.DAMAGED, WriteJournal.classify("other", e, 1_000_000L + 5_000L))
        // No usable modification time: as before.
        assertEquals(WriteJournal.Verdict.DAMAGED, WriteJournal.classify("other", e, null))
        assertEquals(WriteJournal.Verdict.DAMAGED, WriteJournal.classify("other", e, 0L))
        // The known contents win whatever the time says.
        assertEquals(WriteJournal.Verdict.COMPLETED, WriteJournal.classify("new", e, later))
        assertEquals(WriteJournal.Verdict.UNCHANGED, WriteJournal.classify("old", e, later))
    }
}
