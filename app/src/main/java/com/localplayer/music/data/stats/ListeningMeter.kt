package com.localplayer.music.data.stats

import kotlin.math.max
import kotlin.math.min

/**
 * The ONE rule that decides when a listen counts as a play (used for play counts, Top 20 and Your Mix).
 * A play needs [MAX_THRESHOLD_MS] (30 s) of REAL playback, or half of the song when it is shorter than a minute,
 * but never less than [MIN_THRESHOLD_MS] (3 s, or the whole song if it is shorter than that). So a fast skip is
 * not a play and short songs are not penalised. An unknown duration uses the 30 s rule.
 */
object PlayCriterion {
    const val MAX_THRESHOLD_MS = 30_000L
    const val MIN_THRESHOLD_MS = 3_000L

    fun thresholdMs(durationMs: Long): Long {
        if (durationMs <= 0L) return MAX_THRESHOLD_MS
        val base = min(MAX_THRESHOLD_MS, durationMs / 2)
        return max(base, min(MIN_THRESHOLD_MS, durationMs))
    }
}

/**
 * Pure bookkeeping of real listening time, driven by the player's events with an explicit monotonic clock
 * (no Android in here, so it is unit tested on the JVM).
 *
 *  - Time only accumulates between "playing" and "not playing" (pause, stop, buffering, end), so being in the
 *    foreground, seeking or skipping never adds time by themselves.
 *  - Every unflushed millisecond is handed to the [Sink] exactly once ([flush] resets the accumulator), so repeated
 *    callbacks or repeated ticks cannot count anything twice.
 *  - One play instance (one start of an item) counts at most once, when [PlayCriterion] is met.
 */
class ListeningMeter(private val sink: Sink) {
    fun interface Sink {
        /** [listenedMs] new real listening time of [uri]; [plays] is 1 when this flush completed the play criterion. */
        fun record(uri: String, listenedMs: Long, plays: Int)
    }

    private var uri: String? = null
    private var durationMs = 0L
    private var since = NOT_PLAYING
    private var unflushed = 0L
    private var playedInInstance = 0L
    private var counted = false
    private var pendingPlay = 0

    val isRunning: Boolean get() = since != NOT_PLAYING

    /** A new play instance starts (track change, repeat of the same item, first item after a restart). */
    fun startItem(uri: String?, durationMs: Long, now: Long, playing: Boolean) {
        flush(now)
        this.uri = uri
        this.durationMs = durationMs
        playedInInstance = 0L
        counted = false
        pendingPlay = 0
        since = if (playing && uri != null) now else NOT_PLAYING
    }

    fun setDuration(durationMs: Long) {
        if (durationMs > 0L) this.durationMs = durationMs
    }

    /** Player says audio is (not) really playing now. Repeated identical states are ignored. */
    fun setPlaying(playing: Boolean, now: Long) {
        if (playing) {
            if (since == NOT_PLAYING && uri != null) since = now
        } else if (since != NOT_PLAYING) {
            flush(now)
            since = NOT_PLAYING
        }
    }

    /** Periodic save while playing; keeps at most one interval of listening time at risk if the process dies. */
    fun tick(now: Long) = flush(now)

    fun release(now: Long) {
        flush(now)
        since = NOT_PLAYING
        uri = null
    }

    private fun sync(now: Long) {
        if (since == NOT_PLAYING) return
        val delta = (now - since).coerceIn(0L, MAX_STEP_MS)
        since = now
        unflushed += delta
        playedInInstance += delta
        if (!counted && playedInInstance >= PlayCriterion.thresholdMs(durationMs)) {
            counted = true
            pendingPlay = 1
        }
    }

    private fun flush(now: Long) {
        sync(now)
        val current = uri
        if (current != null && (unflushed > 0L || pendingPlay > 0)) sink.record(current, unflushed, pendingPlay)
        unflushed = 0L
        pendingPlay = 0
    }

    private companion object {
        const val NOT_PLAYING = Long.MIN_VALUE
        /** A single step longer than this is a clock/thread stall, not listening (the periodic tick is 10 s). */
        const val MAX_STEP_MS = 120_000L
    }
}
