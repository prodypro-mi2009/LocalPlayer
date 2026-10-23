package com.localplayer.music.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueSheetMotionTest {
    private val extent = 2400f

    @Test fun progressIsZeroWhenClosedAndOneWhenOpen() {
        assertEquals(0f, QueueSheetMotion.progress(extent, extent), 1e-6f)
        assertEquals(1f, QueueSheetMotion.progress(0f, extent), 1e-6f)
        assertEquals(0.5f, QueueSheetMotion.progress(extent / 2, extent), 1e-6f)
    }

    @Test fun progressIsClampedAndSafeWithoutExtent() {
        assertEquals(1f, QueueSheetMotion.progress(-50f, extent), 1e-6f) // overshoot above the top
        assertEquals(0f, QueueSheetMotion.progress(extent + 50f, extent), 1e-6f)
        assertEquals(0f, QueueSheetMotion.progress(10f, 0f), 1e-6f)
    }

    @Test fun scrimIsLinearUpToFortyFivePercent() {
        assertEquals(0f, QueueSheetMotion.scrimAlpha(0f), 1e-6f)
        assertEquals(0.45f, QueueSheetMotion.scrimAlpha(1f), 1e-6f)
        // Measured on the reference app: 87 % / 75 % / 66 % of the resting brightness at p = 0.30 / 0.55 / 0.73.
        assertEquals(0.87f, 1f - QueueSheetMotion.scrimAlpha(0.30f), 0.015f)
        assertEquals(0.75f, 1f - QueueSheetMotion.scrimAlpha(0.55f), 0.015f)
        assertEquals(0.67f, 1f - QueueSheetMotion.scrimAlpha(0.73f), 0.015f)
    }

    @Test fun contentScalesDownSlightly() {
        assertEquals(1f, QueueSheetMotion.contentScale(0f), 1e-6f)
        assertEquals(0.978f, QueueSheetMotion.contentScale(1f), 1e-6f)
        assertEquals(1f - 0.022f * 0.55f, QueueSheetMotion.contentScale(0.55f), 1e-6f)
    }

    @Test fun toolbarOnlyNearlyOpenAndOnlyWhileOpening() {
        assertFalse(QueueSheetMotion.toolbarVisible(0.5f, isOpen = true))
        assertFalse(QueueSheetMotion.toolbarVisible(0.87f, isOpen = true))
        assertTrue(QueueSheetMotion.toolbarVisible(0.88f, isOpen = true))
        assertTrue(QueueSheetMotion.toolbarVisible(1f, isOpen = true))
        // Closing started: the target is "closed", so the toolbar leaves first even though p is still ~1.
        assertFalse(QueueSheetMotion.toolbarVisible(1f, isOpen = false))
    }

    @Test fun hapticPointIsAtAQuarter() {
        assertFalse(QueueSheetMotion.crossedHapticPoint(0.24f))
        assertTrue(QueueSheetMotion.crossedHapticPoint(0.25f))
    }

    @Test fun quickFlickFromSmallDistanceOpens_butTinyTouchDoesNot() {
        val minFling = 48f * 3f // 48 dp at 3x density
        // 200 px up (well below 25 %) with a fast upward flick (3000 px/s): the coast carries it open.
        assertTrue(SheetPhysics.shouldOpen(200f, 3000f, extent, minFling))
        // 20 px (under the minimum) with the same flick: the coast is ignored, it falls back.
        assertFalse(SheetPhysics.shouldOpen(20f, 3000f, extent, minFling))
        // Slow drag that did not get far falls back; past halfway it opens even when nearly stopped.
        assertFalse(SheetPhysics.shouldOpen(300f, 50f, extent, minFling))
        assertTrue(SheetPhysics.shouldOpen(0.74f * extent, 100f, extent, minFling))
        // A flick the other way always cancels.
        assertFalse(SheetPhysics.shouldOpen(0.6f * extent, -400f, extent, minFling))
    }

    @Test fun pullingTheOpenSheetDownClosesOnProjectedPosition() {
        val minFling = 144f
        assertTrue(SheetPhysics.shouldDismiss(0.3f * extent, 0f, extent, minFling))
        assertFalse(SheetPhysics.shouldDismiss(0.1f * extent, 0f, extent, minFling))
        assertTrue(SheetPhysics.shouldDismiss(300f, 3000f, extent, minFling)) // flick down closes
        assertFalse(SheetPhysics.shouldDismiss(0.5f * extent, -400f, extent, minFling)) // flick up cancels
    }
}
