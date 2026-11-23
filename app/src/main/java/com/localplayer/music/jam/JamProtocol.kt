package com.localplayer.music.jam

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Port the host tries first; if it is busy the next ones are tried (see [JamHost.start]). */
const val JAM_DEFAULT_PORT = 8765
const val JAM_PORT_TRIES = 10

/** At most this many guests (plus the host). */
const val JAM_MAX_GUESTS = 8
const val JAM_MAX_NAME = 24

/** The songs that must be loaded on every phone before the music starts: 1 to 5, default 1. */
const val JAM_GATE_MIN = 1
const val JAM_GATE_MAX = 5
const val JAM_GATE_DEFAULT = 1

/** The current song and the next two are always loaded in advance (more when the host asks for more). */
const val JAM_DEFAULT_AHEAD = 3

/** How many songs before the current one a guest keeps on the phone (for "previous" without waiting). */
const val JAM_KEEP_BEHIND = 2

/** One song a guest sends to the host may not be larger than this. */
const val JAM_MAX_UPLOAD_BYTES = 300L * 1024 * 1024

/** The temporary songs of one session may not take more than this on the host phone. */
const val JAM_MAX_CACHE_BYTES = 1536L * 1024 * 1024

/** A person in the session (the host is not listed here; it is [JamState.hostName]). */
data class JamMember(val id: String, val name: String)

/** One entry of the shared queue as the guests see it. [addedBy] is the name of the person who added it. */
data class JamTrack(val id: String, val title: String, val artist: String, val addedBy: String)

/**
 * Everything a guest needs to draw the session. The host owns it; [rev] grows with every change, so a guest asks
 * "give me something newer than rev N" and gets the answer as soon as there is one.
 */
data class JamState(
    val rev: Long,
    val hostName: String,
    val members: List<JamMember>,
    val queue: List<JamTrack>,
    val currentIndex: Int,
    val playing: Boolean,
    /** The host's playback position of the current song, true at the host-clock time [anchorMs] (see [JamClock]). */
    val positionMs: Long = 0L,
    val anchorMs: Long = 0L,
    /** How many songs (the current one and the next ones) every guest loads in advance. */
    val ahead: Int = JAM_DEFAULT_AHEAD,
    /** The open request for the next / previous song, if any, and how the last one ended. */
    val proposal: JamProposal? = null,
    val result: JamVoteResult? = null,
    /** The id of the member this answer was made for (set by the host for each guest). */
    val me: String? = null,
)

/** A song the host can send to a guest's player: its size, kind and a way to read it from any byte. */
class JamAudio(val length: Long, val mime: String, val open: (offset: Long) -> java.io.InputStream?)

/** Each phone's own clock (milliseconds, only ever compared with itself); the guest learns the host's with [offset]. */
object JamClock {
    fun nowMs(): Long = System.nanoTime() / 1_000_000L

    /** One measurement: [rttMs] = how long the question took, [offsetMs] = host clock minus this phone's clock. */
    data class Sample(val rttMs: Long, val offsetMs: Long)

    /** Host time at guest time [guestSendMs]..[guestReceiveMs], when the host said [hostMs] in between. */
    fun sample(guestSendMs: Long, guestReceiveMs: Long, hostMs: Long): Sample =
        Sample(guestReceiveMs - guestSendMs, hostMs - (guestSendMs + guestReceiveMs) / 2)

    /** The measurement with the shortest round trip is the most exact one. Null without samples. */
    fun best(samples: List<Sample>): Long? = samples.minByOrNull { it.rttMs }?.offsetMs
}

/** What a guest's player must do to follow the host. Pure, unit-tested. */
object JamSync {
    /** Where the host is now, when the host clock says [hostNowMs]. */
    fun expectedPositionMs(state: JamState, hostNowMs: Long): Long =
        if (state.playing) state.positionMs + (hostNowMs - state.anchorMs).coerceAtLeast(0L) else state.positionMs

    sealed interface Fix {
        /** Play at this speed (1.0 = in sync; a little faster or slower to catch up without a jump). */
        data class Speed(val value: Float) : Fix

        /** Too far apart: jump. */
        data class Seek(val positionMs: Long) : Fix
    }

    const val IN_SYNC_MS = 40L
    const val NUDGE_MS = 250L

    fun fix(actualMs: Long, expectedMs: Long): Fix {
        val diff = actualMs - expectedMs
        return when {
            kotlin.math.abs(diff) <= IN_SYNC_MS -> Fix.Speed(1f)
            kotlin.math.abs(diff) <= NUDGE_MS -> Fix.Speed(if (diff > 0) 0.97f else 1.03f)
            else -> Fix.Seek(expectedMs.coerceAtLeast(0L))
        }
    }
}

