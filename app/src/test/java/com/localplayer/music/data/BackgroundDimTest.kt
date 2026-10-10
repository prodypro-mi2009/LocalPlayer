package com.localplayer.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundDimTest {
    @Test fun clampsIntoRange() {
        assertEquals(0f, AppSettings.clampDim(-3f), 0f)
        assertEquals(1f, AppSettings.clampDim(7f), 0f)
        assertEquals(0.4f, AppSettings.clampDim(0.4f), 0f)
    }
    @Test fun nanFallsBackToDefault() {
        assertEquals(AppSettings.DEFAULT_DIM, AppSettings.clampDim(Float.NaN), 0f)
    }
}
