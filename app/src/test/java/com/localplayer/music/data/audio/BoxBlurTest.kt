package com.localplayer.music.data.audio

import com.localplayer.music.data.artwork.BoxBlur
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
}
