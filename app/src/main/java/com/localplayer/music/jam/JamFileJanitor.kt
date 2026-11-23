package com.localplayer.music.jam

import java.io.File

/**
 * Remembers the temporary file behind each Jam song of the queue and says which files are no longer needed:
 * those whose queue entry was seen in the queue and is gone now (played and removed, or removed by hand).
 * An entry is only judged after it has been seen once, because a new entry reaches the published queue a moment
 * after it was added. Pure (no Android), unit-tested.
 */
class JamFileJanitor {
    private val files = HashMap<String, File>() // queue entry (occurrence) id -> file
    private val seen = HashSet<String>()

    fun track(occurrenceId: String, file: File) {
        files[occurrenceId] = file
    }

    /** [queueIds] = the occurrence ids of the queue now. Returns the files to delete (and forgets them). */
    fun onQueue(queueIds: Collection<String?>): List<File> {
        val present = queueIds.filterNotNull().toSet()
        seen += files.keys.filter { it in present }
        val gone = files.keys.filter { it in seen && it !in present }
        val result = gone.mapNotNull { files[it] }
        gone.forEach {
            files.remove(it)
            seen.remove(it)
        }
        return result
    }

    fun clear() {
        files.clear()
        seen.clear()
    }
}
