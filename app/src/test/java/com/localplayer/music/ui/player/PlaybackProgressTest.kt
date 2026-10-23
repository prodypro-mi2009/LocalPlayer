package com.localplayer.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressTest {
    @Test fun fraction_isClampedToZeroOne() {
        assertEquals(0.5f, PlaybackProgress.fraction(90_000, 180_000), 0.0001f)
        assertEquals(0f, PlaybackProgress.fraction(-5, 180_000), 0f)
        assertEquals(1f, PlaybackProgress.fraction(200_000, 180_000), 0f)
        assertEquals(0f, PlaybackProgress.fraction(0, 180_000), 0f)
        assertEquals(1f, PlaybackProgress.fraction(180_000, 180_000), 0f)
    }

    @Test fun fraction_unknownDurationIsZero() {
        assertEquals(0f, PlaybackProgress.fraction(5_000, 0), 0f)
        assertEquals(0f, PlaybackProgress.fraction(5_000, -1), 0f)
    }

    @Test fun step_threeMinuteSongOnA1000pxBarIs90ms() {
        assertEquals(90L, PlaybackProgress.stepMs(durationMs = 180_000, barWidthPx = 1000))
    }

    @Test fun step_hasA30HzFloorAndA4HzCap() {
        // very short track on a wide bar -> would be < 33 ms -> floor
        assertEquals(PlaybackProgress.MIN_STEP_MS, PlaybackProgress.stepMs(durationMs = 10_000, barWidthPx = 1000))
        // very long track on a narrow bar -> would be > 250 ms -> cap
        assertEquals(PlaybackProgress.MAX_STEP_MS, PlaybackProgress.stepMs(durationMs = 3_600_000, barWidthPx = 500))
        assertEquals(33L, PlaybackProgress.MIN_STEP_MS)
        assertEquals(250L, PlaybackProgress.MAX_STEP_MS)
    }

    @Test fun step_unknownDurationOrWidthUsesTheSlowRate() {
        assertEquals(250L, PlaybackProgress.stepMs(0, 1000))
        assertEquals(250L, PlaybackProgress.stepMs(-1, 1000))
        assertEquals(250L, PlaybackProgress.stepMs(180_000, 0))
        assertEquals(250L, PlaybackProgress.stepMs(180_000, -10))
        assertEquals(250L, PlaybackProgress.stepMs(0, 0))
    }

    @Test fun step_movesTheBarByAtMostOnePixel() {
        for (duration in listOf(60_000L, 180_000L, 240_000L, 600_000L)) {
            for (width in listOf(360, 720, 1080, 1440)) {
                val step = PlaybackProgress.stepMs(duration, width)
                val pxPerStep = step.toFloat() / duration * width
                // inside the 33..250 ms band the bar moves <= 1 px per step (the band edges may differ)
                if (step in (PlaybackProgress.MIN_STEP_MS + 1) until PlaybackProgress.MAX_STEP_MS) {
                    assertTrue("$duration/$width -> $pxPerStep px", pxPerStep <= 1f)
                }
            }
        }
    }
}
