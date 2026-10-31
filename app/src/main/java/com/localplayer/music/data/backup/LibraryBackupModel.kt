package com.localplayer.music.data.backup

/**
 * Library data in a backup: playlists, favourites, listening history/statistics and the user's library rules.
 * Pure Kotlin (no Android, no database), so the format and the song matching are tested on the JVM.
 *
 * Songs are NOT stored by their database id. Every song that appears anywhere in the backup gets one entry in a
 * table of [TrackRef]s (uri + file name + title + artist + duration + size); the sections point into that table.
 * On import a song is found again by [TrackMatcher], which also works on another phone where the URIs differ.
 */
class TrackRef(
    val uri: String,
    val name: String?,
    val title: String?,
    val artist: String?,
    val durationMs: Long,
    val sizeBytes: Long,
)

class PlaylistItemData(val track: Int, val position: Int, val addedAt: Long)

class PlaylistData(
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceFolderKey: String?,
    val sourceFolderLabel: String?,
    val sourceIncludesSubfolders: Boolean,
    /** Entry name of the cover picture inside the archive (assets/covers/<name>), or null. */
    val cover: String?,
    val items: List<PlaylistItemData>,
)

class FavoriteData(val track: Int, val addedAt: Long)
class HistoryData(val track: Int, val playedAt: Long)
class DayData(val day: Int, val track: Int, val listenedMs: Long, val plays: Int, val lastListenedAt: Long)
class OverrideData(val track: Int, val primaryArtist: String)
class HiddenData(val track: Int, val hiddenAt: Long)
class ExcludedData(val prefix: String, val excludedAt: Long)

class LibraryData(
    val tracks: List<TrackRef>,
    val playlists: List<PlaylistData>,
    val favorites: List<FavoriteData>,
    val history: List<HistoryData>,
    val days: List<DayData>,
    val overrides: List<OverrideData>,
    val hidden: List<HiddenData>,
    val excluded: List<ExcludedData>,
) {
    val isEmpty: Boolean
        get() = playlists.isEmpty() && favorites.isEmpty() && history.isEmpty() && days.isEmpty() &&
            overrides.isEmpty() && hidden.isEmpty() && excluded.isEmpty()
}

/** The parts of the library data the user can switch on or off before importing. */
object LibrarySection {
    const val PLAYLISTS = "playlists"
    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val RULES = "rules"
    val all = listOf(PLAYLISTS, FAVORITES, HISTORY, RULES)
}

object LibraryBackupCodec {
    const val FORMAT = "localplayer-library"
    const val VERSION = 1
    const val MAX_ITEMS = 2_000_000

    fun encode(data: LibraryData, appVersion: String, exportedAtMs: Long): String {
        fun ref(r: TrackRef) = listOf(r.uri, r.name, r.title, r.artist, r.durationMs, r.sizeBytes)
        val root = linkedMapOf<String, Any?>(
            "format" to FORMAT,
            "version" to VERSION,
            "appVersion" to appVersion,
            "exportedAt" to exportedAtMs,
            "tracks" to data.tracks.map(::ref),
            "playlists" to data.playlists.map { p ->
                linkedMapOf<String, Any?>(
                    "name" to p.name,
                    "createdAt" to p.createdAt,
                    "updatedAt" to p.updatedAt,
                    "sourceFolderKey" to p.sourceFolderKey,
                    "sourceFolderLabel" to p.sourceFolderLabel,
                    "sourceIncludesSubfolders" to p.sourceIncludesSubfolders,
                    "cover" to p.cover,
                    "items" to p.items.map { listOf(it.track, it.position, it.addedAt) },
                )
            },
            "favorites" to data.favorites.map { listOf(it.track, it.addedAt) },
            "history" to data.history.map { listOf(it.track, it.playedAt) },
            "days" to data.days.map { listOf(it.day, it.track, it.listenedMs, it.plays, it.lastListenedAt) },
            "overrides" to data.overrides.map { listOf(it.track, it.primaryArtist) },
            "hidden" to data.hidden.map { listOf(it.track, it.hiddenAt) },
            "excluded" to data.excluded.map { listOf(it.prefix, it.excludedAt) },
        )
        return MiniJson.stringify(root, pretty = false)
    }

