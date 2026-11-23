package com.localplayer.music.data.spotify

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SpotifyTrack(val title: String, val artist: String, val durationMs: Long?)

data class SpotifyPlaylist(val name: String, val tracks: List<SpotifyTrack>)

/**
 * Reads the song NAMES (title + artist) of a Spotify playlist from its public "embed" web page, using only the share
 * link: no account, no key. Nothing is downloaded from Spotify. This is not an official interface: if Spotify changes
 * the page, reading stops working (the result is then [Result.Unreadable]).
 */
object SpotifyPlaylistReader {
    sealed interface Result {
        data class Success(val playlist: SpotifyPlaylist) : Result

        /** The text holds no playlist link. */
        data object BadLink : Result

        /** Spotify says there is no such (public) playlist. */
        data object NotFound : Result

        data object Network : Result

        /** The page was loaded but no song list could be found in it. */
        data object Unreadable : Result
    }

    private val idRegex = Regex("""playlist[/:]([A-Za-z0-9]{22})""")
    private val bareId = Regex("""^[A-Za-z0-9]{22}$""")
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** The 22-character playlist id inside a link (open.spotify.com/playlist/ID?si=..., spotify:playlist:ID) or the id itself. */
    fun playlistId(input: String): String? {
        val text = input.trim()
        idRegex.find(text)?.let { return it.groupValues[1] }
        return text.takeIf { bareId.matches(it) }
    }

    suspend fun read(input: String): Result = withContext(Dispatchers.IO) {
        val id = playlistId(input) ?: resolveShortLink(input)?.let { playlistId(it) } ?: return@withContext Result.BadLink
        val connection = URL("https://open.spotify.com/embed/playlist/$id").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            when (connection.responseCode) {
                200 -> {
                    val html = connection.inputStream.bufferedReader().use { it.readText() }
                    parseEmbedHtml(html)?.let { Result.Success(it) } ?: Result.Unreadable
                }
                404 -> Result.NotFound
                else -> Result.Network
            }
        } catch (e: java.io.IOException) {
            Result.Network
        } finally {
            connection.disconnect()
        }
    }

    /** spotify.link/xyz style share links redirect to the real address. */
    private fun resolveShortLink(input: String): String? {
        val url = input.trim().takeIf { it.startsWith("https://spotify.link/") || it.startsWith("https://spotify.app.link/") } ?: return null
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.responseCode
            connection.url.toString()
        } catch (e: java.io.IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** Pulls the playlist name and its songs out of the page's embedded data (JSON inside a script tag). */
    internal fun parseEmbedHtml(html: String): SpotifyPlaylist? {
        val json = Regex("""<script[^>]*id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: return null
        val root = try {
            JSONObject(json)
        } catch (e: org.json.JSONException) {
            return null
        }
        val tracks = ArrayList<SpotifyTrack>()
        val seen = HashSet<String>()
        var name: String? = null
        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val uri = node.optString("uri")
                    val title = node.optString("title")
                    if (uri.startsWith("spotify:track:") && title.isNotBlank()) {
                        if (seen.add(uri)) {
                            val artist = node.optString("subtitle").replace(' ', ' ').trim()
                            val duration = if (node.has("duration")) node.optLong("duration", -1L).takeIf { it > 0 } else null
                            tracks += SpotifyTrack(title.trim(), artist, duration)
                        }
                    } else {
                        if (name == null && node.optString("type") == "playlist") {
                            name = node.optString("name").ifBlank { node.optString("title") }.ifBlank { null }
                        }
                        val keys = node.keys()
                        while (keys.hasNext()) walk(node.opt(keys.next()))
                    }
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
            }
        }
        walk(root)
        if (tracks.isEmpty()) return null
        return SpotifyPlaylist(name ?: "Spotify", tracks)
    }
}
