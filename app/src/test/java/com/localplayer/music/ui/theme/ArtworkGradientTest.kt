package com.localplayer.music.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkGradientTest {
    @Test fun fullTintAtTheTopAndNoneAtTheBottom() {
        assertEquals(ArtworkGradient.TOP_ALPHA, ArtworkGradient.alpha(0f), 1e-6f)
        assertEquals(0f, ArtworkGradient.alpha(1f), 1e-6f)
        assertEquals(0f, ArtworkGradient.alpha(ArtworkGradient.FADE_END), 1e-6f)
    }

    @Test fun neverIncreasesWhileGoingDown() {
        var previous = Float.MAX_VALUE
        for (i in 0..200) {
            val a = ArtworkGradient.alpha(i / 200f)
            assertTrue("alpha rose at ${i / 200f}", a <= previous + 1e-6f)
            previous = a
        }
    }

    @Test fun almostBlackStartsAroundTwoFifthsOfTheHeight() {
        // Tint below 15 % at 2/5, but still clearly visible (above 8 %): the "almost black" area begins there.
        val atTwoFifths = ArtworkGradient.alpha(0.4f)
        assertTrue("alpha at 2/5 = $atTwoFifths", atTwoFifths in 0.08f..0.15f)
        // And the way down is gradual: a real tint is still there at 1/3 (the old version was black from about here).
        assertTrue(ArtworkGradient.alpha(1f / 3f) > 0.25f)
    }

    @Test fun noSteepDropBetweenNeighbouringStops() {
        val stops = ArtworkGradient.stops()
        for (i in 1 until stops.size) {
            val drop = stops[i - 1].second - stops[i].second
            assertTrue("drop $drop between stop ${i - 1} and $i", drop < 0.2f)
            assertTrue(stops[i].first >= stops[i - 1].first)
        }
        assertEquals(0f, stops.first().first, 0f)
        assertEquals(1f, stops.last().first, 0f)
    }

    @Test fun brightTonesAreWeakenedDarkTonesAreNot() {
        assertEquals(ArtworkGradient.TOP_ALPHA, ArtworkGradient.topAlphaFor(0.1f), 1e-6f)
        assertEquals(ArtworkGradient.TOP_ALPHA, ArtworkGradient.topAlphaFor(0.45f), 1e-6f)
        assertTrue(ArtworkGradient.topAlphaFor(0.95f) < ArtworkGradient.TOP_ALPHA * 0.7f)
        assertTrue(ArtworkGradient.topAlphaFor(1f) > 0.4f)
    }

    @Test fun lyricsStayTintedAllTheWayDownAndSmooth() {
        val stops = ArtworkGradient.lyricsStops()
        assertEquals(0.78f, stops.first().second, 1e-6f)
        assertEquals(0.30f, stops.last().second, 1e-6f)
        for (i in 1 until stops.size) {
            assertTrue(stops[i].second <= stops[i - 1].second + 1e-6f)
            assertTrue(stops[i - 1].second - stops[i].second < 0.15f)
        }
        assertTrue(stops.all { it.second >= 0.30f - 1e-6f })
    }
}
