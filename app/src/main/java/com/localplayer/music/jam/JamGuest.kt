package com.localplayer.music.jam

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The guest side: plain HTTP calls to the host (see [JamHost]). Every call blocks, so it runs on the IO dispatcher. */
class JamGuest(private val invite: JamInvite, private val deviceId: String) {
    sealed interface JoinResult {
        /** Waiting for the host to answer; ask again with [awaitApproval]. */
        data class Pending(val requestId: String) : JoinResult
        data class Approved(val token: String) : JoinResult
        data object WrongCode : JoinResult
        data object Full : JoinResult
        data object Unreachable : JoinResult
    }

    sealed interface Approval {
        data class Approved(val token: String) : Approval
        data object Denied : Approval
        data object StillPending : Approval
        data object Unreachable : Approval
    }

    sealed interface StateResult {
        data class Ok(val state: JamState) : StateResult
        /** The host does not know this guest any more (removed, or the session ended). */
        data object Removed : StateResult
        data object Unreachable : StateResult
    }

    sealed interface UploadResult {
        data object Sent : UploadResult
        data object TooBig : UploadResult
        data object NoSpace : UploadResult
        data object Unauthorized : UploadResult
        data object Failed : UploadResult
    }

    class UploadMeta(val title: String, val artist: String, val album: String, val durationMs: Long, val ext: String)

    private class Reply(val code: Int, val text: String)

    private val active: MutableSet<HttpURLConnection> = Collections.synchronizedSet(HashSet())

    @Volatile
    private var token: String? = null

    /** Cuts every call that is still running (leaving the session). */
    fun close() {
        synchronized(active) { active.toList() }.forEach { it.disconnect() }
    }

    suspend fun join(name: String): JoinResult = withContext(Dispatchers.IO) {
        val body = JamJson.joinRequest(invite.code, name, deviceId).toByteArray(Charsets.UTF_8)
        val reply = try {
            call("POST", "/join", emptyMap(), body, null, 10_000)
        } catch (e: IOException) {
            return@withContext JoinResult.Unreachable
        }
        when (reply.code) {
            200 -> {
                val parsed = JamJson.decodeJoinReply(reply.text) ?: return@withContext JoinResult.Unreachable
                val granted = parsed.token
                if (parsed.status == "approved" && granted != null) {
                    token = granted
                    JoinResult.Approved(granted)
                } else {
                    JoinResult.Pending(parsed.requestId)
                }
            }
            403 -> JoinResult.WrongCode
            409 -> JoinResult.Full
            else -> JoinResult.Unreachable
        }
    }

    /** One wait (up to about 20 seconds) for the host's answer. */
    suspend fun awaitApproval(requestId: String): Approval = withContext(Dispatchers.IO) {
        val reply = try {
            call("GET", "/join/status", mapOf("request" to requestId), null, null, 35_000)
        } catch (e: IOException) {
            return@withContext Approval.Unreachable
        }
        when (reply.code) {
            200 -> {
                val parsed = JamJson.decodeJoinReply(reply.text) ?: return@withContext Approval.Unreachable
                val granted = parsed.token
                when {
                    parsed.status == "approved" && granted != null -> {
                        token = granted
                        Approval.Approved(granted)
                    }
                    parsed.status == "denied" -> Approval.Denied
                    else -> Approval.StillPending
                }
            }
            404 -> Approval.Denied
            else -> Approval.Unreachable
        }
    }

    /** Waits until the host has something newer than [since] (up to about 20 seconds). */
    suspend fun pollState(since: Long): StateResult = withContext(Dispatchers.IO) {
        val granted = token ?: return@withContext StateResult.Removed
        val reply = try {
            call("GET", "/state", mapOf("token" to granted, "rev" to since.toString()), null, null, 35_000)
        } catch (e: IOException) {
            return@withContext StateResult.Unreachable
        }
        when (reply.code) {
            200 -> JamJson.decodeState(reply.text)?.let { StateResult.Ok(it) } ?: StateResult.Unreachable
            401 -> StateResult.Removed
            else -> StateResult.Unreachable
        }
    }

    /** Sends one song file. [length] is its exact size; [open] gives a fresh stream of it. */
    suspend fun upload(meta: UploadMeta, length: Long, open: () -> InputStream?): UploadResult = withContext(Dispatchers.IO) {
        val granted = token ?: return@withContext UploadResult.Unauthorized
        val query = mapOf(
            "token" to granted,
            "title" to meta.title,
            "artist" to meta.artist,
            "album" to meta.album,
            "duration" to meta.durationMs.toString(),
            "ext" to meta.ext,
        )
        val source = open() ?: return@withContext UploadResult.Failed
        val reply = try {
            source.use { call("POST", "/upload", query, null, UploadBody(it, length), 120_000) }
        } catch (e: IOException) {
            return@withContext UploadResult.Failed
        }
        when (reply.code) {
            200 -> UploadResult.Sent
            401 -> UploadResult.Unauthorized
            413 -> UploadResult.TooBig
            507 -> UploadResult.NoSpace
            else -> UploadResult.Failed
        }
    }

