package com.localplayer.music.data.library

/**
 * Pure decision logic for hidden songs, library folders and folder playlists.
 * No Android and no database in here, so every rule below has a plain JVM unit test.
 */

/** What the UI needs to know about one folder of the library. */
data class FolderEntry(
    val key: String,
    /** Songs currently indexed directly in this folder (not in subfolders). */
    val trackCount: Int,
    /** True when the folder, or a parent of it, is excluded from the library. */
    val excluded: Boolean,
    /** The excluded folder responsible: [key] itself, or a parent. Null when not excluded. */
    val excludedBy: String?,
)

object FolderOverview {
    /**
     * [counts]: folder key -> songs in it. [excluded]: the folder keys the user excluded.
     * An excluded folder also excludes everything below it; a subfolder of an excluded folder is shown as
     * excluded "by" its parent (it cannot be switched on on its own). Excluded folders that currently hold no
     * indexed songs are still listed so the user can switch them back on.
     */
    fun build(counts: Map<String, Int>, excluded: Set<String>): List<FolderEntry> {
        val keys = (counts.keys + excluded).distinct()
            .sortedWith(compareBy<String, String>(String.CASE_INSENSITIVE_ORDER) { it }.thenBy { it })
        return keys.map { key ->
            val by = excluded
                .filter { FolderKeys.covers(it, key) }
                .minByOrNull { it.length } // the outermost excluded folder
            FolderEntry(key = key, trackCount = counts[key] ?: 0, excluded = by != null, excludedBy = by)
        }
    }
}

/**
 * Hiding is stored against the song's URI. If the file is moved or re-registered the URI can change, so
 * a hidden entry whose track no longer exists is matched to a new track with the same file name and size
 * (the same file at its new place) instead of being lost and letting the song reappear.
 */
object HiddenReconciler {
    /** Entries are dropped after this long if their file never came back (e.g. an SD card that stays out). */
    const val GRACE_MS = 30L * 24 * 60 * 60 * 1000

    data class Orphan(val uri: String, val hiddenAt: Long, val displayName: String?, val sizeBytes: Long?)

    data class Candidate(val uri: String, val displayName: String, val sizeBytes: Long)

    data class Plan(
        /** old URI -> new URI: the same file, found again under a new URI. */
        val rekey: Map<String, String>,
        /** Orphans to delete (file gone for longer than [GRACE_MS]). */
        val drop: List<String>,
    )

    fun plan(orphans: List<Orphan>, candidates: List<Candidate>, now: Long): Plan {
        val byIdentity = candidates.groupBy { it.displayName to it.sizeBytes }
        // A candidate may be claimed by one orphan only; an ambiguous match (several files with the same
        // name and size, or several orphans wanting the same file) is not guessed.
        val wanted = HashMap<String, MutableList<String>>() // candidate uri -> orphans wanting it
        val choice = HashMap<String, String>()               // orphan uri -> candidate uri
        for (orphan in orphans) {
            val name = orphan.displayName ?: continue
            val size = orphan.sizeBytes ?: continue
            val matches = byIdentity[name to size] ?: continue
            if (matches.size != 1) continue
            val target = matches.first().uri
            choice[orphan.uri] = target
            wanted.getOrPut(target) { mutableListOf() }.add(orphan.uri)
        }
        val rekey = LinkedHashMap<String, String>()
        for ((orphanUri, target) in choice) {
            if (wanted[target]?.size == 1) rekey[orphanUri] = target
        }
        val drop = orphans
            .filter { it.uri !in rekey && now - it.hiddenAt > GRACE_MS }
            .map { it.uri }
        return Plan(rekey, drop)
    }
}

/** Keeps a playlist that mirrors a folder in step with that folder. */
object FolderPlaylistSync {
    data class Diff(
        /** URIs to append, in folder order. */
        val add: List<String>,
        val remove: List<String>,
        /**
         * True when the folder now yields no songs although the playlist has some. That is far more likely a
         * folder that is temporarily not readable (unmounted card, revoked access) than a deliberately emptied
         * one, so nothing is removed and the user is told instead.
         */
        val folderLooksUnavailable: Boolean,
    ) {
        val changed: Boolean get() = add.isNotEmpty() || remove.isNotEmpty()
    }

    /** [current]: the playlist's songs in order. [desired]: the folder's songs (library order). */
    fun diff(current: List<String>, desired: List<String>): Diff {
        if (desired.isEmpty() && current.isNotEmpty()) return Diff(emptyList(), emptyList(), true)
        val currentSet = current.toHashSet()
        val desiredSet = desired.toHashSet()
        val add = desired.filter { it !in currentSet }.distinct()
        val remove = current.filter { it !in desiredSet }
        return Diff(add, remove, false)
    }
}
