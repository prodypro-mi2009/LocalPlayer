package com.localplayer.music.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal client for the public LRCLIB API (https://lrclib.net/api). Only the documented endpoints are used:
 * GET /api/get (track_name, artist_name, album_name, duration) and GET /api/search (track_name, artist_name).
 * It runs only when the user presses "Internet Search", over HTTPS, with timeouts and without any account or token.
 */
class LrcLibClient(private val userAgent: String) {

    sealed interface Outcome {
        class Found(val results: List<LrcLibResult>) : Outcome
        data object NetworkError : Outcome
        data object RateLimited : Outcome
        class ServerError(val code: Int) : Outcome
    }

    private sealed interface Http {
        class Ok(val body: String) : Http
        data object NotFound : Http
        data object Limited : Http
        class Failed(val code: Int) : Http
        data object Network : Http
    }

    suspend fun search(title: String, artist: String?, album: String?, durationMs: Long): Outcome = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<Long, LrcLibResult>()

        // 1) exact lookup, when everything it needs is known
        if (!album.isNullOrBlank() && !artist.isNullOrBlank() && durationMs > 0) {
            val q = "track_name=${enc(title)}&artist_name=${enc(artist)}&album_name=${enc(album)}&duration=${(durationMs / 1000.0).toInt()}"
            when (val r = get("$BASE/get?$q")) {
                is Http.Ok -> parseObject(r.body)?.let { found[it.id] = it }
                Http.NotFound -> Unit
                Http.Limited -> return@withContext Outcome.RateLimited
                is Http.Failed -> return@withContext Outcome.ServerError(r.code)
                Http.Network -> return@withContext Outcome.NetworkError
            }
        }

        // 2) search by title (+ artist)
        val q = buildString {
            append("track_name=").append(enc(title))
            if (!artist.isNullOrBlank()) append("&artist_name=").append(enc(artist))
        }
        when (val r = get("$BASE/search?$q")) {
            is Http.Ok -> parseArray(r.body).forEach { found.putIfAbsent(it.id, it) }
            Http.NotFound -> Unit
            Http.Limited -> return@withContext Outcome.RateLimited
            is Http.Failed -> return@withContext Outcome.ServerError(r.code)
            Http.Network -> if (found.isEmpty()) return@withContext Outcome.NetworkError
        }
        Outcome.Found(found.values.toList())
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): Http {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/json")
            }
            when (val code = connection.responseCode) {
                200 -> Http.Ok(connection.inputStream.bufferedReader().use { readLimited(it) })
                404 -> Http.NotFound
                429 -> Http.Limited
                else -> Http.Failed(code)
            }
        } catch (e: Exception) {
            Http.Network
        } finally {
            connection?.disconnect()
        }
    }

    private fun readLimited(reader: java.io.Reader): String {
        val out = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val n = reader.read(buffer)
            if (n < 0) break
            out.append(buffer, 0, n)
            if (out.length > MAX_CHARS) throw java.io.IOException("response too large")
        }
        return out.toString()
    }

    private fun parseArray(body: String): List<LrcLibResult> = try {
        val array = JSONArray(body)
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(::toResult) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun parseObject(body: String): LrcLibResult? = try {
        toResult(JSONObject(body))
    } catch (e: Exception) {
        null
    }

    private fun JSONObject.text(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

    private fun toResult(o: JSONObject): LrcLibResult? {
        if (!o.has("id")) return null
        return LrcLibResult(
            id = o.optLong("id"),
            trackName = o.text("trackName") ?: o.text("name") ?: return null,
            artistName = o.text("artistName").orEmpty(),
            albumName = o.text("albumName"),
            durationSec = o.optDouble("duration", 0.0).let { if (it.isNaN()) 0.0 else it },
            instrumental = o.optBoolean("instrumental", false),
            plainLyrics = o.text("plainLyrics"),
            syncedLyrics = o.text("syncedLyrics"),
        )
    }

    private companion object {
        const val BASE = "https://lrclib.net/api"
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 10_000
        const val MAX_CHARS = 2_000_000
    }
}