/**
 * The host waits with the music until the phones have loaded the first songs. Pure, unit-tested.
 * [wanted] = the queue entries that must be loaded (the first [gateSize] ones from the current song on, only entries
 * a guest can fetch); [ready] = guests that have all of them; [all] = everyone; [enough] = more than half of the guests,
 * which is when the host may press Start.
 */
object JamGate {
    data class Result(val wanted: List<String>, val ready: Int, val total: Int, val all: Boolean, val enough: Boolean, val needed: Int)

    fun evaluate(
        gateSize: Int,
        queueIds: List<String?>,
        currentIndex: Int,
        memberIds: List<String>,
        readiness: Map<String, Set<String>>,
    ): Result {
        val wanted = queueIds.drop(currentIndex.coerceAtLeast(0)).filterNotNull().take(gateSize.coerceIn(JAM_GATE_MIN, JAM_GATE_MAX))
        val ready = memberIds.count { id -> readiness[id].orEmpty().containsAll(wanted) }
        val total = memberIds.size
        val needed = if (total == 0) 0 else total / 2 + 1
        return Result(wanted, ready, total, all = ready == total, enough = total > 0 && ready >= needed, needed = needed)
    }
}

/** Where a song a guest sent goes in the host's queue. */
object JamQueue {
    /**
     * Right after the current song, but after the guests' songs that are already waiting there (so two songs added one
     * after the other play in that order). [fromGuest] says, for each queue entry, whether a guest added it.
     */
    fun insertIndex(currentIndex: Int, fromGuest: List<Boolean>): Int {
        var index = (currentIndex + 1).coerceIn(0, fromGuest.size)
        while (index < fromGuest.size && fromGuest[index]) index++
        return index
    }
}

/** Names typed by people: cleaned up, limited in length, made unique inside a session. */
object JamNames {
    private val spaces = Regex("[\\p{Cntrl}\\s]+")

    /** Null when nothing usable is left. */
    fun clean(raw: String?): String? {
        val text = raw.orEmpty().replace(spaces, " ").trim().take(JAM_MAX_NAME).trim()
        return text.ifEmpty { null }
    }

    /** [name], or "[name] 2", "[name] 3"... when [taken] already has it (ignoring upper/lower case). */
    fun unique(name: String, taken: Collection<String>): String {
        val lower = taken.map { it.lowercase() }.toSet()
        if (name.lowercase() !in lower) return name
        var n = 2
        while (true) {
            val suffix = " $n"
            val candidate = name.take(JAM_MAX_NAME - suffix.length).trimEnd() + suffix
            if (candidate.lowercase() !in lower) return candidate
            n++
        }
    }
}

/** What the QR code of a session says: where the host is and the code that lets a guest ask to join. */
data class JamInvite(val host: String, val port: Int, val code: String) {
    fun toUri(): String = "$SCHEME://$host:$port/$code"

    /** "192.168.1.5:8765", the same thing a person can type. */
    fun address(): String = "$host:$port"

    companion object {
        const val SCHEME = "lpjam"
        private val ipv4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
        private val codeRegex = Regex("^\\d{6}$")

        fun isIpv4(text: String): Boolean {
            val m = ipv4.matchEntire(text) ?: return false
            return m.groupValues.drop(1).all { it.toInt() in 0..255 }
        }

        fun isCode(text: String): Boolean = codeRegex.matches(text)

        /** Reads what [toUri] wrote; null for anything else (a QR code of something else entirely). */
        fun parse(text: String?): JamInvite? {
            val raw = text?.trim() ?: return null
            val prefix = "$SCHEME://"
            if (!raw.startsWith(prefix)) return null
            val rest = raw.removePrefix(prefix)
            val slash = rest.indexOf('/')
            if (slash < 0) return null
            return fromParts(rest.substring(0, slash), rest.substring(slash + 1))
        }

        /** From what a person typed: "192.168.1.5" or "192.168.1.5:8765" (default port) and the 6-digit code. */
        fun fromParts(address: String, code: String): JamInvite? {
            val addr = address.trim()
            val host = addr.substringBefore(':')
            val port = if (':' in addr) addr.substringAfter(':').toIntOrNull() else JAM_DEFAULT_PORT
            val cleanCode = code.trim()
            if (!isIpv4(host) || !PeerCheck.isLocalIpv4(host) || port == null || port !in 1..65535 || !isCode(cleanCode)) return null
            return JamInvite(host, port, cleanCode)
        }
    }
}

