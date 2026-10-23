package com.localplayer.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartTrackerTest {
    /** Mirrors QueueOps.removeOccurrence's decision with the tracker in place of position heuristics. */
    private fun undo(ids: List<String?>, current: Int, id: String, tracker: StartTracker, playing: Boolean = false, playWhenReady: Boolean = false) =
        QueueOccurrence.planUndo(ids, current, id, tracker.neverStarted(id) && !playing && !playWhenReady)

    @Test fun preparedButNeverPlayedCanBeUndone() {
        val t = StartTracker(); t.onAddedToEmptyQueue("a")
        assertEquals(QueueOccurrence.UndoPlan.Remove(0), undo(listOf("a"), 0, "a", t))
    }

    @Test fun playThenImmediatePauseCannotBeUndone() {
        val t = StartTracker(); t.onAddedToEmptyQueue("a")
        t.onPlaybackStarted("a") // play requested, then paused within a few ms: position and state look "unstarted"
        assertEquals(QueueOccurrence.UndoPlan.IsPlayingNow, undo(listOf("a"), 0, "a", t, playing = false, playWhenReady = false))
    }

    @Test fun seekBackNearTheStartAndPauseCannotBeUndone() {
        val t = StartTracker(); t.onAddedToEmptyQueue("a")
        t.onPlaybackStarted("a") // it played; the user then seeked to 0 and paused: position is irrelevant
        assertFalse(t.neverStarted("a"))
    }

    @Test fun onlyTheStartedOccurrenceIsAffectedDuplicatesAreIndependent() {
        val t = StartTracker(); t.onAddedToEmptyQueue("first"); t.onAddedToEmptyQueue("copy")
        t.onPlaybackStarted("first")
        assertFalse(t.neverStarted("first"))
        assertTrue(t.neverStarted("copy"))
        // Undo of the copy (not current) removes exactly the copy, never the other occurrence of the same file
        assertEquals(QueueOccurrence.UndoPlan.Remove(1), undo(listOf("first", "copy"), 0, "copy", t))
    }

    @Test fun playRequestedWhileTheStateShowsPlayWhenReadyBlocksUndoToo() {
        val t = StartTracker(); t.onAddedToEmptyQueue("a")
        assertEquals(QueueOccurrence.UndoPlan.IsPlayingNow, undo(listOf("a"), 0, "a", t, playWhenReady = true))
    }

    @Test fun aCurrentOccurrenceThatChangedSinceTheUndoBeganIsNotRemovedAsUnstarted() {
        val t = StartTracker(); t.onAddedToEmptyQueue("a")
        // the queue was replaced: "a" is gone -> nothing to undo
        assertEquals(QueueOccurrence.UndoPlan.AlreadyGone, undo(listOf("x", "y"), 0, "a", t))
        // another occurrence became current and started: "a" is not current any more -> plain targeted removal
        t.onPlaybackStarted("x")
        assertEquals(QueueOccurrence.UndoPlan.Remove(1), undo(listOf("x", "a"), 0, "a", t))
    }

    @Test fun releasingTheConnectionMakesUndoStricter() {
        val t = StartTracker(); t.onAddedToEmptyQueue("a")
        t.clear() // connection abandoned: nothing may be assumed any more
        assertFalse(t.neverStarted("a"))
        assertEquals(QueueOccurrence.UndoPlan.IsPlayingNow, undo(listOf("a"), 0, "a", t))
    }

    @Test fun anEntryThatWasNeverAddedToAnEmptyQueueIsNotTracked() {
        assertFalse(StartTracker().neverStarted("zzz"))
    }
}
