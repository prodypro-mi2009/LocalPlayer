package com.localplayer.music.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The decisions behind the mini-player dismiss Undo and the Clear Undo, modelled on queue changes. */
class UndoGuardsTest {
    /** Dismiss = the queue becomes empty (no epoch bump); the token is the epoch right after it. */
    private fun dismissed(): Pair<QueueEpoch, Long> {
        val e = QueueEpoch()
        e.onPlaylistChanged(0)
        return e to e.value
    }

    @Test fun undoImmediatelyAfterDismissIsAccepted() {
        val (e, token) = dismissed()
        assertTrue(e.canRestoreDismissed(itemCountNow = 0, tokenEpoch = token))
    }

    @Test fun dismissThenSelectAnotherTrackThenUndoIsRefused() {
        val (e, token) = dismissed()
        e.onPlaylistChanged(40) // tapped a song: a new queue replaced the empty one
        assertFalse(e.canRestoreDismissed(40, token))
    }

    @Test fun dismissThenAddASongThenUndoIsRefused() {
        val (e, token) = dismissed()
        e.onPlaylistChanged(1)
        assertFalse(e.canRestoreDismissed(1, token))
    }

    @Test fun dismissThenNewerChangeThatEmptiesTheQueueAgainIsStillRefused() {
        val (e, token) = dismissed()
        e.onPlaylistChanged(1) // user added a song...
        e.onPlaylistChanged(0) // ...and removed it again: that was a newer intentional change
        assertFalse(e.canRestoreDismissed(0, token))
    }

    @Test fun anAddThatHasNotYetBeenReportedStillFailsTheEmptyCheck() {
        val (e, token) = dismissed()
        // the controller already holds the new song but its event has not been delivered yet
        assertFalse(e.canRestoreDismissed(itemCountNow = 1, tokenEpoch = token))
    }

    @Test fun anAcceptedUndoCannotBeUsedTwice() {
        val (e, token) = dismissed()
        assertTrue(e.canRestoreDismissed(0, token))
        e.onPlaylistChanged(12) // the restore itself fills the queue
        assertFalse(e.canRestoreDismissed(0, token))
    }

    @Test fun clearUndoIsValidOnlyForTheQueueClearLeftBehind() {
        val left = listOf<String?>("cur")
        assertTrue(UndoGuards.clearUndoValid(left, listOf("cur")))
    }

    @Test fun clearThenAddASongThenUndoIsRefused() {
        assertFalse(UndoGuards.clearUndoValid(listOf("cur"), listOf("cur", "added")))
    }

    @Test fun clearThenRemoveOrReplaceIsRefused() {
        assertFalse(UndoGuards.clearUndoValid(listOf("cur"), emptyList()))
        assertFalse(UndoGuards.clearUndoValid(listOf("cur"), listOf("other")))
    }

    @Test fun clearUpcomingTokenKeepsTheHistoryItDidNotRemove() {
        // Repeat OFF: history stays, so the token is that whole remaining queue
        val left = listOf<String?>("h1", "h2", "cur")
        assertTrue(UndoGuards.clearUndoValid(left, listOf("h1", "h2", "cur")))
        assertFalse(UndoGuards.clearUndoValid(left, listOf("h2", "h1", "cur"))) // reordered meanwhile
    }
}