    @Suppress("UNCHECKED_CAST")
    fun decode(text: String): LibraryData {
        val root = try { MiniJson.parseObject(text) } catch (e: MiniJson.JsonException) { throw BackupFormat.InvalidBackup("library.json is damaged") }
        if (root["format"] != FORMAT) throw BackupFormat.InvalidBackup("library.json is not LocalPlayer library data")
        val version = (root["version"] as? Double)?.toInt() ?: throw BackupFormat.InvalidBackup("library.json has no version")
        if (version < 1 || version > VERSION) throw BackupFormat.InvalidBackup("Unsupported library data version $version")

        fun list(key: String): List<Any?> = (root[key] as? List<Any?>).orEmpty().also {
            if (it.size > MAX_ITEMS) throw BackupFormat.InvalidBackup("library.json is too large")
        }
        fun row(v: Any?): List<Any?>? = v as? List<Any?>
        fun long(v: Any?): Long? = (v as? Double)?.takeIf { !it.isNaN() && !it.isInfinite() }?.toLong()
        fun int(v: Any?): Int? = long(v)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        fun str(v: Any?): String? = v as? String

        val tracks = list("tracks").mapNotNull { v ->
            val r = row(v) ?: return@mapNotNull null
            val uri = str(r.getOrNull(0))?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            TrackRef(uri, str(r.getOrNull(1)), str(r.getOrNull(2)), str(r.getOrNull(3)), long(r.getOrNull(4)) ?: 0L, long(r.getOrNull(5)) ?: 0L)
        }
        // A section row that points outside the table is dropped, so a damaged file can never index out of range.
        val n = tracks.size
        fun valid(i: Int?): Int? = i?.takeIf { it in 0 until n }

        val playlists = list("playlists").mapNotNull { v ->
            val m = v as? Map<String, Any?> ?: return@mapNotNull null
            val name = str(m["name"])?.trim()?.takeIf { it.isNotEmpty() && it.length <= 200 } ?: return@mapNotNull null
            val cover = str(m["cover"])?.takeIf { BackupFormat.isCoverName(it) }
            val items = (m["items"] as? List<Any?>).orEmpty().mapNotNull { x ->
                val r = row(x) ?: return@mapNotNull null
                val t = valid(int(r.getOrNull(0))) ?: return@mapNotNull null
                PlaylistItemData(t, int(r.getOrNull(1)) ?: 0, long(r.getOrNull(2)) ?: 0L)
            }
            PlaylistData(
                name, long(m["createdAt"]) ?: 0L, long(m["updatedAt"]) ?: 0L,
                str(m["sourceFolderKey"]), str(m["sourceFolderLabel"]),
                (m["sourceIncludesSubfolders"] as? Boolean) ?: true, cover, items.sortedBy { it.position },
            )
        }
        val favorites = list("favorites").mapNotNull { v ->
            val r = row(v) ?: return@mapNotNull null
            FavoriteData(valid(int(r.getOrNull(0))) ?: return@mapNotNull null, long(r.getOrNull(1)) ?: 0L)
        }
        val history = list("history").mapNotNull { v ->
            val r = row(v) ?: return@mapNotNull null
            HistoryData(valid(int(r.getOrNull(0))) ?: return@mapNotNull null, long(r.getOrNull(1)) ?: return@mapNotNull null)
        }
        val days = list("days").mapNotNull { v ->
            val r = row(v) ?: return@mapNotNull null
            DayData(
                int(r.getOrNull(0)) ?: return@mapNotNull null, valid(int(r.getOrNull(1))) ?: return@mapNotNull null,
                long(r.getOrNull(2)) ?: 0L, int(r.getOrNull(3)) ?: 0, long(r.getOrNull(4)) ?: 0L,
            )
        }
        val overrides = list("overrides").mapNotNull { v ->
            val r = row(v) ?: return@mapNotNull null
            val artist = str(r.getOrNull(1))?.trim()?.takeIf { it.isNotEmpty() && it.length <= 300 } ?: return@mapNotNull null
            OverrideData(valid(int(r.getOrNull(0))) ?: return@mapNotNull null, artist)
        }
        val hidden = list("hidden").mapNotNull { v ->
            val r = row(v) ?: return@mapNotNull null
            HiddenData(valid(int(r.getOrNull(0))) ?: return@mapNotNull null, long(r.getOrNull(1)) ?: 0L)
        }
        val excluded = list("excluded").mapNotNull { v ->
            val r = row(v) ?: return@mapNotNull null
            ExcludedData(str(r.getOrNull(0))?.takeIf { it.isNotEmpty() && it.length <= 1000 } ?: return@mapNotNull null, long(r.getOrNull(1)) ?: 0L)
        }
        return LibraryData(tracks, playlists, favorites, history, days, overrides, hidden, excluded)
    }
}

