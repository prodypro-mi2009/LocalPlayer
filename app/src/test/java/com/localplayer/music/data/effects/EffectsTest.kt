package com.localplayer.music.data.effects

import org.junit.Assert.*
import org.junit.Test

class EffectsTest {
    @Test fun gainParsing() {
        assertEquals(-6.54f, ReplayGainMath.parseGainDb("-6.54 dB")!!, 0.001f)
        assertEquals(3.2f, ReplayGainMath.parseGainDb("+3.2dB")!!, 0.001f)
        assertEquals(0f, ReplayGainMath.parseGainDb(" 0 ")!!, 0f)
        for (bad in listOf(null, "", "abc", "NaN", "Infinity", "999 dB", "-- dB")) assertNull(bad, ReplayGainMath.parseGainDb(bad))
        assertEquals(0.98f, ReplayGainMath.parsePeak("0.98")!!, 0.001f)
        for (bad in listOf(null, "", "0", "-1", "x", "NaN", "1000")) assertNull(bad, ReplayGainMath.parsePeak(bad))
    }

    @Test fun trackAndAlbumModes_withFallbacks() {
        val both = ReplayGainInfo(-6f, null, -3f, null)
        assertEquals(-6f, ReplayGainMath.effectiveGainDb(both, ReplayGainMode.TRACK, true)!!, 0f)
        assertEquals(-3f, ReplayGainMath.effectiveGainDb(both, ReplayGainMode.ALBUM, true)!!, 0f)
        assertEquals(-6f, ReplayGainMath.effectiveGainDb(ReplayGainInfo(trackGainDb = -6f), ReplayGainMode.ALBUM, true)!!, 0f)
        assertEquals(-3f, ReplayGainMath.effectiveGainDb(ReplayGainInfo(albumGainDb = -3f), ReplayGainMode.TRACK, true)!!, 0f)
        assertNull(ReplayGainMath.effectiveGainDb(ReplayGainInfo(), ReplayGainMode.TRACK, true))
    }

    @Test fun clippingProtection_capsPositiveGain() {
        val info = ReplayGainInfo(trackGainDb = 6f, trackPeak = 0.8f)   // 0.8 -> max +1.938 dB
        assertEquals(1.938f, ReplayGainMath.effectiveGainDb(info, ReplayGainMode.TRACK, true)!!, 0.01f)
        assertEquals(6f, ReplayGainMath.effectiveGainDb(info, ReplayGainMode.TRACK, false)!!, 0f)
        // peak above 1 forces a cut even when the tag asked for 0 dB
        assertTrue(ReplayGainMath.effectiveGainDb(ReplayGainInfo(trackGainDb = 0f, trackPeak = 1.25f), ReplayGainMode.TRACK, true)!! < 0f)
        // a huge tag is limited
        assertEquals(12f, ReplayGainMath.effectiveGainDb(ReplayGainInfo(trackGainDb = 40f), ReplayGainMode.TRACK, true)!!, 0f)
        assertEquals(-24f, ReplayGainMath.effectiveGainDb(ReplayGainInfo(trackGainDb = -40f), ReplayGainMode.TRACK, true)!!, 0f)
    }

    @Test fun plan_cutUsesVolume_boostUsesEnhancer() {
        assertTrue(ReplayGainMath.plan(null).isNeutral)
        assertTrue(ReplayGainMath.plan(0f).isNeutral)
        val cut = ReplayGainMath.plan(-6f); assertEquals(0.501f, cut.volume, 0.002f); assertEquals(0, cut.boostMb)
        val boost = ReplayGainMath.plan(3f); assertEquals(1f, boost.volume, 0f); assertEquals(300, boost.boostMb)
    }

    @Test fun eq_presetsAndCustomText() {
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 0f, 0f), EqPreset.FLAT.gainsDb(FloatArray(5) { 9f }), 0f)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f, 5f), EqPreset.CUSTOM.gainsDb(floatArrayOf(1f, 2f, 3f, 4f, 5f)), 0f)
        assertEquals(EqPreset.FLAT, EqPreset.fromKey("nonsense"))
        assertNull(EqModel.parseCustom("1,2,3")); assertNull(EqModel.parseCustom("a,b,c,d,e")); assertNull(EqModel.parseCustom(null))
        assertArrayEquals(floatArrayOf(12f, -12f, 0f, 0f, 0f), EqModel.parseCustom("50,-50,0,0,0")!!, 0f)
        assertEquals("1.0,2.5,0.0,-3.0,4.0", EqModel.serializeCustom(floatArrayOf(1f, 2.5f, 0f, -3f, 4f)))
    }

    @Test fun eq_mapsOntoRealBands() {
        // typical 5-band device effect
        val centers = intArrayOf(60, 230, 910, 3600, 14000)
        val g = floatArrayOf(6f, 4f, 0f, -2f, 1f)
        assertArrayEquals(intArrayOf(600, 400, 0, -200, 100), EqModel.mapToDevice(g, centers, -1500, 1500), )
        // clamped to the device range
        assertEquals(1000, EqModel.mapToDevice(floatArrayOf(12f, 12f, 12f, 12f, 12f), intArrayOf(100), -1000, 1000)[0])
        // 3-band and 10-band devices work too; values interpolate monotonically between neighbours
        val three = EqModel.mapToDevice(g, intArrayOf(100, 1000, 8000), -1500, 1500)
        assertEquals(3, three.size)
        assertTrue(three[0] in 0..600 && three[1] in -200..0)
        val ten = EqModel.mapToDevice(floatArrayOf(0f, 0f, 0f, 0f, 0f), IntArray(10) { 31 * (1 shl it) }, -1500, 1500)
        assertTrue(ten.all { it == 0 })
        // out-of-range centres use the nearest logical band
        assertEquals(600, EqModel.mapToDevice(g, intArrayOf(20), -1500, 1500)[0])
        assertEquals(100, EqModel.mapToDevice(g, intArrayOf(20000), -1500, 1500)[0])
    }
}