/** The JSON the host and the guests exchange. Small, hand-checked, unit-tested. */
object JamJson {
    fun encode(state: JamState, me: String? = null): String = JSONObject().apply {
        put("rev", state.rev)
        put("host", state.hostName)
        put(
            "members",
            JSONArray().apply { state.members.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } },
        )
        put(
            "queue",
            JSONArray().apply {
                state.queue.forEach {
                    put(JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist).put("by", it.addedBy))
                }
            },
        )
        put("current", state.currentIndex)
        put("playing", state.playing)
        put("pos", state.positionMs)
        put("anchor", state.anchorMs)
        put("ahead", state.ahead)
        if (me != null) put("me", me)
        state.proposal?.let { p ->
            put(
                "proposal",
                JSONObject().put("id", p.id).put("action", p.action.wire).put("byId", p.byId).put("by", p.byName)
                    .put("track", p.trackId).put("ends", p.endsAtMs).put("yes", JSONArray(p.yes.toList()))
                    .put("no", JSONArray(p.no.toList())).put("needed", p.needed).put("voters", p.voters),
            )
        }
        state.result?.let { r ->
            put("result", JSONObject().put("id", r.id).put("action", r.action.wire).put("by", r.byName).put("passed", r.passed))
        }
    }.toString()

    private fun ids(array: JSONArray?): Set<String> = if (array == null) emptySet() else (0 until array.length()).map { array.getString(it) }.toSet()

    fun decodeState(text: String): JamState? = try {
        val o = JSONObject(text)
        val members = o.getJSONArray("members")
        val queue = o.getJSONArray("queue")
        JamState(
            rev = o.getLong("rev"),
            hostName = o.getString("host"),
            members = (0 until members.length()).map {
                val m = members.getJSONObject(it)
                JamMember(m.getString("id"), m.getString("name"))
            },
            queue = (0 until queue.length()).map {
                val t = queue.getJSONObject(it)
                JamTrack(t.getString("id"), t.getString("title"), t.getString("artist"), t.getString("by"))
            },
            currentIndex = o.getInt("current"),
            playing = o.getBoolean("playing"),
            positionMs = o.optLong("pos", 0L),
            anchorMs = o.optLong("anchor", 0L),
            ahead = o.optInt("ahead", JAM_DEFAULT_AHEAD),
            proposal = o.optJSONObject("proposal")?.let { p ->
                val action = JamAction.parse(p.optString("action")) ?: return@let null
                JamProposal(
                    p.getString("id"), action, p.getString("byId"), p.getString("by"), p.getString("track"), p.getLong("ends"),
                    ids(p.optJSONArray("yes")), ids(p.optJSONArray("no")), p.getInt("needed"), p.getInt("voters"),
                )
            },
            result = o.optJSONObject("result")?.let { r ->
                val action = JamAction.parse(r.optString("action")) ?: return@let null
                JamVoteResult(r.getString("id"), action, r.getString("by"), r.getBoolean("passed"))
            },
            me = o.optString("me").ifEmpty { null },
        )
    } catch (e: JSONException) {
        null
    }

    fun joinRequest(code: String, name: String, deviceId: String): String =
        JSONObject().put("code", code).put("name", name).put("device", deviceId).toString()

    class JoinRequest(val code: String, val name: String, val deviceId: String)

    fun decodeJoinRequest(text: String): JoinRequest? = try {
        val o = JSONObject(text)
        JoinRequest(o.getString("code"), o.getString("name"), o.getString("device"))
    } catch (e: JSONException) {
        null
    }

    /** status = "pending" | "approved" | "denied"; [token] and [memberId] only when approved. */
    fun joinReply(requestId: String, status: String, token: String? = null, memberId: String? = null): String =
        JSONObject().apply {
            put("request", requestId)
            put("status", status)
            if (token != null) put("token", token)
            if (memberId != null) put("member", memberId)
        }.toString()

    class JoinReply(val requestId: String, val status: String, val token: String?, val memberId: String?)

    fun decodeJoinReply(text: String): JoinReply? = try {
        val o = JSONObject(text)
        JoinReply(
            o.getString("request"),
            o.getString("status"),
            o.optString("token").ifEmpty { null },
            o.optString("member").ifEmpty { null },
        )
    } catch (e: JSONException) {
        null
    }

    fun time(hostMs: Long): String = JSONObject().put("t", hostMs).toString()

    fun decodeTime(text: String): Long? = try {
        JSONObject(text).getLong("t")
    } catch (e: JSONException) {
        null
    }

    fun error(code: String): String = JSONObject().put("error", code).toString()

    fun errorCode(text: String): String? = try {
        JSONObject(text).optString("error").ifEmpty { null }
    } catch (e: JSONException) {
        null
    }
}

/** The extension of a song file, safe to put in a file name: 1 to 5 letters or digits, else "audio". */
fun safeAudioExtension(raw: String?): String {
    val ext = raw.orEmpty().substringAfterLast('.', "").lowercase()
    return if (ext.isNotEmpty() && ext.length <= 5 && ext.all { it in 'a'..'z' || it in '0'..'9' }) ext else "audio"
}
