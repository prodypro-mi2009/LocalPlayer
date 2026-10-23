package com.localplayer.music.playback.session

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPersistenceTest {
    private fun entry(n: Int, uri: String = "content://t/$n", playNext: Boolean = false, rank: Int = -1) =
        SavedEntry("occ-$n", uri, playNext, rank)

    private fun state(gen: Long, occ: String?, idx: Int, pos: Long = 1234L) =
        SavedState(gen, occ, idx, pos, shuffle = true, repeatMode = 2, writtenAtMs = 99L)

    private fun dir(): File = Files.createTempDirectory("lp-session").toFile()

    // --- codec ---------------------------------------------------------------------------------

    @Test fun queueRoundTripKeepsOrderDuplicatesAndOccurrenceIds() {
        val q = SavedQueue(7, listOf(entry(1, "content://same"), entry(2, "content://same", playNext = true, rank = 4), entry(3)))
        val back = SessionCodec.decodeQueue(SessionCodec.encodeQueue(q))
        assertEquals(q, back)
        assertEquals(listOf("occ-1", "occ-2", "occ-3"), back!!.entries.map { it.occurrenceId })
    }

    @Test fun stateRoundTripIsExactToTheMillisecond() {
        val s = state(3, "occ-9", 8, 123_456_789L)
        assertEquals(s, SessionCodec.decodeState(SessionCodec.encodeState(s)))
        val noCurrent = s.copy(currentOccurrenceId = null)
        assertEquals(noCurrent, SessionCodec.decodeState(SessionCodec.encodeState(noCurrent)))
    }

    @Test fun damagedOrTruncatedFilesAreRejected() {
        val bytes = SessionCodec.encodeQueue(SavedQueue(1, listOf(entry(1), entry(2))))
        assertNull(SessionCodec.decodeQueue(bytes.copyOf(bytes.size - 3)))
        val flipped = bytes.copyOf().also { it[12] = (it[12] + 1).toByte() }
        assertNull(SessionCodec.decodeQueue(flipped))
        assertNull(SessionCodec.decodeQueue(ByteArray(0)))
        assertNull(SessionCodec.decodeState(bytes)) // wrong kind of file
    }

    // --- store ---------------------------------------------------------------------------------

    @Test fun savedSessionIsLoadedBack() {
        val core = SessionStoreCore(dir(), Executor { it.run() })
        val q = SavedQueue(1, listOf(entry(1), entry(2)))
        core.writeQueue(q)
        core.writeState(state(1, "occ-2", 1))
        val loaded = core.load()
        assertNotNull(loaded)
        assertEquals(q, loaded!!.queue)
        assertEquals("occ-2", loaded.state!!.currentOccurrenceId)
        assertEquals(1234L, loaded.state!!.positionMs)
    }

    @Test fun anEmptyQueueNeverReplacesAValidSnapshot() {
        val core = SessionStoreCore(dir(), Executor { it.run() })
        core.writeQueue(SavedQueue(1, listOf(entry(1))))
        core.writeQueue(SavedQueue(2, emptyList()))
        assertEquals(1L, core.load()!!.queue.generation)
        assertEquals(1, core.load()!!.queue.entries.size)
    }

    @Test fun invalidateRemovesTheSession() {
        val core = SessionStoreCore(dir(), Executor { it.run() })
        core.writeQueue(SavedQueue(1, listOf(entry(1))))
        core.writeState(state(1, "occ-1", 0))
        core.invalidate()
        assertNull(core.load())
    }

    @Test fun staleWritesQueuedBeforeInvalidateCannotBringTheSessionBack() {
        // Executor that holds jobs until we release them, to model slow disk I/O.
        val held = ArrayList<Runnable>()
        val core = SessionStoreCore(dir(), Executor { held += it })
        core.writeQueue(SavedQueue(1, listOf(entry(1))))
        core.writeState(state(1, "occ-1", 0))
        core.invalidate() // intentional clear while the writes above are still waiting
        held.forEach { it.run() }
        assertNull(core.load())
    }

    @Test fun writesRequestedAfterInvalidateAreKept() {
        val held = ArrayList<Runnable>()
        val core = SessionStoreCore(dir(), Executor { held += it })
        core.writeQueue(SavedQueue(1, listOf(entry(1))))
        core.invalidate()
        core.writeQueue(SavedQueue(2, listOf(entry(5), entry(6))))
        held.forEach { it.run() }
        assertEquals(2L, core.load()!!.queue.generation)
    }

    @Test fun burstOfWritesEndsWithTheNewestOne() {
        val core = SessionStoreCore(dir(), Executor { it.run() })
        for (g in 1L..20L) core.writeQueue(SavedQueue(g, listOf(entry(g.toInt()))))
        assertEquals(20L, core.load()!!.queue.generation)
    }

    @Test fun corruptFilesOnDiskAreIgnored() {
        val d = dir()
        File(d, "queue.bin").writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9))
        assertNull(SessionStoreCore(d, Executor { it.run() }).load())
    }

    @Test fun flushBlockingWaitsForBackgroundWrites() {
        val exec = Executors.newSingleThreadExecutor()
        try {
            val core = SessionStoreCore(dir(), exec)
            core.writeQueue(SavedQueue(1, listOf(entry(1))))
            core.writeState(state(1, "occ-1", 0))
            assertEquals(SessionStoreCore.SaveOutcome.DURABLE, core.flushBlocking(5_000))
            assertNotNull(core.load())
        } finally {
            exec.shutdown()
        }
    }

    @Test fun noTempFilesAreLeftBehind() {
        val d = dir()
        val core = SessionStoreCore(d, Executor { it.run() })
        core.writeQueue(SavedQueue(1, listOf(entry(1))))
        core.writeState(state(1, "occ-1", 0))
        assertFalse(d.listFiles()!!.any { it.name.endsWith(".tmp") })
    }

    // --- observable failures ---------------------------------------------------------------------

    @Test fun aFailedWriteKeepsTheLastValidSnapshotAndIsReported() {
        val d = dir()
        val log = ArrayList<String>()
        var fail = false
        val core = SessionStoreCore(d, Executor { it.run() }, { log += it }) { target, bytes ->
            if (fail) throw IOException("disk full /secret/path content://media/private")
            SessionStoreCore.atomicWriteTo(d, target, bytes)
        }
        core.writeQueue(SavedQueue(1, listOf(entry(1), entry(2))))
        fail = true
        core.writeQueue(SavedQueue(2, listOf(entry(7))))
        assertEquals(SessionStoreCore.SaveOutcome.FAILED, core.flushBlocking(1_000))
        val loaded = core.load()!!
        assertEquals(1L, loaded.queue.generation) // the older, valid snapshot is untouched
        assertEquals(2, loaded.queue.entries.size)
        assertTrue(log.isNotEmpty())
        assertTrue("no URIs or paths in the log", log.none { it.contains("content://") || it.contains("/secret") })
    }

    @Test fun aLaterSuccessfulWriteClearsTheFailure() {
        val d = dir()
        var fail = true
        val core = SessionStoreCore(d, Executor { it.run() }, {}) { target, bytes ->
            if (fail) throw IOException("x") else SessionStoreCore.atomicWriteTo(d, target, bytes)
        }
        core.writeQueue(SavedQueue(1, listOf(entry(1))))
        assertEquals(SessionStoreCore.SaveOutcome.FAILED, core.flushBlocking(1_000))
        fail = false
        core.writeQueue(SavedQueue(2, listOf(entry(2))))
        assertEquals(SessionStoreCore.SaveOutcome.DURABLE, core.flushBlocking(1_000))
        assertEquals(2L, core.load()!!.queue.generation)
    }

    @Test fun anUnwritableDirectoryIsAFailureNotACrash() {
        val blocker = File.createTempFile("lp-blocker", ".tmp") // a FILE where the directory should be
        val core = SessionStoreCore(File(blocker, "sub"), Executor { it.run() })
        core.writeQueue(SavedQueue(1, listOf(entry(1))))
        assertEquals(SessionStoreCore.SaveOutcome.FAILED, core.flushBlocking(1_000))
        assertNull(core.load())
    }

    @Test fun aBusyWriterIsATimeoutNotAConfirmedSave() {
        val held = ArrayList<Runnable>() // the writer never gets to run
        val core = SessionStoreCore(dir(), Executor { held += it })
        core.writeQueue(SavedQueue(1, listOf(entry(1))))
        assertEquals(SessionStoreCore.SaveOutcome.TIMED_OUT, core.flushBlocking(50))
        assertNull(core.load()) // nothing is on disk yet
    }

    @Test fun aFailedWriteLeavesNoTempFileAndNoPartialSnapshot() {
        val d = dir()
        val core = SessionStoreCore(d, Executor { it.run() }, {}) { target, bytes ->
            // simulate a crash after a partial temp write
            File(d, target.name + ".tmp").writeBytes(bytes.copyOf(bytes.size / 2))
            throw IOException("interrupted")
        }
        core.writeQueue(SavedQueue(1, listOf(entry(1))))
        assertNull(core.load())
    }

    // --- restore plan --------------------------------------------------------------------------

    private fun saved(current: Int?, entries: List<SavedEntry>, pos: Long = 5000L) =
        SavedSession(
            SavedQueue(1, entries),
            current?.let { state(1, entries[it].occurrenceId, it, pos) },
        )

    @Test fun restoreKeepsCurrentSongAndExactPosition() {
        val entries = (1..5).map { entry(it) }
        val plan = SessionRestorePlan.plan(saved(2, entries, 98_765L), entries.map { it.trackUri }.toSet())!!
        assertEquals(entries, plan.entries)
        assertEquals(2, plan.startIndex)
        assertEquals(98_765L, plan.positionMs)
    }

    @Test fun duplicateFilesKeepTheirOwnOccurrence() {
        val entries = listOf(entry(1, "content://x"), entry(2, "content://y"), entry(3, "content://x"))
        val plan = SessionRestorePlan.plan(saved(2, entries), setOf("content://x", "content://y"))!!
        assertEquals(2, plan.startIndex) // the second x, not the first
        assertEquals(listOf("occ-1", "occ-2", "occ-3"), plan.entries.map { it.occurrenceId })
    }

    @Test fun missingFilesAreDroppedAndIndexFollowsTheCurrentSong() {
        val entries = (1..5).map { entry(it) }
        val available = entries.map { it.trackUri }.toSet() - "content://t/1" - "content://t/4"
        val plan = SessionRestorePlan.plan(saved(2, entries), available)!!
        assertEquals(listOf("occ-2", "occ-3", "occ-5"), plan.entries.map { it.occurrenceId })
        assertEquals(1, plan.startIndex)
        assertEquals(5000L, plan.positionMs)
    }

    @Test fun ifTheCurrentSongIsMissingTheNextSurvivorStartsFromZero() {
        val entries = (1..5).map { entry(it) }
        val available = entries.map { it.trackUri }.toSet() - "content://t/3" - "content://t/4"
        val plan = SessionRestorePlan.plan(saved(2, entries), available)!!
        assertEquals("occ-5", plan.entries[plan.startIndex].occurrenceId)
        assertEquals(0L, plan.positionMs)
    }

    @Test fun ifNothingAfterTheMissingCurrentSongTheLastSurvivorBeforeIsUsed() {
        val entries = (1..3).map { entry(it) }
        val plan = SessionRestorePlan.plan(saved(2, entries), setOf("content://t/1", "content://t/2"))!!
        assertEquals("occ-2", plan.entries[plan.startIndex].occurrenceId)
        assertEquals(0L, plan.positionMs)
    }

    @Test fun nothingSurvivingMeansNothingToRestore() {
        val entries = (1..3).map { entry(it) }
        assertNull(SessionRestorePlan.plan(saved(1, entries), emptySet()))
    }

    @Test fun stateFromAnOlderQueueFallsBackToTheStart() {
        val entries = (1..3).map { entry(it) }
        val s = SavedSession(SavedQueue(5, entries), state(1, "occ-gone", 9))
        val plan = SessionRestorePlan.plan(s, entries.map { it.trackUri }.toSet())!!
        assertEquals(0, plan.startIndex)
        assertEquals(0L, plan.positionMs)
    }

    @Test fun missingStateStartsAtTheFirstSong() {
        val entries = (1..3).map { entry(it) }
        val plan = SessionRestorePlan.plan(saved(null, entries), entries.map { it.trackUri }.toSet())!!
        assertEquals(0, plan.startIndex)
    }
}
