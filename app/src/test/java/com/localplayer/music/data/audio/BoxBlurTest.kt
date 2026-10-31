package com.localplayer.music.data.audio

import com.localplayer.music.data.artwork.BoxBlur
import com.localplayer.music.data.artwork.Saturation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxBlurTest {
    @Test fun uniformImageStaysUniform() {
        val px = IntArray(16 * 16) { 0xFF336699.toInt() }
        assertTrue(BoxBlur.blur(px, 16, 16, 3, 3).all { it == 0xFF336699.toInt() })
    }
    @Test fun aSingleBrightPixelIsSpreadOut() {
        val px = IntArray(16 * 16) { 0xFF000000.toInt() }
        px[8 * 16 + 8] = 0xFFFFFFFF.toInt()
        val out = BoxBlur.blur(px, 16, 16, 3, 2)
        val centre = out[8 * 16 + 8] and 0xFF
        assertTrue(centre in 1..254)
        assertTrue((out[8 * 16 + 9] and 0xFF) > 0)
    }
    @Test fun sizeIsPreservedAndAlphaKept() {
        val out = BoxBlur.blur(IntArray(40 * 40) { 0xFF102030.toInt() }, 40, 40, 3, 3)
        assertEquals(1600, out.size); assertEquals(0xFF, out[0] ushr 24)
    }

    @Test fun saturationBoostMovesAwayFromGreyAndKeepsGreyAndAlpha() {
        val grey = Saturation.boost(intArrayOf(0xFF808080.toInt()), 1.15f)[0]
        assertEquals(0xFF808080.toInt(), grey)
        val boosted = Saturation.boost(intArrayOf(0xFFC06060.toInt()), 1.15f)[0]
        assertTrue(((boosted shr 16) and 0xFF) > 0xC0)   // red up
        assertTrue((boosted and 0xFF) < 0x60)             // blue down
        assertEquals(0xFF, boosted ushr 24)
    }
    @Test fun saturationFactorOneChangesNothing() {
        val px = intArrayOf(0xFF336699.toInt(), 0x80FFAA00.toInt())
        assertTrue(px.contentEquals(Saturation.boost(px, 1f)))
    }
}
