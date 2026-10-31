package com.localplayer.music.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkToneTest {
    private fun argb(r: Int, g: Int, b: Int, a: Int = 255) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun green(c: Int) = (c shr 8) and 0xFF
    private fun blue(c: Int) = c and 0xFF
    private fun lightness(c: Int): Double =
        (maxOf(red(c), green(c), blue(c)) + minOf(red(c), green(c), blue(c))) / 510.0

    @Test fun greyCoverStaysGrey() {
        val tone = ArtworkTone.mix(IntArray(256) { argb(100, 100, 100) })!!
        assertEquals(red(tone), green(tone))
        assertEquals(green(tone), blue(tone))
    }

    @Test fun twoColoursAreMixedNotPickedOne() {
        // Half blue, half red (both dark enough to keep): the result has BOTH, in the middle.
        val tone = ArtworkTone.mix(IntArray(100) { i -> if (i < 50) argb(30, 30, 200) else argb(200, 30, 30) })!!
        assertTrue(red(tone) in 100..130)
        assertTrue(blue(tone) in 100..130)
    }

    @Test fun mostlyWhiteRedCoverGivesAReddishToneNotNearWhite() {
        // 78 % white leather, 14 % red lettering, 8 % black (the GRISELDA cover).
        val pixels = IntArray(100) { i ->
            when {
                i < 78 -> argb(235, 235, 235)
                i < 92 -> argb(190, 30, 35)
                else -> argb(25, 25, 25)
            }
        }
        val tone = ArtworkTone.mix(pixels)!!
        assertTrue("red ${red(tone)} must clearly beat green ${green(tone)}", red(tone) - green(tone) > 30)
        assertTrue("lightness ${lightness(tone)}", lightness(tone) <= ArtworkTone.MAX_LIGHTNESS + 0.01)
    }

    @Test fun whiteBelowTheCapIsNotTouched() {
        // 20 % white is below the 40 % cap: the plain average of all pixels (dark enough not to be limited).
        val pixels = IntArray(100) { i -> if (i < 20) argb(240, 240, 240) else argb(20, 30, 60) }
        val tone = ArtworkTone.mix(pixels)!!
        val plainBlue = (20 * 240 + 80 * 60) / 100
        assertEquals(plainBlue.toDouble(), blue(tone).toDouble(), 2.0)
    }

    @Test fun whiteAboveTheCapIsReducedToTheCap() {
        // With the weights capped at 40 % the average of white (240) and blue (120) is 0.4*240 + 0.6*120 = 168.
        val pixels = IntArray(100) { i -> if (i < 90) argb(240, 240, 240) else argb(0, 0, 120) }
        val tone = ArtworkTone.mix(pixels, whiteCap = 0.40f)!!
        // Without the cap the blue channel would be 0.9*240 + 0.1*120 = 228 (then limited); with it, blue stays > red.
        assertTrue(blue(tone) > red(tone))
    }

    @Test fun allWhiteCoverIsALimitedGreyNotWhite() {
        val tone = ArtworkTone.mix(IntArray(64) { argb(255, 255, 255) })!!
        assertTrue(lightness(tone) <= ArtworkTone.MAX_LIGHTNESS + 0.01)
        assertEquals(red(tone), blue(tone))
    }

    @Test fun darkCoverIsKeptAsIs() {
        val tone = ArtworkTone.mix(IntArray(64) { argb(20, 40, 60) })!!
        assertEquals(20, red(tone))
        assertEquals(60, blue(tone))
    }

    @Test fun limitingLightnessKeepsTheHue() {
        val tone = ArtworkTone.mix(IntArray(64) { argb(250, 120, 120) })!! // light red
        assertTrue(red(tone) > green(tone) && red(tone) > blue(tone))
        assertTrue(lightness(tone) <= ArtworkTone.MAX_LIGHTNESS + 0.01)
    }

    @Test fun transparentPixelsAreIgnored() {
        assertNull(ArtworkTone.mix(IntArray(64) { argb(255, 0, 0, a = 0) }))
        val tone = ArtworkTone.mix(IntArray(10) { i -> if (i < 5) argb(255, 0, 0, a = 0) else argb(20, 40, 60) })
        assertNotNull(tone)
        assertEquals(20, red(tone!!))
    }

    @Test fun resultIsOpaque() {
        assertEquals(0xFF, ArtworkTone.mix(IntArray(64) { argb(10, 200, 90) })!! ushr 24)
    }
}
