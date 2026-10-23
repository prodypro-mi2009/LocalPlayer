package com.localplayer.music.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetMorphTest {
    private fun frame(p: Float) = SheetMorph.frame(p, 2000f, 1800f, 1900f, 16f, 40f)

    @Test fun closedIsTheMiniPlayerRect() {
        val f = frame(0f)
        assertEquals(1800f, f.top, 0.01f); assertEquals(1900f, f.bottom, 0.01f)
        assertEquals(16f, f.inset, 0.01f); assertEquals(40f, f.corner, 0.01f); assertEquals(0f, f.navShift, 0.01f)
    }

    @Test fun openIsFullScreen() {
        val f = frame(1f)
        assertEquals(0f, f.top, 0.01f); assertEquals(2000f, f.bottom, 0.01f)
        assertEquals(0f, f.inset, 0.01f); assertEquals(0f, f.corner, 0.01f); assertEquals(100f, f.navShift, 0.01f)
    }

    @Test fun tabBarStaysPutUntilTheTopReachesTheScreenTop() {
        val f = frame(SheetMorph.NAV_START)
        assertEquals(0f, f.top, 0.01f); assertEquals(0f, f.navShift, 0.01f)
        assertEquals(0f, frame(0.4f).navShift, 0.01f)
        assertTrue(frame(0.4f).top > 0f)
    }

    @Test fun tabBarMovesOnlyAfterNavStart() {
        assertTrue(frame(0.85f).navShift > 0f)
        assertEquals(1900f, frame(0.5f).bottom, 0.01f)
    }

    @Test fun fadeIsDoneEarly() {
        assertEquals(0f, SheetMorph.fade(0f), 0.001f); assertEquals(1f, SheetMorph.fade(0.3f), 0.001f)
    }
}
