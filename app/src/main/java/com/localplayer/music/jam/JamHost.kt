package com.localplayer.music.jam

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The host side of a session: a small web server on this phone. Guests ask to join (the host accepts or refuses), then
 * keep asking "anything newer than revision N?" (the answer waits until there is something), and send songs.
 * Every request after joining carries the guest's secret token. Nothing leaves the local network.
 *
 * It knows nothing about the player: the manager gives it the queue ([publish]) and takes the uploaded songs ([onUploaded]).
 */
class JamHost(
    private val cacheDir: File,
    val hostName: String,
    val code: String,
    /** Called on a server thread with a song a guest sent; the file is complete. Return false when it is not accepted. */
    private val onUploaded: (UploadedSong) -> Boolean,
    /** The song behind a queue entry id, for the guests' players; null when it is not (or no longer) in the queue. */
    private val audioFor: (String) -> JamAudio?,
) {
    /** Someone who asked to join and waits for the host's answer. */
    data class Request(val id: String, val name: String, val deviceId: String, val askedAt: Long)

    class UploadedSong(
        val file: File,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val memberName: String,
    )

    private enum class Answer { PENDING, APPROVED, DENIED }

    private class Slot(val request: Request, var answer: Answer, var token: String? = null, var memberId: String? = null)

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val random = SecureRandom()
    private val server = MiniHttpServer(::handle)

    private var rev = 1L
    private var stopped = false
    private val members = ArrayList<JamMember>()
    private val tokens = HashMap<String, String>() // token -> member id
    private val devices = HashMap<String, String>() // device id -> member id
    private val slots = LinkedHashMap<String, Slot>() // request id -> slot
    private var queue: List<JamTrack> = emptyList()
    private var currentIndex = -1
    private var playing = false
    private var positionMs = 0L
    private var anchorMs = 0L
    private val voting = JamVoting()
    private var resultSinceMs = 0L

    /** Called (outside the lock) when a request for the next / previous song got enough votes. */
    @Volatile
    var onVotePassed: (JamAction) -> Unit = {}

    private val _votes = MutableStateFlow<Pair<JamProposal?, JamVoteResult?>>(null to null)

    /** The open request and the last result, for the host's own screen. */
    val votes: StateFlow<Pair<JamProposal?, JamVoteResult?>> = _votes.asStateFlow()

    private val _members = MutableStateFlow<List<JamMember>>(emptyList())
    val memberList: StateFlow<List<JamMember>> = _members.asStateFlow()
    private val readyByMember = HashMap<String, Set<String>>()
    private var ahead = JAM_DEFAULT_AHEAD
    private val downloadSlots = java.util.concurrent.Semaphore(MAX_DOWNLOADS)

    /** Wrong codes per address: after [MAX_WRONG_CODES] in a minute that address is locked out for a minute. */
    private class Attempts(var count: Int = 0, var since: Long = 0L, var lockedUntil: Long = 0L)

    private val attempts = HashMap<String, Attempts>()

    /** Bytes of uploads that are being received right now (counted against the cache limit at once). */
    private var uploadReserved = 0L

    private fun isLockedOut(remote: String): Boolean = synchronized(attempts) {
        (attempts[remote]?.lockedUntil ?: 0L) > System.currentTimeMillis()
    }

    private fun noteWrongCode(remote: String) = synchronized(attempts) {
        val now = System.currentTimeMillis()
        if (attempts.size > 256) attempts.values.removeAll { it.lockedUntil < now && now - it.since > ATTEMPT_WINDOW_MS }
        val a = attempts.getOrPut(remote) { Attempts(since = now) }
        if (now - a.since > ATTEMPT_WINDOW_MS) {
            a.count = 0
            a.since = now
        }
        a.count++
        if (a.count >= MAX_WRONG_CODES) {
            a.lockedUntil = now + ATTEMPT_WINDOW_MS
            a.count = 0
        }
    }

    /** For every guest: the queue entries it has loaded completely (it reports them as it goes). */
    private val _readiness = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val readiness: StateFlow<Map<String, Set<String>>> = _readiness.asStateFlow()
    private val _pending = MutableStateFlow<List<Request>>(emptyList())
    val pending: StateFlow<List<Request>> = _pending.asStateFlow()

    val port: Int get() = server.port

    /** False when no port could be opened. */
    fun start(): Boolean = server.start(JAM_DEFAULT_PORT, JAM_PORT_TRIES)

    fun stop() {
        lock.withLock {
            stopped = true
            changed.signalAll()
        }
        server.stop()
    }

    // --- Host actions -------------------------------------------------------------------------

    /** The host answered "yes" to a request. */
    fun approve(requestId: String) = lock.withLock {
        val slot = slots[requestId] ?: return@withLock
        if (slot.answer != Answer.PENDING) return@withLock
        if (members.size >= JAM_MAX_GUESTS) {
            slot.answer = Answer.DENIED
        } else {
            addMember(slot)
        }
        publishLists()
        changed.signalAll()
    }

    fun deny(requestId: String) = lock.withLock {
        val slot = slots[requestId] ?: return@withLock
        if (slot.answer == Answer.PENDING) slot.answer = Answer.DENIED
        publishLists()
        changed.signalAll()
    }

    /** Removes a guest; its next request is answered "unauthorized". */
    fun kick(memberId: String) = lock.withLock {
        removeMember(memberId)
        publishLists()
        changed.signalAll()
    }

    /** How many songs the guests load in advance (the current one and the next ones). */
    fun setAhead(count: Int) = lock.withLock {
        if (count == ahead) return@withLock
        ahead = count
        rev++
        changed.signalAll()
    }

    /**
     * The queue and the playback as they are now (from the player); wakes every guest that waits for news. [newPositionMs]
     * is the position of the current song at the host-clock time [newAnchorMs] ([JamClock.nowMs]); it is published again
     * every few seconds while playing, so the guests keep in step.
     */
    fun publish(newQueue: List<JamTrack>, newCurrentIndex: Int, isPlaying: Boolean, newPositionMs: Long, newAnchorMs: Long) = lock.withLock {
        if (newQueue == queue && newCurrentIndex == currentIndex && isPlaying == playing &&
            newPositionMs == positionMs && newAnchorMs == anchorMs
        ) {
            return@withLock
        }
        queue = newQueue
        currentIndex = newCurrentIndex
        playing = isPlaying
        positionMs = newPositionMs
        anchorMs = newAnchorMs
        rev++
        changed.signalAll()
    }

    // --- Internals (call with the lock held) --------------------------------------------------

    private fun newId(bytes: Int): String {
        val raw = ByteArray(bytes)
        random.nextBytes(raw)
        return raw.joinToString("") { "%02x".format(it) }
    }

    private fun addMember(slot: Slot) {
        val name = JamNames.unique(slot.request.name, members.map { it.name } + hostName)
        val member = JamMember(newId(6), name)
        members += member
        val token = newId(16)
        tokens[token] = member.id
        devices[slot.request.deviceId] = member.id
        slot.answer = Answer.APPROVED
        slot.token = token
        slot.memberId = member.id
        rev++
    }

    private fun removeMember(memberId: String) {
        readyByMember.remove(memberId)
        _readiness.value = HashMap(readyByMember)
        if (members.removeAll { it.id == memberId }) rev++
        tokens.entries.removeAll { it.value == memberId }
        devices.entries.removeAll { it.value == memberId }
    }

    private fun publishLists() {
        _members.value = members.toList()
        _pending.value = slots.values.filter { it.answer == Answer.PENDING }.map { it.request }
    }

    private fun purgeOldSlots() {
        // A request is only kept for a few minutes; an answer only matters until the guest has read it.
        val limit = System.currentTimeMillis() - SLOT_LIFETIME_MS
        if (slots.values.removeAll { it.request.askedAt < limit }) publishLists()
    }

    private fun memberFor(token: String?): JamMember? = lock.withLock {
        val id = tokens[token ?: return@withLock null] ?: return@withLock null
        members.firstOrNull { it.id == id }
    }

    // --- Requests -----------------------------------------------------------------------------

    private fun handle(request: HttpRequest): HttpResponse = when ("${request.method} ${request.path}") {
        "POST /join" -> join(request)
        "GET /join/status" -> joinStatus(request)
        "GET /state" -> state(request)
        "POST /upload" -> upload(request)
        "POST /leave" -> leave(request)
        "POST /ready" -> ready(request)
        "POST /propose" -> propose(request)
        "POST /vote" -> voteRequest(request)
        "GET /time" -> HttpResponse.json(200, JamJson.time(JamClock.nowMs()))
        "GET /audio" -> audio(request)
        "GET /ping" -> HttpResponse.json(200, "{}")
        else -> HttpResponse.json(404, JamJson.error("not_found"))
    }

    private fun join(request: HttpRequest): HttpResponse {
        val body = request.readBody(2048) ?: return HttpResponse.json(400, JamJson.error("bad_request"))
        val join = JamJson.decodeJoinRequest(String(body, Charsets.UTF_8))
            ?: return HttpResponse.json(400, JamJson.error("bad_request"))
        if (isLockedOut(request.remote)) return HttpResponse.json(429, JamJson.error("too_many"))
        if (join.code != code) {
            noteWrongCode(request.remote)
            return HttpResponse.json(403, JamJson.error("wrong_code"))
        }
        val name = JamNames.clean(join.name) ?: return HttpResponse.json(400, JamJson.error("bad_name"))
        val device = join.deviceId.take(64)
        return lock.withLock {
            purgeOldSlots()
            // The same phone again (it lost the connection, or the app was restarted): it gets a new token at once.
            val known = devices[device]
            if (known != null && members.any { it.id == known }) {
                removeTokensOf(known)
                val token = newId(16)
                tokens[token] = known
                changed.signalAll()
                return@withLock HttpResponse.json(200, JamJson.joinReply(newId(6), "approved", token, known))
            }
            if (members.size >= JAM_MAX_GUESTS) return@withLock HttpResponse.json(409, JamJson.error("full"))
            // A second request of a phone replaces its older one.
            slots.values.removeAll { it.request.deviceId == device && it.answer == Answer.PENDING }
            if (slots.values.count { it.answer == Answer.PENDING } >= MAX_PENDING) return@withLock HttpResponse.json(429, JamJson.error("too_many"))
            val req = Request(newId(6), name, device, System.currentTimeMillis())
            slots[req.id] = Slot(req, Answer.PENDING)
            publishLists()
            HttpResponse.json(200, JamJson.joinReply(req.id, "pending"))
        }
    }

    private fun removeTokensOf(memberId: String) {
        tokens.entries.removeAll { it.value == memberId }
    }

    private fun joinStatus(request: HttpRequest): HttpResponse {
        val id = request.query["request"] ?: return HttpResponse.json(400, JamJson.error("bad_request"))
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(POLL_SECONDS)
        return lock.withLock {
            var reply: HttpResponse? = null
            while (reply == null) {
                val slot = slots[id]
                if (slot == null) {
                    reply = HttpResponse.json(404, JamJson.error("unknown_request"))
                } else if (slot.answer == Answer.APPROVED) {
                    reply = HttpResponse.json(200, JamJson.joinReply(id, "approved", slot.token, slot.memberId))
                } else if (slot.answer == Answer.DENIED) {
                    reply = HttpResponse.json(200, JamJson.joinReply(id, "denied"))
                } else {
                    val left = deadline - System.nanoTime()
                    if (left <= 0 || stopped) reply = HttpResponse.json(200, JamJson.joinReply(id, "pending")) else changed.awaitNanos(left)
                }
            }
            reply
        }
    }

    private fun state(request: HttpRequest): HttpResponse {
        val token = request.query["token"] ?: return HttpResponse.json(401, JamJson.error("unauthorized"))
        if (memberFor(token) == null) return HttpResponse.json(401, JamJson.error("unauthorized"))
        val since = request.query["rev"]?.toLongOrNull() ?: 0L
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(POLL_SECONDS)
        return lock.withLock {
            while (rev <= since && !stopped) {
                val left = deadline - System.nanoTime()
                if (left <= 0) break
                changed.awaitNanos(left)
            }
            // Stopped, or this guest was removed while it was waiting.
            if (stopped || tokens[token] == null) {
                HttpResponse.json(401, JamJson.error("unauthorized"))
            } else {
                HttpResponse.json(200, JamJson.encode(snapshot(), tokens[token]))
            }
        }
    }

    private fun voterIds(): Set<String> = members.map { it.id }.toSet() + JAM_VOTER_HOST

    private fun currentTrackId(): String? = queue.getOrNull(currentIndex)?.id

    /** Sends a changed vote to everybody (call with the lock held). */
    private fun votesChanged() {
        if (voting.result?.id != _votes.value.second?.id) resultSinceMs = JamClock.nowMs()
        _votes.value = voting.proposal to voting.result
        rev++
        changed.signalAll()
    }

    private fun dispatch(outcome: JamVoting.Outcome) {
        if (outcome is JamVoting.Outcome.Passed) onVotePassed(outcome.action)
    }

    /** A guest asks for the next / previous song. */
    private fun propose(request: HttpRequest): HttpResponse {
        val member = memberFor(request.query["token"]) ?: return HttpResponse.json(401, JamJson.error("unauthorized"))
        val action = JamAction.parse(request.query["action"]) ?: return HttpResponse.json(400, JamJson.error("bad_request"))
        val outcome = lock.withLock {
            val result = voting.propose(newId(6), action, member.id, member.name, currentTrackId(), JamClock.nowMs(), voterIds())
            if (result != JamVoting.Outcome.Refused && result != JamVoting.Outcome.Busy) votesChanged()
            result
        }
        dispatch(outcome)
        return when (outcome) {
            JamVoting.Outcome.Busy -> HttpResponse.json(409, JamJson.error("busy"))
            JamVoting.Outcome.Refused -> HttpResponse.json(400, JamJson.error("refused"))
            else -> HttpResponse.json(200, "{}")
        }
    }

    /** A guest answers the open request: yes=1 or yes=0. */
    private fun voteRequest(request: HttpRequest): HttpResponse {
        val member = memberFor(request.query["token"]) ?: return HttpResponse.json(401, JamJson.error("unauthorized"))
        val id = request.query["id"] ?: return HttpResponse.json(400, JamJson.error("bad_request"))
        val yes = request.query["yes"] == "1"
        val outcome = lock.withLock {
            val result = voting.vote(member.id, id, yes, voterIds())
            if (result != JamVoting.Outcome.Refused && result != JamVoting.Outcome.Nothing) votesChanged()
            result
        }
        dispatch(outcome)
        return HttpResponse.json(200, "{}")
    }

    /** The host's own press of next / previous: it counts as the first yes. */
    fun proposeAsHost(action: JamAction) {
        val outcome = lock.withLock {
            val result = voting.propose(newId(6), action, JAM_VOTER_HOST, hostName, currentTrackId(), JamClock.nowMs(), voterIds())
            if (result != JamVoting.Outcome.Refused && result != JamVoting.Outcome.Busy) votesChanged()
            result
        }
        dispatch(outcome)
    }

    /** The host's own answer to the open request. */
    fun voteAsHost(proposalId: String, yes: Boolean) {
        val outcome = lock.withLock {
            val result = voting.vote(JAM_VOTER_HOST, proposalId, yes, voterIds())
            if (result != JamVoting.Outcome.Refused && result != JamVoting.Outcome.Nothing) votesChanged()
            result
        }
        dispatch(outcome)
    }

    /** Called every few seconds: ends requests that ran out of time or whose song changed, and recounts after guests left. */
    fun tickVotes() {
        val outcome = lock.withLock {
            val now = JamClock.nowMs()
            val result = voting.tick(now, currentTrackId(), voterIds())
            if (result != JamVoting.Outcome.Nothing) {
                votesChanged()
            } else if (voting.proposal == null && voting.result != null && now - resultSinceMs > JAM_VOTE_RESULT_MS) {
                voting.clearResult()
                votesChanged()
            }
            result
        }
        dispatch(outcome)
    }

    private fun snapshot() =
        JamState(rev, hostName, members.toList(), queue, currentIndex, playing, positionMs, anchorMs, ahead, voting.proposal, voting.result)

    /** A guest says which songs it has loaded completely (comma separated queue entry ids). */
    private fun ready(request: HttpRequest): HttpResponse {
        val member = memberFor(request.query["token"]) ?: return HttpResponse.json(401, JamJson.error("unauthorized"))
        val ids = request.query["ids"].orEmpty().split(',').filter { it.isNotBlank() }.take(32).toSet()
        lock.withLock {
            readyByMember[member.id] = ids
            _readiness.value = HashMap(readyByMember)
        }
        return HttpResponse.json(200, "{}")
    }

    /** At most [MAX_DOWNLOADS] guests download at the same time; the others wait for a free place. */
    private fun limited(open: () -> java.io.InputStream?): () -> java.io.InputStream? = {
        try {
            downloadSlots.acquire()
            val stream = try {
                open()
            } catch (e: RuntimeException) {
                null
            }
            if (stream == null) {
                downloadSlots.release()
                null
            } else {
                object : java.io.FilterInputStream(stream) {
                    private var released = false

                    override fun close() {
                        try {
                            super.close()
                        } finally {
                            if (!released) {
                                released = true
                                downloadSlots.release()
                            }
                        }
                    }
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }

    /** A song of the queue for a guest's player; honours "Range" so the player can seek. */
    private fun audio(request: HttpRequest): HttpResponse {
        if (memberFor(request.query["token"]) == null) return HttpResponse.json(401, JamJson.error("unauthorized"))
        val id = request.query["id"] ?: return HttpResponse.json(400, JamJson.error("bad_request"))
        val song = audioFor(id)
        if (song == null || song.length <= 0L) return HttpResponse.json(404, JamJson.error("not_found"))
        val ranged = mapOf("Accept-Ranges" to "bytes")
        return when (val range = HttpRange.parse(request.headers["range"], song.length)) {
            HttpRange.Full -> HttpResponse(
                200, contentType = song.mime, stream = limited { song.open(0L) }, streamLength = song.length, extraHeaders = ranged,
            )
            is HttpRange.Partial -> HttpResponse(
                206,
                contentType = song.mime,
                stream = limited { song.open(range.start) },
                streamLength = range.end - range.start + 1,
                extraHeaders = ranged + ("Content-Range" to "bytes ${range.start}-${range.end}/${song.length}"),
            )
            HttpRange.Unsatisfiable -> HttpResponse(416, extraHeaders = ranged + ("Content-Range" to "bytes */${song.length}"))
        }
    }

    // A rough guard (the file is also size-capped); cleared cache space does not matter for a few songs.
    @Suppress("UsableSpace")
    private fun upload(request: HttpRequest): HttpResponse {
        val member = memberFor(request.query["token"]) ?: return HttpResponse.json(401, JamJson.error("unauthorized"))
        val length = request.contentLength
        if (length <= 0) return HttpResponse.json(400, JamJson.error("bad_request"))
        if (length > JAM_MAX_UPLOAD_BYTES) return HttpResponse.json(413, JamJson.error("too_big"))
        if (!cacheDir.isDirectory && !cacheDir.mkdirs()) return HttpResponse.json(507, JamJson.error("no_space"))
        val used = cacheDir.listFiles().orEmpty().sumOf { it.length() }
        // The room is reserved at once, so several uploads at the same time cannot together pass the limit.
        val reserved = lock.withLock {
            if (used + uploadReserved + length > JAM_MAX_CACHE_BYTES) {
                false
            } else {
                uploadReserved += length
                true
            }
        }
        if (!reserved) return HttpResponse.json(507, JamJson.error("no_space"))
        try {
            if (cacheDir.usableSpace < length + FREE_SPACE_MARGIN) return HttpResponse.json(507, JamJson.error("no_space"))
            return receiveUpload(request, member, length)
        } finally {
            lock.withLock { uploadReserved -= length }
        }
    }

    private fun receiveUpload(request: HttpRequest, member: JamMember, length: Long): HttpResponse {
        val ext = safeAudioExtension("x." + request.query["ext"].orEmpty())
        val file = File(cacheDir, "jam-${newIdUnlocked()}.$ext")
        try {
            FileOutputStream(file).use { out ->
                val buffer = ByteArray(64 * 1024)
                var left = length
                while (left > 0) {
                    val n = request.body.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (n < 0) throw IOException("The guest stopped sending")
                    out.write(buffer, 0, n)
                    left -= n
                }
            }
        } catch (e: IOException) {
            file.delete()
            return HttpResponse.json(400, JamJson.error("incomplete"))
        }
        val song = UploadedSong(
            file = file,
            title = request.query["title"].orEmpty().take(200).ifBlank { file.nameWithoutExtension },
            artist = request.query["artist"].orEmpty().take(200),
            album = request.query["album"].orEmpty().take(200),
            durationMs = request.query["duration"]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            memberName = member.name,
        )
        val accepted = try {
            onUploaded(song)
        } catch (e: Exception) {
            false
        }
        if (!accepted) {
            file.delete()
            return HttpResponse.json(500, JamJson.error("rejected"))
        }
        return HttpResponse.json(200, "{\"ok\":true}")
    }

    private fun newIdUnlocked(): String = lock.withLock { newId(8) }

    private fun leave(request: HttpRequest): HttpResponse {
        val member = memberFor(request.query["token"]) ?: return HttpResponse.json(200, "{}")
        lock.withLock {
            removeMember(member.id)
            publishLists()
            changed.signalAll()
        }
        return HttpResponse.json(200, "{}")
    }

    private companion object {
        const val MAX_DOWNLOADS = 3
        const val POLL_SECONDS = 20L
        const val SLOT_LIFETIME_MS = 3 * 60 * 1000L
        const val FREE_SPACE_MARGIN = 100L * 1024 * 1024
        const val MAX_WRONG_CODES = 5
        const val ATTEMPT_WINDOW_MS = 60_000L
        const val MAX_PENDING = 10
    }
}
