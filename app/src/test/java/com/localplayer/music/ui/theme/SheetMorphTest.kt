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

    @Test fun topEdgeReachesTheScreenTopAtTopDone() {
        assertEquals(0f, frame(SheetMorph.TOP_DONE).top, 0.01f)
        assertTrue(frame(0.4f).top > 0f)
    }

    @Test fun bottomIsFullyCoveredWhenTheTopIsAtNinetyPercent() {
        // top edge at 90 % of its way <=> progress = 0.9 * TOP_DONE
        val f = frame(0.9f * SheetMorph.TOP_DONE)
        assertEquals(180f, f.top, 0.5f)            // 1800 * (1 - 0.9)
        assertEquals(2000f, f.bottom, 0.01f)       // already at the screen bottom
        assertEquals(100f, f.navShift, 0.01f)
    }

    @Test fun bottomFollowsTheTopWithAGentleLag() {
        // top at 50 % -> bottom roughly a fifth to a quarter of its way (the "5 up, 2 down" feel)
        val share = SheetMorph.bottomShare(0.5f)
        assertTrue("share=$share", share in 0.18f..0.28f)
        assertEquals(0f, SheetMorph.bottomShare(0f), 0.0001f)
        assertEquals(1f, SheetMorph.bottomShare(1f), 0.0001f)
        assertEquals(1f, SheetMorph.bottomShare(SheetMorph.BOTTOM_AT_TOP), 0.0001f)
    }

    @Test fun edgesNeverMoveBackwardsAndTheBottomSettlesSmoothly() {
        var lastTop = Float.MAX_VALUE
        var lastBottom = -1f
        var p = 0f
        while (p <= 1.0001f) {
            val f = frame(p.coerceAtMost(1f))
            assertTrue(f.top <= lastTop + 0.001f); assertTrue(f.bottom >= lastBottom - 0.001f)
            lastTop = f.top; lastBottom = f.bottom
            p += 0.01f
        }
        // no jump at the end: the last step of the bottom edge is tiny
        val before = frame(0.99f * SheetMorph.BOTTOM_AT_TOP * SheetMorph.TOP_DONE).bottom
        assertEquals(2000f, before, 1.5f)
    }

    @Test fun cornersStayBigUntilTheMiddleAndRelaxAtTheEnd() {
        assertEquals(40f, frame(SheetMorph.CORNER_RELAX_FROM).corner, 0.01f)
        assertTrue(frame(0.8f).corner < 40f)
        assertEquals(0f, frame(1f).corner, 0.01f)
    }

    @Test fun fadeIsDoneEarly() {
        assertEquals(0f, SheetMorph.fade(0f), 0.001f); assertEquals(1f, SheetMorph.fade(0.3f), 0.001f)
    }
}
