package com.localplayer.music.jam

import androidx.core.content.edit
import androidx.core.net.toUri
import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.OpenableColumns
import com.localplayer.music.R
import com.localplayer.music.data.AppLanguage
import com.localplayer.music.data.db.TrackListItem
import com.localplayer.music.playback.MediaItemFactory
import com.localplayer.music.playback.PlayerConnection
import com.localplayer.music.playback.ArtworkRef
import com.localplayer.music.playback.NowPlaying
import com.localplayer.music.playback.PlayerState
import com.localplayer.music.playback.QueueEntry
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Jam: listening together. One phone is the host (it plays, and its queue IS the shared queue), the others join as
 * guests, see the queue and send songs from their own phone. Everything stays in the local network (same Wi-Fi, or the
 * host's own hotspot). Off by default (Settings > Jam). Nothing runs until the user starts or joins a session.
 */
class JamManager(private val context: Context, private val player: PlayerConnection) {
    enum class Role { NONE, HOST, GUEST }

    enum class GuestStatus { CONNECTING, WAITING, CONNECTED, DENIED, NO_ANSWER, WRONG_CODE, FULL, UNREACHABLE, REMOVED }

    enum class HostProblem { NO_NETWORK, PORT_BUSY }

    /** One-off messages for the screen (shown as short toasts). */
    enum class Event { SONG_SENT, SONG_FAILED, SONG_TOO_BIG, SONG_UNREADABLE, HOST_NO_SPACE }

    data class Ui(
        val role: Role = Role.NONE,
        val hostProblem: HostProblem? = null,
        // Host
        val invite: JamInvite? = null,
        val members: List<JamMember> = emptyList(),
        val pending: List<JamHost.Request> = emptyList(),
        /** The music has not started yet: the host waits for the guests to load the first songs. */
        val waiting: Boolean = false,
        val readyCount: Int = 0,
        val guestCount: Int = 0,
        /** Enough guests (half and one more) are ready for the host to press Start. */
        val enough: Boolean = false,
        /** The guests that have loaded the songs the gate waits for. */
        val readyMembers: Set<String> = emptySet(),
        // Guest
        val guestStatus: GuestStatus = GuestStatus.CONNECTING,
        val myName: String = "",
        val state: JamState? = null,
        /** Songs this phone is sending right now. */
        val uploading: Int = 0,
        /** The guest's switch: play the Jam on this phone (on by default); off = only watch the queue. */
        val playLocally: Boolean = true,
        /** Queue entries of the shared queue that are completely loaded on this phone. */
        val loaded: Set<String> = emptySet(),
        /** Loading progress (0..1) of the song being loaded now, or null. */
        val loadProgress: Float? = null,
        // Voting for the next / previous song (both roles)
        val proposal: JamProposal? = null,
        val voteResult: JamVoteResult? = null,
        /** This phone's voter id ([JAM_VOTER_HOST] for the host). */
        val myId: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = context.getSharedPreferences("jam", Context.MODE_PRIVATE)

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    /** The open request alone (the buttons of the player screen watch this, not the whole [ui]). */
    private val _proposal = MutableStateFlow<JamProposal?>(null)
    val proposal: StateFlow<JamProposal?> = _proposal.asStateFlow()

    // Host: which song was current, for noticing a change of song.
    private var lastCurrentId: String? = null
    private var baselineSet = false
    private var holdUntilMs = 0L
    private var lastPositionMs = 0L
    private var lastDurationMs = 0L

    // Guest
    private var guestCacheDir: File? = null
    private var lastSongShown: String? = null

    private val hook = object : PlayerConnection.JamHook {
        override fun next() = ask(JamAction.NEXT)

        override fun previous() = ask(JamAction.PREVIOUS)

        override fun playPause(): Boolean {
            if (_ui.value.role != Role.GUEST || _ui.value.state == null) return false
            // A guest's play / pause is its own switch: hear the Jam on this phone or not.
            setPlayLocally(!_ui.value.playLocally)
            return true
        }
    }

    init {
        player.jamHook = hook
    }

    private var host: JamHost? = null
    private var hostJob: Job? = null
    private var guest: JamGuest? = null
    private var guestJob: Job? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val janitor = JamFileJanitor()

    // Host: what the guests' players may fetch (queue entry id -> where the song is read from) and the latest player data.
    @Volatile
    private var sources: Map<String, String> = emptyMap()
    private var latestQueue: List<QueueEntry> = emptyList()
    private var latestState = PlayerState()
    private val waiting = MutableStateFlow(false)
    private val gateSize = MutableStateFlow(prefs.getInt(KEY_GATE, JAM_GATE_DEFAULT).coerceIn(JAM_GATE_MIN, JAM_GATE_MAX))

    /** How many songs every guest must have loaded before the music starts (chosen by the host, 1 by default). */
    val gateSizeState: StateFlow<Int> get() = gateSize.asStateFlow()

    @Volatile
    private var scheduledAt = 0L

    // Guest: the local player that follows the host and how far the host's clock is from this phone's.
    private var guestPlayer: JamGuestPlayer? = null
    private val wantedIds = MutableStateFlow<List<String>>(emptyList())
    private val loadedIds = MutableStateFlow<Set<String>>(emptySet())
    private var clockOffsetMs = 0L

    /** While true the player connection must stay connected even when the app is in the background. */
    val isHosting: Boolean get() = host != null

    /** The name used last time (prefilled in the dialogs). */
    fun lastName(): String = prefs.getString(KEY_NAME, "").orEmpty()

    private fun rememberName(name: String) {
        prefs.edit { putString(KEY_NAME, name) }
    }

    private fun deviceId(): String {
        prefs.getString(KEY_DEVICE, null)?.let { return it }
        val fresh = UUID.randomUUID().toString()
        prefs.edit { putString(KEY_DEVICE, fresh) }
        return fresh
    }

    // --- Host ---------------------------------------------------------------------------------

    fun startHosting(rawName: String) {
        if (_ui.value.role != Role.NONE) return
        val name = JamNames.clean(rawName) ?: return
        rememberName(name)
        val addresses = JamNet.localAddresses()
        if (addresses.isEmpty()) {
            _ui.value = Ui(hostProblem = HostProblem.NO_NETWORK)
            return
        }
        val code = (100_000 + SecureRandom().nextInt(900_000)).toString()
        val dir = File(context.cacheDir, "jam")
        purgeOldFiles(dir)
        val newHost = JamHost(dir, name, code, ::onUploaded, ::audioFor)
        if (!newHost.start()) {
            _ui.value = Ui(hostProblem = HostProblem.PORT_BUSY)
            return
        }
        host = newHost
        janitor.clear()
        waiting.value = false
        scheduledAt = 0L
        baselineSet = false
        lastCurrentId = null
        newHost.setAhead(maxOf(JAM_DEFAULT_AHEAD, gateSize.value))
        _ui.value = Ui(role = Role.HOST, invite = JamInvite(addresses.first(), newHost.port, code))
        player.connect()
        JamService.start(context)
        acquireWifiLock()
        hostJob = scope.launch {
            launch { newHost.memberList.collect { list -> _ui.update { it.copy(members = list) } } }
            launch { newHost.pending.collect { list -> _ui.update { it.copy(pending = list) } } }
            newHost.onVotePassed = { action ->
                scope.launch {
                    when (action) {
                        JamAction.NEXT -> player.nextTrack()
                        JamAction.PREVIOUS -> player.previousTrack()
                    }
                }
            }
            launch {
                newHost.votes.collect { (proposal, result) ->
                    _proposal.value = proposal
                    _ui.update { it.copy(proposal = proposal, voteResult = result, myId = JAM_VOTER_HOST) }
                    voteAlert(proposal, JAM_VOTER_HOST)
                }
            }
            launch {
                combine(player.queue, player.state) { queue, state -> queue to state }.collect { (queue, state) ->
                    latestQueue = queue
                    latestState = state
                    sources = queue.mapNotNull { entry ->
                        val id = entry.occurrenceId
                        val uri = entry.sourceUri
                        if (id != null && uri != null) id to uri else null
                    }.toMap()
                    detectSongChange(newHost)
                    // (The pause that holds a new song takes a moment to arrive: until then "playing" does not end the wait.)
                    if (state.isPlaying && JamClock.nowMs() >= holdUntilMs) waiting.value = false
                    republish(newHost, name)
                    checkGate(newHost, name)
                    janitor.onQueue(queue.map { it.occurrenceId }).forEach { it.delete() }
                }
            }
            launch {
                combine(waiting, gateSize, newHost.readiness, newHost.memberList) { w, g, r, m -> arrayOf(w, g, r, m) }
                    .collect { checkGate(newHost, name) }
            }
            launch {
                gateSize.collect { size ->
                    newHost.setAhead(maxOf(JAM_DEFAULT_AHEAD, size))
                }
            }
            // The position is published again every few seconds, so the guests notice a seek and never drift apart.
            launch {
                while (true) {
                    delay(HOST_ANCHOR_MS)
                    lastPositionMs = player.currentPositionMs()
                    lastDurationMs = latestState.durationMs
                    republish(newHost, name)
                    newHost.tickVotes()
                    notifySong(latestQueue.getOrNull(latestState.queueIndex)?.title, latestQueue.getOrNull(latestState.queueIndex)?.artist)
                }
            }
        }
    }

    /**
     * The host chose another song (a tap, next / previous after a vote): hold the music until the guests have it, then
     * everybody starts together. A song that simply ended and went on to the next one is left alone.
     */
    private fun detectSongChange(h: JamHost) {
        val queue = latestQueue
        val state = latestState
        val id = queue.getOrNull(state.queueIndex)?.occurrenceId
        val before = lastCurrentId
        lastCurrentId = id
        if (!baselineSet) {
            baselineSet = true
            return
        }
        if (id == null || id == before) {
            if (id == before) {
                lastPositionMs = player.currentPositionMs()
                lastDurationMs = state.durationMs
            }
            return
        }
        if (waiting.value || scheduledAt > JamClock.nowMs() || h.memberList.value.isEmpty()) return
        val ended = before != null && lastDurationMs > 0 && lastDurationMs - lastPositionMs < NATURAL_END_MS &&
            queue.getOrNull(state.queueIndex - 1)?.occurrenceId == before
        if (ended) return
        holdUntilMs = JamClock.nowMs() + HOLD_MS
        player.pause()
        waiting.value = true
    }

    private fun republish(h: JamHost, hostName: String) {
        val state = latestState
        val start = scheduledAt
        val scheduled = start > JamClock.nowMs()
        // While the music is held for the guests (a new song) it counts as paused, even for the moment before the pause
        // reaches the player: otherwise the guests would start at once and be a second ahead of the host.
        val playing = (state.isPlaying && !(waiting.value && !scheduled)) || scheduled
        h.publish(
            latestQueue.map { JamTrack(it.occurrenceId ?: "i${it.index}", it.title, it.artist, it.addedBy ?: hostName) },
            state.queueIndex,
            playing,
            player.currentPositionMs(),
            // While paused the moment does not matter, and a constant keeps the state from changing for nothing.
            // A moment in the future = "everybody start then".
            if (scheduled) start else if (playing) JamClock.nowMs() else 0L,
        )
    }

    /** Looks at how many guests have loaded the first songs; starts by itself when all of them have. */
    private fun checkGate(h: JamHost, hostName: String) {
        val members = h.memberList.value
        val result = JamGate.evaluate(
            gateSize.value,
            latestQueue.map { it.occurrenceId },
            latestState.queueIndex,
            members.map { it.id },
            h.readiness.value,
        )
        val readyMembers = members.filter { h.readiness.value[it.id].orEmpty().containsAll(result.wanted) }.map { it.id }.toSet()
        _ui.update {
            it.copy(
                waiting = waiting.value,
                readyCount = result.ready,
                guestCount = result.total,
                enough = result.enough,
                readyMembers = readyMembers,
            )
        }
        if (waiting.value && result.wanted.isNotEmpty() && result.all) startNow(h, hostName)
    }

    /** The host's Start button (and the automatic start): everybody starts the music together a moment from now. */
    fun startNow() {
        val h = host ?: return
        startNow(h, h.hostName)
    }

    private fun startNow(h: JamHost, hostName: String) {
        if (!waiting.value) return
        waiting.value = false
        scheduledAt = JamClock.nowMs() + START_DELAY_MS
        republish(h, hostName)
        scope.launch {
            delay(START_DELAY_MS)
            player.play()
            scheduledAt = 0L
        }
    }

    fun setGateSize(size: Int) {
        val value = size.coerceIn(JAM_GATE_MIN, JAM_GATE_MAX)
        prefs.edit { putInt(KEY_GATE, value) }
        gateSize.value = value
    }

    /** The song behind a queue entry, for a guest's player. Runs on a server thread. */
    private fun audioFor(id: String): JamAudio? {
        val text = sources[id] ?: return null
        val uri = text.toUri()
        return if (uri.scheme == "file") fileAudio(File(uri.path.orEmpty())) else contentAudio(uri)
    }

    private fun fileAudio(file: File): JamAudio? {
        if (!file.isFile) return null
        return JamAudio(file.length(), mimeFor(file.name)) { offset ->
            try {
                FileInputStream(file).also { it.channel.position(offset) }
            } catch (e: IOException) {
                null
            }
        }
    }

    private fun contentAudio(uri: Uri): JamAudio? {
        val resolver = context.contentResolver
        val length = try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        } catch (e: IOException) {
            -1L
        } catch (e: SecurityException) {
            -1L
        }
        if (length <= 0L) return null
        return JamAudio(length, resolver.getType(uri) ?: "application/octet-stream") { offset ->
            try {
                resolver.openAssetFileDescriptor(uri, "r")?.createInputStream()?.also { skipFully(it, offset) }
            } catch (e: IOException) {
                null
            }
        }
    }

    private fun skipFully(input: InputStream, count: Long) {
        var left = count
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped > 0) {
                left -= skipped
            } else {
                if (input.read() < 0) return
                left--
            }
        }
    }

    private fun mimeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a", "mp4", "aac" -> "audio/mp4"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }

    fun approve(requestId: String) {
        host?.approve(requestId)
    }

    fun deny(requestId: String) {
        host?.deny(requestId)
    }

    /** Asks for the next / previous song: everybody (host and guests) votes. */
    fun requestChange(action: JamAction) {
        ask(action)
    }

    /** True when the press was taken by the Jam (it is a request for everybody to vote on, host included). */
    private fun ask(action: JamAction): Boolean {
        val h = host
        if (h != null) {
            scope.launch(Dispatchers.IO) { h.proposeAsHost(action) }
            return true
        }
        val g = guest
        if (g != null && _ui.value.state != null && _ui.value.guestStatus == GuestStatus.CONNECTED) {
            scope.launch { g.propose(action) }
            return true
        }
        return false
    }

    /** Answers the open request (the screen's Yes / No, or the notification's). */
    fun vote(proposalId: String, yes: Boolean) {
        val h = host
        if (h != null) {
            scope.launch(Dispatchers.IO) { h.voteAsHost(proposalId, yes) }
            return
        }
        val g = guest ?: return
        scope.launch { g.vote(proposalId, yes) }
    }

    /** Shows (or removes) the notification that asks this phone for its vote. */
    private fun voteAlert(proposal: JamProposal?, myId: String?) {
        val mine = proposal != null && myId != null && proposal.byId != myId && myId !in proposal.yes && myId !in proposal.no
        if (mine) JamVoteNotifier.show(context, proposal!!) else JamVoteNotifier.cancel(context)
    }

    fun kick(memberId: String) {
        host?.kick(memberId)
    }

    private fun stopHosting() {
        val h = host ?: return
        host = null
        hostJob?.cancel()
        hostJob = null
        waiting.value = false
        scheduledAt = 0L
        h.stop()
        _proposal.value = null
        notifySong(null, null)
        voteAlert(null, null)
        releaseWifiLock()
        JamService.stop(context)
        _ui.value = Ui()
    }

    /** A song arrived from a guest: put it at the end of the queue. Runs on a server thread; true when it was queued. */
    private fun onUploaded(song: JamHost.UploadedSong): Boolean {
        val strings = AppLanguage.wrap(context)
        val item = MediaItemFactory.createJam(
            song.file, song.title, song.artist, song.album, song.durationMs, song.memberName,
            strings.getString(R.string.unknown_artist), strings.getString(R.string.unknown_album),
        )
        val queued = runBlocking {
            withTimeoutOrNull(QUEUE_TIMEOUT_MS) {
                withContext(Dispatchers.Main) {
                    try {
                        // Right after the current song (and the guests' songs already waiting there), not at the end.
                        val added = player.insertJamOccurrence(item)
                        janitor.track(added.occurrenceId, song.file)
                        // The first song of an empty queue waits until the guests have loaded it (see the gate).
                        if (added.becameCurrent) waiting.value = true
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
            }
        }
        return queued == true
    }

    private fun purgeOldFiles(dir: File) {
        val limit = System.currentTimeMillis() - OLD_FILE_MS
        dir.listFiles().orEmpty().filter { it.lastModified() < limit }.forEach { it.delete() }
    }

    private fun acquireWifiLock() {
        if (wifiLock != null) return
        val manager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = manager.createWifiLock(mode, "LocalPlayer:Jam").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWifiLock() {
        wifiLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
    }

    // --- Guest --------------------------------------------------------------------------------

    fun join(invite: JamInvite, rawName: String) {
        if (_ui.value.role != Role.NONE) return
        val name = JamNames.clean(rawName) ?: return
        rememberName(name)
        val g = JamGuest(invite, deviceId())
        guest = g
        _ui.value = Ui(role = Role.GUEST, guestStatus = GuestStatus.CONNECTING, myName = name)
        JamService.start(context)
        guestJob = scope.launch {
            try {
                runGuest(g, name)
            } finally {
                guestPlayer?.release()
                guestPlayer = null
                guestCacheDir = null
                player.setRemote(null)
                _proposal.value = null
                notifySong(null, null)
                voteAlert(null, null)
                JamService.stop(context)
            }
        }
    }

    private fun setStatus(status: GuestStatus) {
        _ui.update { it.copy(guestStatus = status) }
    }

    private suspend fun runGuest(g: JamGuest, name: String) {
        when (val joined = g.join(name)) {
            is JamGuest.JoinResult.Approved -> Unit
            is JamGuest.JoinResult.Pending -> {
                setStatus(GuestStatus.WAITING)
                var answered = false
                repeat(APPROVAL_WAITS) {
                    if (answered) return@repeat
                    currentCoroutineContext().ensureActive()
                    when (g.awaitApproval(joined.requestId)) {
                        is JamGuest.Approval.Approved -> answered = true
                        JamGuest.Approval.Denied -> {
                            setStatus(GuestStatus.DENIED)
                            return
                        }
                        JamGuest.Approval.StillPending -> Unit
                        JamGuest.Approval.Unreachable -> {
                            setStatus(GuestStatus.UNREACHABLE)
                            return
                        }
                    }
                }
                if (!answered) {
                    setStatus(GuestStatus.NO_ANSWER)
                    return
                }
            }
            JamGuest.JoinResult.WrongCode -> return setStatus(GuestStatus.WRONG_CODE)
            JamGuest.JoinResult.Full -> return setStatus(GuestStatus.FULL)
            JamGuest.JoinResult.Unreachable -> return setStatus(GuestStatus.UNREACHABLE)
        }
        setStatus(GuestStatus.CONNECTED)
        // The Jam takes the place of this phone's own music while the guest is in it (the own queue stays untouched underneath).
        player.connect()
        player.pause()
        clockOffsetMs = g.measureClockOffset() ?: 0L
        val cacheDir = File(context.cacheDir, "jamcache").apply { mkdirs() }
        guestCacheDir = cacheDir
        cacheDir.listFiles().orEmpty().forEach { it.delete() }
        loadedIds.value = emptySet()
        wantedIds.value = emptyList()
        val localPlayer = JamGuestPlayer(context) { id -> cacheFile(cacheDir, id).takeIf { it.isFile } }
        guestPlayer = localPlayer
        coroutineScope {
            val helpers = listOf(
                launch { prefetch(g, cacheDir) },
                // The host's clock is measured again now and then.
                launch {
                    while (true) {
                        delay(CLOCK_MS)
                        g.measureClockOffset()?.let { clockOffsetMs = it }
                    }
                },
                launch {
                    while (true) {
                        delay(TICK_MS)
                        localPlayer.tick()
                        publishRemote()
                    }
                },
            )
            pollLoop(g, localPlayer)
            helpers.forEach { it.cancel() }
        }
    }

    private suspend fun pollLoop(g: JamGuest, localPlayer: JamGuestPlayer) {
        var rev = 0L
        var failures = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            when (val result = g.pollState(rev)) {
                is JamGuest.StateResult.Ok -> {
                    rev = result.state.rev
                    failures = 0
                    val current = result.state.currentIndex.coerceAtLeast(0)
                    val ahead = result.state.queue.drop(current).take(result.state.ahead).map { it.id }
                    // The songs just before this one stay on the phone too (and are fetched after the ones ahead), so "previous" never waits.
                    val behind = result.state.queue.subList((current - JAM_KEEP_BEHIND).coerceAtLeast(0), current).map { it.id }.reversed()
                    wantedIds.value = ahead + behind
                    _proposal.value = result.state.proposal
                    _ui.update {
                        it.copy(
                            state = result.state, guestStatus = GuestStatus.CONNECTED, loaded = loadedIds.value,
                            proposal = result.state.proposal, voteResult = result.state.result, myId = result.state.me,
                        )
                    }
                    voteAlert(result.state.proposal, result.state.me)
                    localPlayer.update(result.state, clockOffsetMs, _ui.value.playLocally)
                    publishRemote()
                    val now = result.state.queue.getOrNull(result.state.currentIndex)
                    notifySong(now?.title, now?.artist)
                }
                JamGuest.StateResult.Removed -> {
                    localPlayer.update(null, clockOffsetMs, false)
                    player.setRemote(null)
                    return setStatus(GuestStatus.REMOVED)
                }
                JamGuest.StateResult.Unreachable -> {
                    failures++
                    if (failures >= 2) setStatus(GuestStatus.UNREACHABLE)
                    delay(RETRY_MS)
                }
            }
        }
    }

    /**
     * Shows the Jam's song, queue and position on the player screens of this phone (see [PlayerConnection.setRemote]):
     * what the guest sees is the same as on the host's phone.
     */
    private fun publishRemote() {
        val ui = _ui.value
        val s = ui.state
        if (ui.role != Role.GUEST || s == null || ui.guestStatus != GuestStatus.CONNECTED) {
            player.setRemote(null)
            return
        }
        val dir = guestCacheDir
        val entries = s.queue.mapIndexed { i, track ->
            val file = dir?.let { cacheFile(it, track.id) }?.takeIf { it.isFile }
            QueueEntry(
                index = i,
                mediaId = track.id,
                occurrenceId = track.id,
                title = track.title,
                artist = track.artist,
                // The cover comes out of the loaded copy of the song, so it shows as soon as the song is on this phone.
                artwork = file?.let { ArtworkRef(syntheticTrackId(track.id), it.toUri().toString(), it.lastModified()) },
                addedBy = track.addedBy.takeIf { it != s.hostName },
                sourceUri = null,
            )
        }
        val current = entries.getOrNull(s.currentIndex)
        val state = PlayerState(
            nowPlaying = current?.let { NowPlaying(it.mediaId, it.title, it.artist, "", it.artwork, 0L, null) },
            isPlaying = s.playing && ui.playLocally,
            hasNext = s.currentIndex in 0 until s.queue.size - 1,
            durationMs = guestPlayer?.durationMs() ?: 0L,
            queueIndex = s.currentIndex,
            queueSize = s.queue.size,
        )
        player.setRemote(
            PlayerConnection.RemoteView(state, entries) {
                val latest = _ui.value.state
                if (latest == null) 0L else JamSync.expectedPositionMs(latest, JamClock.nowMs() + clockOffsetMs).coerceAtLeast(0L)
            },
        )
    }

    /** A stable id for a Jam song, for the cover cache (never equal to the id of a song of the library: those are positive). */
    private fun syntheticTrackId(entryId: String): Long = -1L - (entryId.hashCode().toLong() and 0x7fffffffL)

    /** Puts the song that is playing into the Jam notification (the notification of the phone's own player is not used by the Jam). */
    private fun notifySong(title: String?, artist: String?) {
        val key = title?.let { "$it|$artist" }
        if (key == lastSongShown) return
        lastSongShown = key
        JamService.showSong(context, title, artist)
    }

    private fun cacheFile(dir: File, id: String) = File(dir, id.replace(Regex("[^A-Za-z0-9_-]"), "_"))

    /** Loads the current song and the next ones completely onto this phone, one after the other, and tells the host. */
    private suspend fun prefetch(g: JamGuest, dir: File) {
        val gone = HashSet<String>()
        while (true) {
            currentCoroutineContext().ensureActive()
            val wanted = wantedIds.value
            // Songs that left the queue are not needed any more.
            val keep = wanted.toSet()
            if (!keep.containsAll(loadedIds.value)) loadedIds.value = loadedIds.value.filter { it in keep }.toSet()
            dir.listFiles().orEmpty().filter { f -> keep.none { cacheFile(dir, it).name == f.name || cacheFile(dir, it).name + ".part" == f.name } && !isRecent(f) }.forEach { it.delete() }
            val next = wanted.firstOrNull { it !in loadedIds.value && it !in gone }
            if (next == null) {
                wantedIds.first { it != wanted }
                continue
            }
            val file = cacheFile(dir, next)
            if (file.isFile) {
                markLoaded(g, next)
                continue
            }
            val result = g.download(next, file) { done, total ->
                val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
                _ui.update { it.copy(loadProgress = fraction) }
            }
            _ui.update { it.copy(loadProgress = null) }
            when (result) {
                JamGuest.DownloadResult.Done -> markLoaded(g, next)
                JamGuest.DownloadResult.Gone -> gone += next
                JamGuest.DownloadResult.Failed -> delay(RETRY_MS)
            }
        }
    }

    private fun isRecent(file: File) = System.currentTimeMillis() - file.lastModified() < 60_000L

    private suspend fun markLoaded(g: JamGuest, id: String) {
        loadedIds.value = loadedIds.value + id
        _ui.update { it.copy(loaded = loadedIds.value) }
        publishRemote()
        g.reportReady(loadedIds.value)
    }

    /** The guest's switch: hear the Jam on this phone, or only watch the queue. */
    fun setPlayLocally(on: Boolean) {
        _ui.update { it.copy(playLocally = on) }
        guestPlayer?.update(_ui.value.state, clockOffsetMs, on)
        publishRemote()
    }

    /** Sends one song of this phone's library to the host. */
    fun addSong(track: TrackListItem) {
        val g = guest ?: return
        if (_ui.value.guestStatus != GuestStatus.CONNECTED) return
        _ui.update { it.copy(uploading = it.uploading + 1) }
        scope.launch {
            val event = withContext(Dispatchers.IO) { send(g, track) }
            _ui.update { it.copy(uploading = (it.uploading - 1).coerceAtLeast(0)) }
            _events.tryEmit(event)
        }
    }

    private suspend fun send(g: JamGuest, track: TrackListItem): Event {
        val uri = track.uri.toUri()
        val resolver = context.contentResolver
        var size = -1L
        var fileName: String? = null
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(0)
                    size = if (cursor.isNull(1)) -1L else cursor.getLong(1)
                }
            }
        } catch (e: RuntimeException) {
            // Some providers refuse the question; the file size is asked another way below.
        }
        if (size <= 0L) {
            size = try {
                resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            } catch (e: IOException) {
                -1L
            }
        }
        if (size <= 0L) return Event.SONG_UNREADABLE
        if (size > JAM_MAX_UPLOAD_BYTES) return Event.SONG_TOO_BIG
        val meta = JamGuest.UploadMeta(track.title, track.artist.orEmpty(), track.album.orEmpty(), track.durationMs, safeAudioExtension(fileName))
        val result = g.upload(meta, size) {
            try {
                resolver.openInputStream(uri)
            } catch (e: IOException) {
                null
            }
        }
        return when (result) {
            JamGuest.UploadResult.Sent -> Event.SONG_SENT
            JamGuest.UploadResult.TooBig -> Event.SONG_TOO_BIG
            JamGuest.UploadResult.NoSpace -> Event.HOST_NO_SPACE
            JamGuest.UploadResult.Unauthorized, JamGuest.UploadResult.Failed -> Event.SONG_FAILED
        }
    }

    // --- Both ---------------------------------------------------------------------------------

    /** Ends the session on this phone: the host closes it, a guest leaves it. Also clears an error screen. */
    fun leave() {
        when (_ui.value.role) {
            Role.HOST -> stopHosting()
            Role.GUEST -> {
                val g = guest
                guest = null
                guestJob?.cancel()
                guestJob = null
                g?.close()
                player.setRemote(null)
                _proposal.value = null
                JamVoteNotifier.cancel(context)
                _ui.value = Ui()
                if (g != null) scope.launch { g.leave() }
            }
            Role.NONE -> _ui.value = Ui()
        }
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_DEVICE = "device"
        const val KEY_GATE = "gate"
        const val START_DELAY_MS = 1_000L
        /** A song change within this long of the end of the song is the song ending by itself, not a skip. */
        const val NATURAL_END_MS = 6_000L
        const val HOLD_MS = 1_500L
        const val QUEUE_TIMEOUT_MS = 8_000L
        const val OLD_FILE_MS = 12L * 60 * 60 * 1000
        const val APPROVAL_WAITS = 9 // about 3 minutes
        const val RETRY_MS = 2_000L
        const val HOST_ANCHOR_MS = 2_000L
        const val CLOCK_MS = 20_000L
        const val TICK_MS = 500L
    }
}
