package com.localplayer.music.playback

/**
 * Remembers, per queue OCCURRENCE, whether playback has ever been started for an entry that was added to an
 * empty queue (so it was prepared but not played). Undo may remove such an entry even though it is the current
 * one, but never once it has started, however briefly and wherever the position is now.
 *
 * It does not look at the position or the pause state: those cannot tell "never played" from "played, paused".
 * It is forgotten whenever the connection to the player is abandoned, which can only make Undo stricter.
 */
class StartTracker {
    private val notStarted = HashSet<String>()

    fun onAddedToEmptyQueue(occurrenceId: String) { notStarted.add(occurrenceId) }

    /** Playback was requested or began for the current occurrence. */
    fun onPlaybackStarted(occurrenceId: String?) { if (occurrenceId != null) notStarted.remove(occurrenceId) }

    fun neverStarted(occurrenceId: String): Boolean = occurrenceId in notStarted

    fun forget(occurrenceId: String) { notStarted.remove(occurrenceId) }

    fun clear() { notStarted.clear() }
}
