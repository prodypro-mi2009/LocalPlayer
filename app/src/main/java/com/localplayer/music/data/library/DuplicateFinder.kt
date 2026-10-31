package com.localplayer.music.data.library

import kotlin.math.abs

/** What duplicate detection needs to know about one song. */
class DupTrack(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val name: String?,
    val lastModified: Long,
)

/** Songs that look like the same recording. [keep] is the one to leave visible (the biggest file = usually the best quality). */
class DupGroup(val tracks: List<DupTrack>, val keep: DupTrack, val sameFile: Boolean)

/**
 * Finds duplicates without reading any file: same title + artist and a duration within 2 seconds. A group is
 * "same file" when all sizes are equal too. Different versions (live, remix) differ in duration or title and are left alone.
 * Pure Kotlin, tested on the JVM. The app never deletes anything because of this: the extra copies are only HIDDEN
 * (and can be un-hidden in Settings -> Library -> Hidden songs).
 */
object DuplicateFinder {
    private const val TOLERANCE_MS = 2_000L

    fun find(tracks: List<DupTrack>): List<DupGroup> {
        val buckets = HashMap<String, MutableList<DupTrack>>()
        for (t in tracks) {
            val title = norm(t.title) ?: continue
            buckets.getOrPut(title + "\u0000" + (norm(t.artist) ?: "")) { ArrayList() } += t
        }
        val groups = ArrayList<DupGroup>()
        for (list in buckets.values) {
            if (list.size < 2) continue
            val sorted = list.sortedBy { it.durationMs }
            var cluster = ArrayList<DupTrack>()
            fun flush() {
                if (cluster.size >= 2) groups += toGroup(cluster)
                cluster = ArrayList()
            }
            for (t in sorted) {
                if (cluster.isNotEmpty() && abs(t.durationMs - cluster.last().durationMs) > TOLERANCE_MS) flush()
                cluster += t
            }
            flush()
        }
        return groups.sortedWith(compareBy({ norm(it.keep.title) }, { norm(it.keep.artist) }))
    }

    private fun toGroup(cluster: List<DupTrack>): DupGroup {
        val keep = cluster.sortedWith(compareByDescending<DupTrack> { it.sizeBytes }.thenBy { it.lastModified }.thenBy { it.id }).first()
        val sameFile = cluster.all { it.sizeBytes == keep.sizeBytes } && keep.sizeBytes > 0
        return DupGroup(cluster, keep, sameFile)
    }

    private fun norm(s: String?): String? =
        s?.trim()?.lowercase()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }
}
