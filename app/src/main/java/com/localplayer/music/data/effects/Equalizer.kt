package com.localplayer.music.data.effects

import kotlin.math.ln

/** Presets of the five-band equaliser. Gains are in dB for the five logical bands of [EqModel.BAND_HZ]. */
enum class EqPreset(val key: String, private val gains: FloatArray?) {
    FLAT("flat", floatArrayOf(0f, 0f, 0f, 0f, 0f)),
    BASS_BOOST("bass_boost", floatArrayOf(6f, 4f, 0f, 0f, 0f)),
    ROCK("rock", floatArrayOf(4f, 2f, -1f, 3f, 4f)),
    POP("pop", floatArrayOf(-1f, 2f, 4f, 2f, -1f)),
    VOCAL("vocal", floatArrayOf(-2f, 0f, 4f, 3f, 0f)),
    CUSTOM("custom", null);

    fun gainsDb(custom: FloatArray): FloatArray = (gains ?: custom).copyOf()

    companion object {
        fun fromKey(key: String?): EqPreset = entries.firstOrNull { it.key == key } ?: FLAT
    }
}

/** Pure helpers of the equaliser: custom-gain text, clamping, and the mapping onto the REAL bands of the device effect. */
object EqModel {
    const val BANDS = 5
    const val MAX_DB = 12f
    /** Logical band centres shown in the UI. */
    val BAND_HZ = intArrayOf(60, 230, 910, 3600, 14000)

    fun clamp(db: Float): Float = if (db.isNaN()) 0f else db.coerceIn(-MAX_DB, MAX_DB)

    fun flat() = FloatArray(BANDS)

    /** "a,b,c,d,e" -> five clamped gains, or null if the text is not exactly five numbers. */
    fun parseCustom(raw: String?): FloatArray? {
        val parts = raw?.split(',')?.map { it.trim().toFloatOrNull() ?: return null } ?: return null
        if (parts.size != BANDS) return null
        return FloatArray(BANDS) { clamp(parts[it]) }
    }

    fun serializeCustom(gains: FloatArray): String = gains.joinToString(",") { "%.1f".format(java.util.Locale.ROOT, clamp(it)) }

    /**
     * Maps the five logical gains to the device's own bands. For every real band the gain is interpolated on a
     * logarithmic frequency axis between the two nearest logical bands (flat outside the range), then converted to
     * millibels and clamped to the device's supported level range. Works for any band count (1..N).
     * [centerHz] are the real centre frequencies in Hz; returns millibels per real band.
     */
    fun mapToDevice(logicalDb: FloatArray, centerHz: IntArray, minMb: Int, maxMb: Int): IntArray = IntArray(centerHz.size) { i ->
        val f = centerHz[i].coerceAtLeast(1).toDouble()
        val db = when {
            f <= BAND_HZ.first() -> logicalDb.first().toDouble()
            f >= BAND_HZ.last() -> logicalDb.last().toDouble()
            else -> {
                var k = 0
                while (k < BANDS - 2 && f > BAND_HZ[k + 1]) k++
                val t = (ln(f) - ln(BAND_HZ[k].toDouble())) / (ln(BAND_HZ[k + 1].toDouble()) - ln(BAND_HZ[k].toDouble()))
                logicalDb[k] + (logicalDb[k + 1] - logicalDb[k]) * t
            }
        }
        Math.round(db * 100).toInt().coerceIn(minMb, maxMb)
    }
}
