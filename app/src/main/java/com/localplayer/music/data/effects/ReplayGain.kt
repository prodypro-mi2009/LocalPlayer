package com.localplayer.music.data.effects

import kotlin.math.log10
import kotlin.math.pow

enum class ReplayGainMode(val key: String) {
    TRACK("track"), ALBUM("album");

    companion object {
        fun fromKey(key: String?): ReplayGainMode = entries.firstOrNull { it.key == key } ?: TRACK
    }
}

/** ReplayGain values read from a file's own tags. Any value may be missing. Peak is linear (1.0 = full scale). */
data class ReplayGainInfo(
    val trackGainDb: Float? = null,
    val trackPeak: Float? = null,
    val albumGainDb: Float? = null,
    val albumPeak: Float? = null,
) {
    val isEmpty: Boolean get() = trackGainDb == null && albumGainDb == null
}

/** Playback adjustment: the player volume (<= 1) plus a positive boost in millibels for the loudness enhancer. */
data class GainPlan(val volume: Float, val boostMb: Int) {
    val isNeutral: Boolean get() = volume == 1f && boostMb == 0
}

/** Pure ReplayGain rules. */
object ReplayGainMath {
    const val MAX_CUT_DB = -24f
    const val MAX_BOOST_DB = 12f

    /** "-6.54 dB", "+3.2dB", "-6.54", " 0 dB " -> dB; anything else (empty, text, NaN, absurd) -> null. */
    fun parseGainDb(raw: String?): Float? {
        val t = raw?.trim()?.removeSuffix("dB")?.removeSuffix("DB")?.removeSuffix("db")?.trim() ?: return null
        val v = t.toFloatOrNull() ?: return null
        return if (v.isFinite() && v in -60f..60f) v else null
    }

    /** Linear peak, must be > 0 and sane. */
    fun parsePeak(raw: String?): Float? {
        val v = raw?.trim()?.toFloatOrNull() ?: return null
        return if (v.isFinite() && v > 0f && v <= 20f) v else null
    }

    /**
     * Gain in dB for [mode], or null when the file has no usable tag (then the track plays unchanged).
     * Album mode falls back to the track gain (and the other way round). With [preventClipping] the gain never
     * exceeds the value that would push the tagged peak above full scale.
     */
    fun effectiveGainDb(info: ReplayGainInfo, mode: ReplayGainMode, preventClipping: Boolean): Float? {
        val useAlbum = when (mode) {
            ReplayGainMode.ALBUM -> info.albumGainDb != null
            ReplayGainMode.TRACK -> info.trackGainDb == null && info.albumGainDb != null
        }
        val gain = (if (useAlbum) info.albumGainDb else info.trackGainDb) ?: return null
        val peak = if (useAlbum) info.albumPeak else info.trackPeak
        var g = gain
        if (preventClipping && peak != null) g = minOf(g, (-20.0 * log10(peak.toDouble())).toFloat())
        return g.coerceIn(MAX_CUT_DB, MAX_BOOST_DB)
    }

    fun plan(gainDb: Float?): GainPlan {
        if (gainDb == null || gainDb == 0f) return GainPlan(1f, 0)
        return if (gainDb < 0f) GainPlan(10.0.pow(gainDb / 20.0).toFloat().coerceIn(0f, 1f), 0)
        else GainPlan(1f, Math.round(gainDb * 100))
    }
}
