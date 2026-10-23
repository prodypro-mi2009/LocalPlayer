package com.localplayer.music.data.download

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/** One song found at a link. [artist] may be empty (YouTube did not say). */
data class FoundSong(val id: String, val title: String, val artist: String)

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
        val request = YoutubeDLRequest(url.trim())
        request.addOption("--flat-playlist")
        request.addOption("--ignore-errors")
        request.addOption("--no-warnings")
        request.addOption("--print", "%(id)s\t%(title)s\t%(artist,uploader,channel)s")
        val out = YoutubeDL.getInstance().execute(request, null, null).out
        return parseListing(out)
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
    fun downloadMp3(context: Context, id: String, dir: File, processId: String, onProgress: (Float) -> Unit): File {
        ensureInit(context)
        dir.mkdirs()
        val target = File(dir, "$id.mp3")
        var lastError: Throwable? = null
        for ((index, attempt) in ATTEMPTS.withIndex()) {
            target.delete()
            onProgress(0f)
            val request = YoutubeDLRequest("https://www.youtube.com/watch?v=$id")
            for ((option, value) in attempt) request.addOption(option, value)
            request.addOption("-x")
            request.addOption("--audio-format", "mp3")
            request.addOption("--audio-quality", "0")
            request.addOption("--embed-thumbnail")
            request.addOption("--convert-thumbnails", "jpg")
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
            if (target.isFile && target.length() > 0L) return target
            lastError = IllegalStateException("yt-dlp produced no MP3 (attempt ${index + 1})")
        }
        throw lastError ?: IllegalStateException("yt-dlp produced no MP3 for $id")
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    class UpdateResult(val error: String?, val alreadyLatest: Boolean)

    /** Updates the bundled yt-dlp from the latest stable GitHub release. */
    fun update(context: Context): UpdateResult = try {
        ensureInit(context)
        val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.STABLE)
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

    /** Output of --print: one "id TAB title TAB artist" line per entry; missing fields are printed as NA. */
    internal fun parseListing(out: String): List<FoundSong> =
        out.lineSequence()
            .map { it.trim('\r') }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split('\t')
                val id = parts.getOrNull(0)?.trim().orEmpty()
                if (id.isEmpty() || id == "NA" || id.contains(' ')) return@mapNotNull null
                val title = parts.getOrNull(1)?.trim().orEmpty().let { if (it == "NA") "" else it }
                FoundSong(id, title.ifBlank { id }, cleanArtist(parts.getOrNull(2)))
            }
            .distinctBy { it.id }
            .toList()

    /** "NA" means unknown; YouTube Music channels are called "<Artist> - Topic". */
    internal fun cleanArtist(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text == "NA") return ""
        return text.removeSuffix(" - Topic").trim()
    }

    /** A file name that every file system accepts: no path/control characters, no trailing dots, bounded length. */
    internal fun safeFileName(title: String, fallback: String): String {
        val cleaned = title
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.', ' ')
            .take(120)
        return cleaned.ifBlank { fallback }
    }
}
