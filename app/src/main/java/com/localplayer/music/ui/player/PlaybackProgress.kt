package com.localplayer.music.ui.player

/**
 * Pure helpers for the progress bar. The bar should move by at most about one pixel per update, so it looks
 * continuous without being redrawn more often than it can visibly change.
 */
object PlaybackProgress {
    const val MIN_STEP_MS = 33L
    const val MAX_STEP_MS = 250L

    /** Position as a fraction of the duration, clamped to 0..1 (0 when the duration is unknown). */
    fun fraction(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    /**
     * Delay between two updates: `duration / (width * 2)`, i.e. half a pixel of travel, kept between 33 ms
     * (30 Hz) and 250 ms (4 Hz). 250 ms when the duration or the bar width is not known yet.
     * A 3-minute song on a 1000 px bar gives 90 ms.
     */
    fun stepMs(durationMs: Long, barWidthPx: Int): Long {
        if (durationMs <= 0L || barWidthPx <= 0) return MAX_STEP_MS
        return (durationMs / (barWidthPx.toLong() * 2L)).coerceIn(MIN_STEP_MS, MAX_STEP_MS)
    }
}
