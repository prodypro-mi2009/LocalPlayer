package com.localplayer.music.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRateLimitTest {
    @Test fun exhaustedLimitIsRateLimit() = assertTrue(UpdateChecker.isRateLimit("0", null))

    @Test fun retryAfterIsRateLimit() = assertTrue(UpdateChecker.isRateLimit(null, "60"))

    @Test fun plainForbiddenIsNot() {
        assertFalse(UpdateChecker.isRateLimit(null, null))
        assertFalse(UpdateChecker.isRateLimit("42", null))
        assertFalse(UpdateChecker.isRateLimit("42", " "))
    }
}
