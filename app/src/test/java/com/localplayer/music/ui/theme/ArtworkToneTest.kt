package com.localplayer.music.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkToneTest {
    private fun argb(r: Int, g: Int, b: Int, a: Int = 255) = (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun vividColourWinsOverGreyAndBlack() {
        val pixels = IntArray(256) { i ->
            when {
                i < 40 -> argb(200, 30, 40)      // red, vivid
                i < 140 -> argb(128, 128, 128)   // grey
                else -> argb(5, 5, 5)            // near black
            }
        }
        val seed = ArtworkTone.fromPixels(pixels)
        assertNotNull(seed)
        val red = (seed!! shr 16) and 0xFF
        val green = (seed shr 8) and 0xFF
        assertTrue(red > 150 && green < 80)
    }

    @Test fun majorityVividHueIsChosen() {
        val pixels = IntArray(100) { i -> if (i < 70) argb(20, 160, 220) else argb(230, 40, 40) }
        val seed = ArtworkTone.fromPixels(pixels)!!
        assertTrue((seed and 0xFF) > 150) // blue channel dominant
    }

    @Test fun greyscaleHasNoSeed() {
        assertNull(ArtworkTone.fromPixels(IntArray(256) { argb(100, 100, 100) }))
    }

    @Test fun transparentPixelsAreIgnored() {
        assertNull(ArtworkTone.fromPixels(IntArray(64) { argb(255, 0, 0, a = 0) }))
    }

    @Test fun resultIsOpaque() {
        val seed = ArtworkTone.fromPixels(IntArray(64) { argb(10, 200, 90) })!!
        assertEquals(0xFF, seed ushr 24)
    }
}
