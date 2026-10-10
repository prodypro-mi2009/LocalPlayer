package com.localplayer.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Swipe right (play next), swipe left (add to end), Undo and a queue reorder, run against a tiny model of the
 * Media3 timeline that applies exactly the same index rules as [PlayerConnection]
 * (addMediaItem at [PlayNextOrder.insertIndex] / at the end, removeMediaItem(index) chosen by [QueueOccurrence.planUndo],
 * moveMediaItem). It checks the part we own: which entry is touched and that the current item never changes.
 */
class QueueInteractionTest {
    private data class Entry(val track: String, val occ: String, val playNext: Boolean = false)

    private class Timeline(items: List<Entry>, var current: Int) {
        val items = items.toMutableList()
        fun currentEntry() = items[current]

        fun insertNext(track: String): String {
            val id = QueueOccurrence.newId()
            val at = PlayNextOrder.insertIndex(current, items.size) { items[it].playNext }
            items.add(at, Entry(track, id, playNext = true))
            if (at <= current) current++ // never happens for insertNext, kept to mirror Media3's index shift
            return id
        }

        fun append(track: String): String {
            val id = QueueOccurrence.newId()
            items.add(Entry(track, id))
            return id
        }

        /** Same decision as PlayerConnection.removeOccurrence. */
        fun undo(id: String): Boolean = when (val plan = QueueOccurrence.planUndo(items.map { it.occ }, current, id)) {
            is QueueOccurrence.UndoPlan.Remove -> {
                items.removeAt(plan.index)
                if (plan.index < current) current--
                true
            }
            else -> false
        }

        fun move(from: Int, to: Int) {
            val e = items.removeAt(from)
            items.add(to, e)
            current = when {
                current == from -> to
                from < current && to >= current -> current - 1
                from > current && to <= current -> current + 1
                else -> current
            }
        }
    }

    private fun queue(vararg tracks: String, current: Int = 0) =
        Timeline(tracks.mapIndexed { i, t -> Entry(t, "seed$i") }, current)

    @Test fun swipeRightInsertsRightAfterTheCurrentSongAndKeepsTheCurrentOne() {
        val q = queue("A", "B", "C", current = 1)
        val playing = q.currentEntry()
        q.insertNext("X")
        assertEquals(listOf("A", "B", "X", "C"), q.items.map { it.track })
        assertEquals(playing, q.currentEntry())
    }

    @Test fun twoSwipesRightKeepTheirOrder() {
        val q = queue("A", "B", current = 0)
        q.insertNext("X")
        q.insertNext("Y")
        assertEquals(listOf("A", "X", "Y", "B"), q.items.map { it.track })
    }

    @Test fun swipeLeftAppendsAtTheEnd() {
        val q = queue("A", "B", current = 0)
        q.append("X")
        assertEquals(listOf("A", "B", "X"), q.items.map { it.track })
    }

    @Test fun undoRemovesExactlyTheEntryOfThatGestureEvenWithDuplicates() {
        val q = queue("A", "B", current = 0)
        val first = q.append("B")      // the same file as an existing entry
        val second = q.insertNext("B") // and once more as the next song
        val playing = q.currentEntry()
        assertTrue(q.undo(first))
        assertEquals(listOf("A", "B", "B"), q.items.map { it.track })
        assertTrue(q.items.none { it.occ == first })
        assertTrue(q.items.any { it.occ == second })
        assertEquals(playing, q.currentEntry())
    }

    @Test fun undoAfterTheQueueWasReorderedStillFindsTheRightEntry() {
        val q = queue("A", "B", "C", current = 0)
        val id = q.append("X")                    // A B C X
        q.move(from = 3, to = 1)                  // A X B C  (drag-and-drop)
        val playing = q.currentEntry()
        assertTrue(q.undo(id))
        assertEquals(listOf("A", "B", "C"), q.items.map { it.track })
        assertEquals(playing, q.currentEntry())
    }

    @Test fun undoWhenTheEntryAlreadyStartedPlayingChangesNothing() {
        val q = queue("A", "B", current = 0)
        val id = q.insertNext("X")
        q.current = 1 // playback advanced to X
        val before = q.items.toList()
        assertFalse(q.undo(id))
        assertEquals(before, q.items)
        assertEquals("X", q.currentEntry().track)
    }

    @Test fun undoWhenTheEntryWasRemovedMeanwhileIsANoOp() {
        val q = queue("A", "B", current = 0)
        val id = q.append("X")
        q.items.removeAt(q.items.indexOfFirst { it.occ == id }) // removed with the queue's own "-" button
        val before = q.items.toList()
        assertFalse(q.undo(id))
        assertEquals(before, q.items)
    }

    @Test fun undoOfAnEntryBeforeTheCurrentOneShiftsTheIndexSoTheSameSongKeepsPlaying() {
        val q = queue("A", "B", "C", current = 2)
        val id = "early"
        q.items.add(1, Entry("X", id))
        q.current = 3
        val playing = q.currentEntry()
        assertTrue(q.undo(id))
        assertEquals(playing, q.currentEntry())
        assertEquals(listOf("A", "B", "C"), q.items.map { it.track })
    }

    @Test fun everyFreshOccurrenceIdIsDifferent() {
        val ids = (1..500).map { QueueOccurrence.newId() }.toSet()
        assertEquals(500, ids.size)
    }
}
