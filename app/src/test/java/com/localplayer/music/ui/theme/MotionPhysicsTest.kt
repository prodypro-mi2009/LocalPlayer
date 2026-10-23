package com.localplayer.music.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPhysicsTest {
    // ---- spring conversion -------------------------------------------------------------------------
    @Test fun stiffness_halfSecondResponseIsAbout158() {
        assertEquals(157.9f, MotionPhysics.stiffnessForResponse(0.5f), 0.1f)
    }

    @Test fun stiffness_shorterResponseIsStiffer() {
        assertEquals(246.7f, MotionPhysics.stiffnessForResponse(0.4f), 0.2f)   // settle (critical)
        assertEquals(438.6f, MotionPhysics.stiffnessForResponse(0.3f), 0.3f)   // settle (momentum)
        assertEquals(986.96f, MotionPhysics.stiffnessForResponse(0.2f), 0.5f)  // press
        assertTrue(MotionPhysics.stiffnessForResponse(0.2f) > MotionPhysics.stiffnessForResponse(0.4f))
    }

    // ---- momentum projection -----------------------------------------------------------------------
    @Test fun project_matchesTheFormula() {
        assertEquals(0f, MotionPhysics.project(0f), 0.0001f)
        // 1000 px/s -> 1 * 0.998 / 0.002 = 499 px
        assertEquals(499f, MotionPhysics.project(1000f), 0.5f)
        assertEquals(-499f, MotionPhysics.project(-1000f), 0.5f)
        assertEquals(998f, MotionPhysics.project(2000f), 1f)
    }

    @Test fun project_isLinearInVelocity() {
        assertEquals(2f * MotionPhysics.project(300f), MotionPhysics.project(600f), 0.01f)
    }

    @Test fun momentum_thresholdIs700InEitherDirection() {
        assertFalse(MotionPhysics.carriesMomentum(699f))
        assertTrue(MotionPhysics.carriesMomentum(700f))
        assertTrue(MotionPhysics.carriesMomentum(-700f))
        assertFalse(MotionPhysics.carriesMomentum(-699f))
        assertFalse(MotionPhysics.carriesMomentum(0f))
    }

    // ---- sheet: pull DOWN to dismiss ---------------------------------------------------------------
    private val extent = 2000f

    @Test fun dismiss_farEnoughWithoutVelocity() {
        assertTrue(SheetPhysics.shouldDismiss(offsetPx = 501f, velocityPxPerSec = 0f, extentPx = extent))
        assertFalse(SheetPhysics.shouldDismiss(offsetPx = 499f, velocityPxPerSec = 0f, extentPx = extent))
    }

    @Test fun dismiss_downwardFlickAddsTheCoast() {
        // 100 px pulled + 1000 px/s flick (coast ~499) = ~599 > 500
        assertTrue(SheetPhysics.shouldDismiss(100f, 1000f, extent))
        assertFalse(SheetPhysics.shouldDismiss(100f, 500f, extent)) // 100 + ~249 = 349
    }

    @Test fun dismiss_upwardFlickAlwaysCancels() {
        assertFalse(SheetPhysics.shouldDismiss(offsetPx = 1500f, velocityPxPerSec = -301f, extentPx = extent))
        // a slow upward drift (-300 or slower) does not force the cancel by itself
        assertTrue(SheetPhysics.shouldDismiss(offsetPx = 1500f, velocityPxPerSec = -300f, extentPx = extent))
    }

    @Test fun dismiss_minFlingGateIgnoresTheCoastForTinyPulls() {
        val min = 150f
        // 100 px pulled is below the gate: the 1000 px/s flick does NOT count
        assertFalse(SheetPhysics.shouldDismiss(100f, 1000f, extent, minFlingOffsetPx = min))
        // at the gate the coast counts again
        assertTrue(SheetPhysics.shouldDismiss(150f, 1000f, extent, minFlingOffsetPx = min))
        // the gate never blocks a far pull
        assertTrue(SheetPhysics.shouldDismiss(600f, 0f, extent, minFlingOffsetPx = min))
    }

    // ---- sheet: drag UP to open (mirror) -----------------------------------------------------------
    @Test fun open_quarterWayWithoutVelocity() {
        assertTrue(SheetPhysics.shouldOpen(openedPx = 501f, upwardVelocityPxPerSec = 0f, extentPx = extent))
        assertFalse(SheetPhysics.shouldOpen(499f, 0f, extent))
    }

    @Test fun open_upwardFlickAddsTheCoast() {
        assertTrue(SheetPhysics.shouldOpen(100f, 1000f, extent))   // 100 + ~499 > 500
        assertFalse(SheetPhysics.shouldOpen(100f, 500f, extent))   // 100 + ~249
    }

    @Test fun open_downwardFlickAlwaysCancels() {
        assertFalse(SheetPhysics.shouldOpen(openedPx = 1500f, upwardVelocityPxPerSec = -301f, extentPx = extent))
        assertTrue(SheetPhysics.shouldOpen(openedPx = 1500f, upwardVelocityPxPerSec = -300f, extentPx = extent))
    }

    @Test fun open_minFlingGateIgnoresTheCoastForTinyPulls() {
        val min = 150f
        assertFalse(SheetPhysics.shouldOpen(100f, 1000f, extent, minFlingOpenedPx = min))
        assertTrue(SheetPhysics.shouldOpen(150f, 1000f, extent, minFlingOpenedPx = min))
    }

    @Test fun open_halfWayOpensWithoutVelocityUnlessFlickedDown() {
        assertTrue(SheetPhysics.shouldOpen(1000f, 0f, extent))
        assertTrue(SheetPhysics.shouldOpen(1200f, -100f, extent))
        assertFalse(SheetPhysics.shouldOpen(1200f, -400f, extent)) // a downward flick still cancels
        // just under half and a little downward drift: the 25 % rule alone would open (999 > 500), so it opens
        assertTrue(SheetPhysics.shouldOpen(999f, 0f, extent))
    }

    @Test fun openAndDismiss_areMirrorImages() {
        // pulled-up distance d from closed == (extent - d) from open; the same release speed mirrored
        for (v in listOf(-500f, 0f, 400f, 1200f)) {
            for (d in listOf(0f, 120f, 480f, 700f)) {
                val opens = SheetPhysics.shouldOpen(openedPx = d, upwardVelocityPxPerSec = v, extentPx = extent)
                if (d < 0.5f * extent && v >= -300f) {
                    // below the half-way shortcut the open rule equals the dismiss rule applied to the same numbers
                    assertEquals(SheetPhysics.shouldDismiss(d, v, extent), opens)
                }
            }
        }
    }
}
