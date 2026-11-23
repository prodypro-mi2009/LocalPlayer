package com.localplayer.music.playback

/** The arithmetic of the fade between songs. Pure Kotlin, tested on the JVM. */
object FadeMath {
    const val MIN_SECONDS = 1
    const val MAX_SECONDS = 10
    const val DEFAULT_SECONDS = 3

    fun clampSeconds(s: Int): Int = s.coerceIn(MIN_SECONDS, MAX_SECONDS)

    /**
     * Milliseconds from now until the fade-out must start, or null when no fade should be scheduled (unknown or too
     * short a song: it needs to be at least four times as long as the fade, so short songs are never swallowed).
     * 0 = already inside the fade zone: start at once.
     */
    fun msUntilFadeOut(durationMs: Long, positionMs: Long, fadeMs: Long): Long? {
        if (durationMs <= 0 || fadeMs <= 0 || durationMs < 4 * fadeMs) return null
        return (durationMs - positionMs - fadeMs).coerceAtLeast(0L)
    }

    /** Volume factor [from] -> [to] after [elapsedMs] of a ramp that lasts [totalMs] (linear, clamped to 0..1). */
    fun factor(from: Float, to: Float, elapsedMs: Long, totalMs: Long): Float {
        if (totalMs <= 0) return to
        val t = (elapsedMs.toFloat() / totalMs).coerceIn(0f, 1f)
        return (from + (to - from) * t).coerceIn(0f, 1f)
    }

    /** How long a ramp that starts at [from] needs to reach [to], so a ramp that was cut short is not slower than a full one. */
    fun rampMs(from: Float, to: Float, fadeMs: Long): Long = (kotlin.math.abs(to - from) * fadeMs).toLong().coerceAtLeast(1L)
}
