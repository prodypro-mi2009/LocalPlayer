package com.localplayer.music.data.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkRetryPolicyTest {
    @Test
    fun firstFailureIsRetriedAfterTheShortDelay() {
        val f = ArtworkRetryPolicy.after(null, 1_000L)
        assertEquals(1, f.attempts)
        assertFalse(ArtworkRetryPolicy.mayRetry(f, 1_000L + 14_999L))
        assertTrue(ArtworkRetryPolicy.mayRetry(f, 1_000L + 15_000L))
    }

    @Test
    fun delayGrowsAndRetriesAreBounded() {
        var f = ArtworkRetryPolicy.after(null, 0L)
        f = ArtworkRetryPolicy.after(f, 0L)
        assertEquals(2 * 60_000L, f.retryAtMs)
        f = ArtworkRetryPolicy.after(f, 0L)
        assertEquals(15 * 60_000L, f.retryAtMs)
        f = ArtworkRetryPolicy.after(f, 0L)
        assertEquals(ArtworkRetryPolicy.MAX_ATTEMPTS, f.attempts)
        assertFalse(ArtworkRetryPolicy.mayRetry(f, Long.MAX_VALUE))
    }
}
