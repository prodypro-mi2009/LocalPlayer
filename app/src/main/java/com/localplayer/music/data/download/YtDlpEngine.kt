package com.localplayer.music.data.download

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/**
 * One song found at a link. [artist] may be empty (YouTube did not say). [trackNumber] is the song's position in the
 * YouTube playlist (for an album that IS the track number; null for a single video). [album] is the album name when
 * YouTube names one (or when the link is a YouTube Music album), otherwise empty.
 */
data class FoundSong(
    val id: String,
    val title: String,
    val artist: String,
    val trackNumber: Int? = null,
    val album: String = "",
)

/** What YouTube itself knows about a downloaded song (empty = unknown). */
data class InfoMeta(val album: String = "", val year: String = "", val trackNumber: Int? = null)

/** A finished download: the MP3 plus whatever YouTube told yt-dlp about it. */
class DownloadedMp3(val file: File, val info: InfoMeta)

/**
 * The only place that talks to yt-dlp (youtubedl-android: Python + FFmpeg bundled as native libraries).
 * Everything here blocks, so callers run it on a background dispatcher.
 */
object YtDlpEngine {
    private val lock = Any()
    @Volatile private var ready = false

    fun ensureInit(context: Context) {
        if (ready) return
        synchronized(lock) {
            if (ready) return
            YoutubeDL.getInstance().init(context.applicationContext)
            FFmpeg.getInstance().init(context.applicationContext)
            ready = true
        }
    }

    /** Lists the song(s) behind a video, album or playlist link without downloading anything. */
    fun fetch(context: Context, url: String): List<FoundSong> {
        ensureInit(context)
        // Only when the user turned "other ways to connect" on, a failed read is tried again as another YouTube client.
        val clients: List<String?> = if (YtDlpExtras.fallbackClients(context)) listOf<String?>(null) + FETCH_CLIENTS else listOf(null)
        var lastError: Throwable? = null
        for (client in clients) {
            val request = YoutubeDLRequest(url.trim())
            request.addOption("--flat-playlist")
            request.addOption("--ignore-errors")
            request.addOption("--no-warnings")
            for ((option, value) in YtDlpExtras.cookieOptions(context)) request.addOption(option, value)
            if (client != null) request.addOption("--extractor-args", "youtube:player_client=$client")
            // id, title, artist, position in the playlist, playlist title, playlist id, album (NA = unknown)
            request.addOption(
                "--print",
                "%(id)s\t%(title)s\t%(artist,uploader,channel)s\t%(playlist_index)s\t%(playlist_title)s\t%(playlist_id)s\t%(album)s",
            )
            try {
                return parseListing(YoutubeDL.getInstance().execute(request, null, null).out)
            } catch (e: InterruptedException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (classify(e) == FailureKind.DRM) break // another client cannot remove DRM
            }
        }
        throw lastError ?: IllegalStateException("yt-dlp could not read the link")
    }

    /** The first [count] YouTube results for [query] (nothing is downloaded). */
    fun search(context: Context, query: String, count: Int = 5): List<com.localplayer.music.data.spotify.YoutubeHit> {
        ensureInit(context)
        val request = YoutubeDLRequest("ytsearch$count:${query.trim()}")
        request.addOption("--flat-playlist")
        request.addOption("--ignore-errors")
        request.addOption("--no-warnings")
        for ((option, value) in YtDlpExtras.cookieOptions(context)) request.addOption(option, value)
        request.addOption("--print", "%(id)s\t%(title)s\t%(channel,uploader)s\t%(duration)s")
        return parseSearch(YoutubeDL.getInstance().execute(request, null, null).out)
    }