    /**
     * The host's clock minus this phone's clock (milliseconds), from several questions of which the quickest answer is
     * trusted most. Null when the host cannot be reached.
     */
    suspend fun measureClockOffset(samples: Int = 8): Long? = withContext(Dispatchers.IO) {
        val results = ArrayList<JamClock.Sample>()
        repeat(samples) {
            val sent = JamClock.nowMs()
            val reply = try {
                call("GET", "/time", emptyMap(), null, null, 3_000)
            } catch (e: IOException) {
                return@repeat
            }
            val received = JamClock.nowMs()
            val host = if (reply.code == 200) JamJson.decodeTime(reply.text) else null
            if (host != null) results += JamClock.sample(sent, received, host)
        }
        JamClock.best(results)
    }

    sealed interface DownloadResult {
        data object Done : DownloadResult

        /** The host does not have this song any more (it left the queue). */
        data object Gone : DownloadResult

        data object Failed : DownloadResult
    }

    /**
     * Loads the whole song with queue entry id [trackId] into [target] (through a ".part" file that is renamed at the
     * end, and continued from where it stopped after a failure). [onProgress] = bytes so far, bytes in all.
     */
    suspend fun download(trackId: String, target: java.io.File, onProgress: (Long, Long) -> Unit): DownloadResult =
        withContext(Dispatchers.IO) {
            val granted = token ?: return@withContext DownloadResult.Failed
            val part = java.io.File(target.parentFile, target.name + ".part")
            val have = if (part.isFile) part.length() else 0L
            val url = URL("http://${invite.host}:${invite.port}/audio?token=${enc(granted)}&id=${enc(trackId)}")
            val connection = url.openConnection() as HttpURLConnection
            active += connection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 5_000
                // The host lets only a few guests download at once; this one may have to wait for its turn.
                connection.readTimeout = 180_000
                connection.useCaches = false
                if (have > 0) connection.setRequestProperty("Range", "bytes=$have-")
                val code = connection.responseCode
                when (code) {
                    200, 206 -> {
                        val append = code == 206 && have > 0
                        val remaining = connection.contentLengthLong
                        val total = if (remaining < 0) -1L else remaining + if (append) have else 0L
                        var done = if (append) have else 0L
                        java.io.FileOutputStream(part, append).use { out ->
                            connection.inputStream.use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    out.write(buffer, 0, n)
                                    done += n
                                    onProgress(done, total)
                                }
                            }
                        }
                        if (total > 0 && done != total) {
                            DownloadResult.Failed // cut short: the next try continues from here
                        } else if (part.renameTo(target)) {
                            DownloadResult.Done
                        } else {
                            DownloadResult.Failed
                        }
                    }
                    404 -> {
                        part.delete()
                        DownloadResult.Gone
                    }
                    416 -> {
                        part.delete() // the .part file was not usable: start again
                        DownloadResult.Failed
                    }
                    else -> DownloadResult.Failed
                }
            } catch (e: IOException) {
                DownloadResult.Failed
            } finally {
                active -= connection
                connection.disconnect()
            }
        }

    /** Tells the host which songs this guest has loaded completely. */
    suspend fun reportReady(ids: Collection<String>) = withContext(Dispatchers.IO) {
        val granted = token ?: return@withContext
        try {
            call("POST", "/ready", mapOf("token" to granted, "ids" to ids.joinToString(",")), ByteArray(0), null, 5_000)
        } catch (e: IOException) {
            // The next report (or the next loaded song) says it again.
        }
    }

    /** Asks the host's Jam for the next / previous song; the others vote. */
    suspend fun propose(action: JamAction) = withContext(Dispatchers.IO) {
        val granted = token ?: return@withContext
        try {
            call("POST", "/propose", mapOf("token" to granted, "action" to action.wire), ByteArray(0), null, 5_000)
        } catch (e: IOException) {
            // Nothing happens; the person can ask again.
        }
    }

    /** This guest's answer to the open request. */
    suspend fun vote(proposalId: String, yes: Boolean) = withContext(Dispatchers.IO) {
        val granted = token ?: return@withContext
        try {
            call("POST", "/vote", mapOf("token" to granted, "id" to proposalId, "yes" to if (yes) "1" else "0"), ByteArray(0), null, 5_000)
        } catch (e: IOException) {
            // The vote is lost; the person can press again while the request is open.
        }
    }

    /** Tells the host this guest is leaving (best effort). */
    suspend fun leave() = withContext(Dispatchers.IO) {
        val granted = token ?: return@withContext
        try {
            call("POST", "/leave", mapOf("token" to granted), ByteArray(0), null, 3_000)
        } catch (e: IOException) {
            // The host is gone already.
        }
        token = null
    }

    private class UploadBody(val stream: InputStream, val length: Long)

    private fun call(method: String, path: String, query: Map<String, String>, body: ByteArray?, upload: UploadBody?, readTimeoutMs: Int): Reply {
        val queryText = query.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val url = URL("http://${invite.host}:${invite.port}$path" + if (queryText.isEmpty()) "" else "?$queryText")
        val connection = url.openConnection() as HttpURLConnection
        active += connection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false // a host that sends the phone somewhere else is not trusted
            connection.connectTimeout = 5_000
            connection.readTimeout = readTimeoutMs
            connection.useCaches = false
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            } else if (upload != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(upload.length)
                connection.outputStream.use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var left = upload.length
                    while (left > 0) {
                        val n = upload.stream.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                        if (n < 0) throw IOException("The song ended early")
                        out.write(buffer, 0, n)
                        left -= n
                    }
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty()
            return Reply(code, text)
        } finally {
            active -= connection
            connection.disconnect()
        }
    }

    private fun enc(text: String): String = URLEncoder.encode(text, "UTF-8")
}