/** A song of THIS library, as far as matching needs to know it. */
class CurrentTrack(
    val uri: String,
    val name: String?,
    val title: String?,
    val artist: String?,
    val durationMs: Long,
    val sizeBytes: Long,
)

/**
 * Finds the song of the current library that a backup entry stands for. In order:
 *  1. the same URI - but only when the file name or title also agrees (on another phone the same URI can be a
 *     different song);
 *  2. file name + size, when exactly one song has them;
 *  3. title + artist + duration (within 2 s), when exactly one song has them;
 *  4. file name + duration (within 2 s), when exactly one song has them.
 * Anything not found, or ambiguous, is "not matched" and is skipped (and reported), never guessed.
 */
class TrackMatcher(current: List<CurrentTrack>) {
    private val byUri = HashMap<String, CurrentTrack>(current.size * 2)
    private val byName = HashMap<String, MutableList<CurrentTrack>>()
    private val byTitle = HashMap<String, MutableList<CurrentTrack>>()

    init {
        for (t in current) {
            byUri[t.uri] = t
            norm(t.name)?.let { byName.getOrPut(it) { ArrayList(1) } += t }
            norm(t.title)?.let { byTitle.getOrPut(it) { ArrayList(1) } += t }
        }
    }

    /** The URI of the matching current song, or null. */
    fun match(ref: TrackRef): String? {
        val refName = norm(ref.name)
        val refTitle = norm(ref.title)

        byUri[ref.uri]?.let { c ->
            val agrees = (refName != null && refName == norm(c.name)) || (refTitle != null && refTitle == norm(c.title))
            if (agrees) return c.uri
        }
        if (refName != null) {
            val named = byName[refName].orEmpty()
            if (ref.sizeBytes > 0) named.filter { it.sizeBytes == ref.sizeBytes }.singleOrNull()?.let { return it.uri }
        }
        if (refTitle != null) {
            val refArtist = norm(ref.artist)
            val hit = byTitle[refTitle].orEmpty()
                .filter { norm(it.artist) == refArtist && near(it.durationMs, ref.durationMs) }
                .singleOrNull()
            if (hit != null) return hit.uri
        }
        if (refName != null) {
            byName[refName].orEmpty().filter { near(it.durationMs, ref.durationMs) }.singleOrNull()?.let { return it.uri }
        }
        return null
    }

    private fun near(a: Long, b: Long) = a > 0 && b > 0 && kotlin.math.abs(a - b) <= 2_000

    private fun norm(s: String?): String? =
        s?.trim()?.lowercase()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }
}

/** What an import of the library data would do, per section (shown in the preview before anything is written). */
class SectionPreview(
    val section: String,
    /** Number of entries in the backup (playlists, favourites, plays, rules). */
    val total: Int,
    /** Entries that need a song but whose song could not be found in this library. */
    val unmatched: Int,
)

object LibraryPlan {
    /** Maps every backup song index to the current URI (null = not found). */
    fun resolve(data: LibraryData, matcher: TrackMatcher): List<String?> = data.tracks.map { matcher.match(it) }

    fun preview(data: LibraryData, resolved: List<String?>): List<SectionPreview> {
        fun miss(tracks: List<Int>) = tracks.count { resolved[it] == null }
        val out = ArrayList<SectionPreview>()
        if (data.playlists.isNotEmpty()) {
            val items = data.playlists.flatMap { p -> p.items.map { it.track } }
            out += SectionPreview(LibrarySection.PLAYLISTS, data.playlists.size, miss(items))
        }
        if (data.favorites.isNotEmpty()) out += SectionPreview(LibrarySection.FAVORITES, data.favorites.size, miss(data.favorites.map { it.track }))
        if (data.history.isNotEmpty() || data.days.isNotEmpty()) {
            val all = data.history.map { it.track } + data.days.map { it.track }
            out += SectionPreview(LibrarySection.HISTORY, data.history.size + data.days.size, miss(all))
        }
        val ruleTracks = data.overrides.map { it.track } + data.hidden.map { it.track }
        val ruleCount = ruleTracks.size + data.excluded.size
        if (ruleCount > 0) out += SectionPreview(LibrarySection.RULES, ruleCount, miss(ruleTracks))
        return out
    }
}
