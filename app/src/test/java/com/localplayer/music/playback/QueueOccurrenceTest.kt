package com.localplayer.music.playback

import com.localplayer.music.playback.QueueOccurrence.UndoPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Mirrors the queue that the player holds: only occurrence ids + current index matter for Undo. */
class QueueOccurrenceTest {
    private class FakeQueue(val ids: MutableList<String?>, var current: Int)

    /** Applies the same decision PlayerConnection.removeOccurrence applies, to a fake queue. */
    private fun undo(q: FakeQueue, id: String): Boolean = when (val p = QueueOccurrence.planUndo(q.ids, q.current, id)) {
        is UndoPlan.Remove -> {
            q.ids.removeAt(p.index)
            if (p.index < q.current) q.current-- // Media3 shifts the current index the same way
            true
        }
        else -> false
    }

    @Test fun idsAreUnique() = assertNotEquals(QueueOccurrence.newId(), QueueOccurrence.newId())

    @Test fun undoRemovesExactlyTheAddedEntryAndKeepsCurrent() {
        val q = FakeQueue(mutableListOf(null, null, "o1"), current = 1)
        assertEquals(true, undo(q, "o1"))
        assertEquals(listOf<String?>(null, null), q.ids)
        assertEquals(1, q.current)
    }

    @Test fun sameFileAddedTwice_undoOfSecondRemovesSecondOccurrence() {
        // Both entries belong to the same file; only the occurrence ids differ.
        val q = FakeQueue(mutableListOf(null, "first", "second"), current = 0)
        assertEquals(true, undo(q, "second"))
        assertEquals(listOf<String?>(null, "first"), q.ids)
        assertEquals(true, undo(q, "first"))
        assertEquals(listOf<String?>(null), q.ids)
    }

    @Test fun undoOfFirstDoesNotRemoveSecond() {
        val q = FakeQueue(mutableListOf(null, "a", "b"), current = 0)
        undo(q, "a")
        assertEquals(listOf<String?>(null, "b"), q.ids)
    }

    @Test fun alreadyRemovedEntryIsANoOp() {
        val q = FakeQueue(mutableListOf(null, null), current = 0)
        assertEquals(UndoPlan.AlreadyGone, QueueOccurrence.planUndo(q.ids, q.current, "gone"))
        assertEquals(false, undo(q, "gone"))
        assertEquals(2, q.ids.size)
    }

    @Test fun entryThatBecameCurrentIsNeverRemoved() {
        val q = FakeQueue(mutableListOf(null, "o"), current = 1)
        assertEquals(UndoPlan.IsPlayingNow, QueueOccurrence.planUndo(q.ids, q.current, "o"))
        assertEquals(false, undo(q, "o"))
        assertEquals(2, q.ids.size)
    }

    @Test fun entryBeforeCurrentShiftsIndexButKeepsSameTrack() {
        val q = FakeQueue(mutableListOf("o", null, null), current = 2)
        undo(q, "o")
        assertEquals(1, q.current) // still the same MediaItem, one slot earlier
    }

    @Test fun firstItemOfAnEmptyQueueCanBeUndoneWhileNotStarted() {
        val plan = QueueOccurrence.planUndo(listOf("a"), currentIndex = 0, occurrenceId = "a", currentNotStarted = true)
        assertEquals(QueueOccurrence.UndoPlan.Remove(0), plan)
    }

    @Test fun firstItemCannotBeUndoneOnceListeningStarted() {
        val plan = QueueOccurrence.planUndo(listOf("a"), currentIndex = 0, occurrenceId = "a", currentNotStarted = false)
        assertEquals(QueueOccurrence.UndoPlan.IsPlayingNow, plan)
    }

    @Test fun notStartedFlagDoesNotAllowRemovingAnotherCurrentEntry() {
        // entry "b" is not the current one: removal is the normal targeted removal either way
        val plan = QueueOccurrence.planUndo(listOf("a", "b"), currentIndex = 0, occurrenceId = "b", currentNotStarted = true)
        assertEquals(QueueOccurrence.UndoPlan.Remove(1), plan)
    }
}
