package com.localplayer.music.playback.session

/** Pure decision of what to restore, given which saved songs still exist in the library. */
object SessionRestorePlan {
    data class Plan(val entries: List<SavedEntry>, val startIndex: Int, val positionMs: Long)

    fun plan(saved: SavedSession, available: Set<String>): Plan? {
        val all = saved.queue.entries
        val kept = all.filter { it.trackUri in available }
        if (kept.isEmpty()) return null
        val state = saved.state ?: return Plan(kept, 0, 0L)

        var original = all.indexOfFirst { it.occurrenceId == state.currentOccurrenceId }
        if (original < 0 && state.queueGeneration == saved.queue.generation && state.currentIndex in all.indices) {
            original = state.currentIndex
        }
        if (original < 0) return Plan(kept, 0, 0L)

        val current = all[original]
        if (current.trackUri in available) {
            val index = kept.indexOfFirst { it.occurrenceId == current.occurrenceId }
            return Plan(kept, index.coerceAtLeast(0), state.positionMs.coerceAtLeast(0L))
        }
        // The saved current song is gone: continue with the next surviving entry, else the last one before it.
        val next = (original + 1 until all.size).firstOrNull { all[it].trackUri in available }
            ?: (original - 1 downTo 0).first { all[it].trackUri in available }
        val index = kept.indexOfFirst { it.occurrenceId == all[next].occurrenceId }
        return Plan(kept, index.coerceAtLeast(0), 0L)
    }
}
