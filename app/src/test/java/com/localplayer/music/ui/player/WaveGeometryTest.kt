package com.localplayer.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class WaveGeometryTest {
    private val wavelength = 79.2f
    private val amplitude = 15.8f
    private val taper = 37f
    private val strokeHalf = 7.9f
    private val thumbRadius = 21f

    @Test fun waveStartsOnTheBaseline() {
        assertEquals(0f, WaveGeometry.offset(0f, 300f, wavelength, taper, amplitude, 1.3f), 1e-4f)
    }

    @Test fun waveEndStaysInsideTheThumbForEveryPhaseAndPosition() {
        var thumbX = 25f
        while (thumbX <= 1000f) {
            var phase = 0f
            while (phase < 2 * PI) {
                val end = abs(WaveGeometry.offset(thumbX, thumbX, wavelength, taper, amplitude, phase))
                // The stroke (centre line plus its half width) never reaches the edge of the round thumb at the end of the wave.
                assertTrue("thumbX=$thumbX phase=$phase end=$end", end + strokeHalf < thumbRadius)
                phase += 0.1f
            }
            thumbX += 7.3f
        }
    }

    @Test fun noSteepStepsBetweenNeighbouringPoints() {
        // Points are 2 px apart: a pointed or vertical piece would show up as a big vertical step.
        var worst = 0f
        var phase = 0f
        while (phase < 2 * PI) {
            val thumbX = 300f
            var x = 0f
            var last = WaveGeometry.offset(0f, thumbX, wavelength, taper, amplitude, phase)
            while (x < thumbX) {
                x += 2f
                val y = WaveGeometry.offset(minOf(x, thumbX), thumbX, wavelength, taper, amplitude, phase)
                worst = maxOf(worst, abs(y - last))
                last = y
            }
            phase += 0.1f
        }
        assertTrue("worst step $worst", worst < 3.5f)
    }
}
