package com.localplayer.music.data.effects

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tan

/** What one song measured: the energy of every 400 ms block (for gating) and the highest sample. */
class Loudness(
    val blocks: DoubleArray,
    val peak: Float,
    /** True when the decoder gave float samples; with 16-bit samples a peak above full scale is cut off at 1.0. */
    val floatSamples: Boolean = true,
) {
    /** Integrated loudness in LUFS, or null for silence / a song shorter than 400 ms. */
    val integratedLufs: Double? get() = Ebur128.integrated(listOf(blocks))
}

/**
 * Loudness measurement as in EBU R128 / ITU-R BS.1770-4 (the same standard as FFmpeg's ebur128 filter): K-weighting,
 * 400 ms blocks every 100 ms, absolute gate -70 LUFS, relative gate -10 LU. Pure Kotlin, no Android classes.
 * Mono is treated as the same signal on both speakers (as it is heard), more than 2 channels weigh like surround.
 */
class Ebur128(sampleRate: Int, private val channels: Int) {
    private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        private var z1 = 0.0
        private var z2 = 0.0
        fun process(x: Double): Double {
            val out = b0 * x + z1
            z1 = b1 * x - a1 * out + z2
            z2 = b2 * x - a2 * out
            return out
        }
    }

    private val shelf = Array(channels) { makeShelf(sampleRate) }
    private val highPass = Array(channels) { makeHighPass(sampleRate) }
    private val weight = DoubleArray(channels) { if (channels == 1) 2.0 else if (it < 3) 1.0 else 1.41 }
    private val hopFrames = (sampleRate / 10).coerceAtLeast(1)
    private val sums = DoubleArray(channels)
    private var framesInHop = 0
    private val recentHops = DoubleArray(4)
    private var hopCount = 0
    private var blocks = DoubleArray(1024)
    private var blockCount = 0
    private var peak = 0f

    /** [frames] interleaved frames of float samples (full scale = 1.0). */
    fun add(samples: FloatArray, frames: Int) {
        var index = 0
        repeat(frames) {
            for (c in 0 until channels) {
                val x = samples[index++]
                val a = abs(x)
                if (a > peak) peak = a
                val y = highPass[c].process(shelf[c].process(x.toDouble()))
                sums[c] += y * y
            }
            if (++framesInHop == hopFrames) endHop()
        }
    }

    private fun endHop() {
        var energy = 0.0
        for (c in 0 until channels) {
            energy += weight[c] * sums[c] / hopFrames
            sums[c] = 0.0
        }
        framesInHop = 0
        recentHops[hopCount % 4] = energy
        hopCount++
        if (hopCount >= 4) {
            if (blockCount == blocks.size) blocks = blocks.copyOf(blocks.size * 2)
            blocks[blockCount++] = recentHops.sum() / 4
        }
    }

    fun finish() = Loudness(blocks.copyOf(blockCount), peak)

    companion object {
        /** ReplayGain 2.0 reference loudness. */
        const val REFERENCE_LUFS = -18.0

        private fun makeShelf(fs: Int): Biquad {
            val f0 = 1681.974450955533
            val q = 0.7071752369554196
            val k = tan(PI * f0 / fs)
            val vh = 10.0.pow(3.999843853973347 / 20)
            val vb = vh.pow(0.4996667741545416)
            val a0 = 1 + k / q + k * k
            return Biquad((vh + vb * k / q + k * k) / a0, 2 * (k * k - vh) / a0, (vh - vb * k / q + k * k) / a0, 2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0)
        }

        private fun makeHighPass(fs: Int): Biquad {
            val q = 0.5003270373238773
            val k = tan(PI * 38.13547087602444 / fs)
            val a0 = 1 + k / q + k * k
            return Biquad(1.0, -2.0, 1.0, 2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0)
        }

        private fun lufs(energy: Double) = -0.691 + 10 * log10(energy)

        /** Gated loudness over all blocks of one or several songs (several = the whole album). */
        fun integrated(songs: List<DoubleArray>): Double? {
            var sum = 0.0
            var count = 0
            for (song in songs) for (z in song) if (z > 0 && lufs(z) > -70.0) { sum += z; count++ }
            if (count == 0) return null
            val relativeGate = lufs(sum / count) - 10
            var gated = 0.0
            var kept = 0
            for (song in songs) for (z in song) if (z > 0 && lufs(z) > -70.0 && lufs(z) > relativeGate) { gated += z; kept++ }
            return if (kept == 0) null else lufs(gated / kept)
        }

        /** The ReplayGain value (dB) that brings [lufs] to the reference loudness. */
        fun gainDb(lufs: Double): Double = REFERENCE_LUFS - lufs
    }
}