    /** Output of [search]: tab-separated id, title, channel, duration in seconds (NA = unknown). */
    internal fun parseSearch(out: String): List<com.localplayer.music.data.spotify.YoutubeHit> =
        out.lineSequence()
            .map { it.trim('\r') }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split('\t')
                val id = parts.getOrNull(0)?.trim().orEmpty()
                if (id.isEmpty() || id == "NA" || !SAFE_ID.matches(id)) return@mapNotNull null
                com.localplayer.music.data.spotify.YoutubeHit(
                    id = id,
                    title = known(parts.getOrNull(1)),
                    channel = known(parts.getOrNull(2)),
                    durationMs = ((parts.getOrNull(3)?.trim()?.toDoubleOrNull() ?: 0.0) * 1000).toLong().coerceAtLeast(0),
                )
            }
            .distinctBy { it.id }
            .toList()

    private val FETCH_CLIENTS = listOf("android_vr", "tv", "mweb", "web_safari")

    enum class FailureKind { BOT, DRM, OTHER }

    /** Which well-known YouTube refusal an error is (the message text of yt-dlp decides). */
    fun classify(t: Throwable): FailureKind {
        val text = generateSequence(t) { it.cause }.joinToString(" ") { it.message.orEmpty() }.lowercase()
        return when {
            "sign in to confirm" in text || "not a bot" in text -> FailureKind.BOT
            "[drm]" in text || "drm protect" in text -> FailureKind.DRM
            else -> FailureKind.OTHER
        }
    }

    /**
     * YouTube answers "HTTP 403" for some stream URLs depending on the client yt-dlp pretends to be, so a download is
     * tried with several client / format combinations, in this order, until one works.
     */
    private val ATTEMPTS: List<List<Pair<String, String>>> = listOf(
        listOf("-f" to "bestaudio/best"),
        listOf("-f" to "bestaudio/best", "--extractor-args" to "youtube:player_client=android_vr"),
        listOf("-f" to "234/233/bestaudio[protocol^=m3u8]/bestaudio/best", "--extractor-args" to "youtube:player_client=web_safari"),
        listOf("-f" to "bestaudio/best", "--extractor-args" to "youtube:player_client=tv"),
        listOf("-f" to "bestaudio/best", "--extractor-args" to "youtube:player_client=mweb"),
    )

    /**
     * Downloads the best audio of one video and converts it to MP3 (VBR quality 0 = the highest MP3 quality).
     * The result is a file in [dir]. If a post-processing step that only matters for the cover fails after the MP3
     * exists, the MP3 is still returned.
     */
    fun downloadMp3(context: Context, id: String, dir: File, processId: String, onProgress: (Float) -> Unit): DownloadedMp3 {
        ensureInit(context)
        dir.mkdirs()
        val target = File(dir, "$id.mp3")
        val infoFile = File(dir, "$id.info.json")
        var lastError: Throwable? = null
        for ((index, attempt) in ATTEMPTS.withIndex()) {
            target.delete()
            infoFile.delete() // yt-dlp does not overwrite an existing info file
            onProgress(0f)
            val request = YoutubeDLRequest("https://www.youtube.com/watch?v=$id")
            for ((option, value) in attempt) request.addOption(option, value)
            for ((option, value) in YtDlpExtras.cookieOptions(context)) request.addOption(option, value)
            request.addOption("-x")
            request.addOption("--audio-format", "mp3")
            request.addOption("--audio-quality", "0")
            request.addOption("--embed-thumbnail")
            request.addOption("--convert-thumbnails", "jpg")
            request.addOption("--write-info-json") // album / release year as YouTube states them (read after the download)
            request.addOption("--no-playlist")
            request.addOption("--no-mtime")
            request.addOption("--no-warnings")
            request.addOption("--retries", "3")
            request.addOption("--fragment-retries", "3")
            request.addOption("-o", File(dir, "$id.%(ext)s").absolutePath)
            try {
                YoutubeDL.getInstance().execute(request, processId) { progress, _, _ -> onProgress(progress) }
            } catch (e: YoutubeDL.CanceledException) {
                throw e
            } catch (e: InterruptedException) {
                throw e
            } catch (e: Exception) {
                // A failed cover step still leaves a complete MP3: that counts as success.
                if (!target.isFile || target.length() == 0L) {
                    lastError = e
                    continue
                }
            }
            if (target.isFile && target.length() > 0L) return DownloadedMp3(target, readInfo(infoFile))
            lastError = IllegalStateException("yt-dlp produced no MP3 (attempt ${index + 1})")
        }
        throw lastError ?: IllegalStateException("yt-dlp produced no MP3 for $id")
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    class UpdateResult(val error: String?, val alreadyLatest: Boolean)

    /** Updates the bundled yt-dlp from the latest stable GitHub release (the nightly one when the user chose that). */
    fun update(context: Context): UpdateResult = try {
        ensureInit(context)
        val nightly = YtDlpExtras.nightly(context)
        val channel = if (nightly) YoutubeDL.UpdateChannel.NIGHTLY else YoutubeDL.UpdateChannel.STABLE
        val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext, channel)
        YtDlpExtras.setInstalledChannel(context, if (nightly) "nightly" else "stable")
        UpdateResult(null, status == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE)
    } catch (t: Throwable) {
        UpdateResult(describe(t), false)
    }

    /** The release tag of the yt-dlp installed by an update (null = still the one bundled with the app). */
    fun installedVersion(context: Context): String? = try {
        YoutubeDL.getInstance().version(context.applicationContext)
    } catch (t: Throwable) {
        null
    }

    /** A short, readable reason for the screen (class name + the last lines of the message). */
    fun describe(t: Throwable): String {
        val message = t.message.orEmpty().lines().filter { it.isNotBlank() }.takeLast(4).joinToString("\n").trim()
        val root = generateSequence(t) { it.cause }.last()
        val cause = if (root !== t) "\n" + root.javaClass.simpleName + ": " + root.message.orEmpty().take(200) else ""
        val head = t.javaClass.simpleName + (if (message.isNotEmpty()) ": $message" else "")
        return (head + cause).take(600)
    }

    /** The id becomes a file name and part of a URL, so only plain characters are accepted. */
    private val SAFE_ID = Regex("[A-Za-z0-9_-]+")

    /**
     * Output of --print: one line per entry with these tab-separated columns: id, title, artist, playlist index,
     * playlist title, playlist id, album. Missing fields are printed as NA (older 3-column lines still work).
     */
    internal fun parseListing(out: String): List<FoundSong> =
        out.lineSequence()
            .map { it.trim('\r') }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split('\t')
                val id = parts.getOrNull(0)?.trim().orEmpty()
                if (id.isEmpty() || id == "NA" || !SAFE_ID.matches(id)) return@mapNotNull null
                val title = parts.getOrNull(1)?.trim().orEmpty().let { if (it == "NA") "" else it }
                val index = parts.getOrNull(3)?.trim()?.toIntOrNull()?.takeIf { it > 0 }
                FoundSong(
                    id = id,
                    title = title.ifBlank { id },
                    artist = cleanArtist(parts.getOrNull(2)),
                    trackNumber = index,
                    album = albumFor(
                        album = parts.getOrNull(6),
                        playlistTitle = parts.getOrNull(4),
                        playlistId = parts.getOrNull(5),
                    ),
                )
            }
            .distinctBy { it.id }
            .toList()

    /** Text from yt-dlp where "NA" / blank mean "unknown". */
    internal fun known(raw: String?): String = raw?.trim().orEmpty().let { if (it == "NA") "" else it }

    /**
     * The album name to pre-fill: the song's own album when YouTube gives one; otherwise the playlist title, but ONLY
     * when the playlist is a YouTube Music album (its id starts with OLAK5uy) - an ordinary playlist's name is not an album.
     */
    internal fun albumFor(album: String?, playlistTitle: String?, playlistId: String?): String {
        known(album).let { if (it.isNotEmpty()) return it }
        return if (known(playlistId).startsWith("OLAK5uy")) stripAlbumPrefix(known(playlistTitle)) else ""
    }

    private val ALBUM_PREFIX = Regex("^(album|single|ep|Άλμπουμ|άλμπουμ|Σινγκλ)\\s*[-–—:]\\s+", RegexOption.IGNORE_CASE)

    /** YouTube Music titles album playlists "Album - Name" (also "Single - ", "EP - "); the album itself is just "Name". */
    internal fun stripAlbumPrefix(title: String): String {
        val t = title.trim()
        val stripped = ALBUM_PREFIX.replace(t, "").trim()
        return stripped.ifEmpty { t }
    }

    /** Reads the info file yt-dlp wrote next to the MP3. Any problem simply means "nothing known". */
    internal fun readInfo(file: File): InfoMeta =
        runCatching { if (file.isFile) parseInfo(file.readText()) else InfoMeta() }.getOrDefault(InfoMeta())

    /** album, release year (release_year, else the year of release_date) and track number from an info JSON text. */
    internal fun parseInfo(text: String): InfoMeta {
        val root = com.localplayer.music.data.backup.MiniJson.parse(text) as? Map<*, *> ?: return InfoMeta()
        val album = known(root["album"] as? String)
        val releaseYear = (root["release_year"] as? Number)?.toInt()?.takeIf { it in 1000..9999 }?.toString()
        val releaseDate = (root["release_date"] as? String)?.trim().orEmpty()
        val yearFromDate = if (releaseDate.length >= 4 && releaseDate.take(4).all { it.isDigit() }) releaseDate.take(4) else ""
        val track = (root["track_number"] as? Number)?.toInt()?.takeIf { it > 0 }
        return InfoMeta(album = album, year = releaseYear ?: yearFromDate, trackNumber = track)
    }

    /** "NA" means unknown; YouTube Music channels are called "<Artist> - Topic". */
    internal fun cleanArtist(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text == "NA") return ""
        return text.removeSuffix(" - Topic").trim()
    }

    /** A file name that every file system accepts: no path/control characters, no trailing dots, bounded length. */
    internal fun safeFileName(title: String, fallback: String): String {
        return PlaylistSync.cleanName(title).ifBlank { fallback }
    }
}
