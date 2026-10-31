package com.localplayer.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FadeMathTest {
    @Test fun clampsSeconds() {
        assertEquals(1, FadeMath.clampSeconds(-5)); assertEquals(10, FadeMath.clampSeconds(99)); assertEquals(4, FadeMath.clampSeconds(4))
    }

    @Test fun fadeOutStartsFadeMsBeforeTheEnd() {
        assertEquals(176_000L, FadeMath.msUntilFadeOut(200_000, 20_000, 4_000))
        assertEquals(0L, FadeMath.msUntilFadeOut(200_000, 199_000, 4_000))      // already inside the zone: now
        assertEquals(0L, FadeMath.msUntilFadeOut(200_000, 250_000, 4_000))
    }

    @Test fun shortOrUnknownSongsAreNotFaded() {
        assertNull(FadeMath.msUntilFadeOut(10_000, 0, 3_000))     // shorter than 4 x fade
        assertNull(FadeMath.msUntilFadeOut(-1, 0, 3_000))
        assertNull(FadeMath.msUntilFadeOut(100_000, 0, 0))
    }

    @Test fun rampIsLinearAndClamped() {
        assertEquals(1f, FadeMath.factor(1f, 0f, 0, 1000), 1e-6f)
        assertEquals(0.5f, FadeMath.factor(1f, 0f, 500, 1000), 1e-6f)
        assertEquals(0f, FadeMath.factor(1f, 0f, 5000, 1000), 1e-6f)
        assertEquals(0.75f, FadeMath.factor(0f, 1f, 750, 1000), 1e-6f)
        assertEquals(1f, FadeMath.factor(0f, 1f, 10, 0), 1e-6f)
    }

    @Test fun cutShortRampTakesProportionalTime() {
        assertEquals(3000L, FadeMath.rampMs(1f, 0f, 3000))
        assertEquals(1500L, FadeMath.rampMs(0.5f, 0f, 3000))
    }
}
