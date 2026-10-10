package com.localplayer.music.playback

import java.util.UUID

/**
 * Pure rules for "add to queue" + Undo, kept free of Android types so they can be unit tested.
 *
 * Every queue entry added by the swipe gesture carries its own occurrence id. Undo removes exactly that
 * entry (never "the first entry of the same file"), and never the entry that is playing right now.
 */
object QueueOccurrence {
    fun newId(): String = UUID.randomUUID().toString()

    sealed interface UndoPlan {
        /** Remove the single entry at [index] (a targeted `removeMediaItem`, nothing else touched). */
        data class Remove(val index: Int) : UndoPlan
        /** The entry is gone already (user removed it or the queue changed): do nothing. */
        object AlreadyGone : UndoPlan
        /** The entry became the playing one meanwhile: removing it would interrupt playback, so leave it. */
        object IsPlayingNow : UndoPlan
    }

    /** [occurrenceIds] = occurrence id of every queue entry in order (null for entries not added by the gesture). */
    fun planUndo(occurrenceIds: List<String?>, currentIndex: Int, occurrenceId: String): UndoPlan {
        val index = occurrenceIds.indexOf(occurrenceId)
        return when {
            index < 0 -> UndoPlan.AlreadyGone
            index == currentIndex -> UndoPlan.IsPlayingNow
            else -> UndoPlan.Remove(index)
        }
    }
}
