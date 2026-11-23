package com.localplayer.music.data.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class Ebur128Test {
    /** 997 Hz sine at -23 dBFS peak in every channel, [seconds] long. */
    private fun measure(rate: Int, channels: Int, seconds: Int, level: Double = -23.0): Loudness {
        val meter = Ebur128(rate, channels)
        val amp = 10.0.pow(level / 20)
        val samples = FloatArray(rate * channels)
        repeat(seconds) { s ->
            for (i in 0 until rate) {
                val v = (amp * sin(2 * PI * 997 * (s * rate + i) / rate)).toFloat()
                for (c in 0 until channels) samples[i * channels + c] = v
            }
            meter.add(samples, rate)
        }
        return meter.finish()
    }

    @Test fun stereoSineAtMinus23DbfsIsMinus23Lufs() {
        assertEquals(-23.0, measure(48000, 2, 10).integratedLufs!!, 0.1)
        assertEquals(-23.0, measure(44100, 2, 10).integratedLufs!!, 0.1)
    }

    @Test fun monoIsHeardOnBothSpeakersSoItReadsTheSame() {
        assertEquals(-23.0, measure(44100, 1, 10).integratedLufs!!, 0.1)
    }

    @Test fun gainBringsTheSongToTheReference() {
        assertEquals(5.0, Ebur128.gainDb(-23.0), 1e-9)
    }

    @Test fun silenceAndVeryShortSongsHaveNoLoudness() {
        assertNull(measure(44100, 2, 10, level = -120.0).integratedLufs)
        assertNull(measure(44100, 2, 0).integratedLufs)
    }

    @Test fun anAlbumIsGatedOverAllItsSongs() {
        val loud = measure(44100, 2, 10, -15.0)
        val quiet = measure(44100, 2, 10, -30.0)
        val album = Ebur128.integrated(listOf(loud.blocks, quiet.blocks))!!
        // The relative gate (-10 LU) drops the quiet song's blocks, so the album reads like the loud one.
        assertEquals(loud.integratedLufs!!, album, 0.2)
    }

    @Test fun peakIsTheHighestSample() {
        assertEquals(10.0.pow(-23.0 / 20), measure(44100, 2, 2).peak.toDouble(), 0.001)
    }
}
