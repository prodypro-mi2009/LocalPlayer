package com.localplayer.music.data.artwork

/**
 * Bounded retry/backoff for artwork reads that FAILED (as opposed to files that simply have no
 * artwork). A temporary failure is retried after 15 s, then 2 min, then 15 min, and after
 * [MAX_ATTEMPTS] failures it is left alone until the process restarts or the file changes.
 */
object ArtworkRetryPolicy {
    const val MAX_ATTEMPTS = 4
    private val DELAYS_MS = longArrayOf(15_000L, 2 * 60_000L, 15 * 60_000L)

    data class Failure(val attempts: Int, val retryAtMs: Long)

    /** The failure record after one more failed read at [nowMs]. */
    fun after(previous: Failure?, nowMs: Long): Failure {
        val attempts = (previous?.attempts ?: 0) + 1
        val delay = DELAYS_MS[(attempts - 1).coerceAtMost(DELAYS_MS.size - 1)]
        return Failure(attempts, nowMs + delay)
    }

    fun mayRetry(failure: Failure, nowMs: Long): Boolean =
        failure.attempts < MAX_ATTEMPTS && nowMs >= failure.retryAtMs
}
