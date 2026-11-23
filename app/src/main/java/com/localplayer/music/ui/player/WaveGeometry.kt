package com.localplayer.music.ui.player

import kotlin.math.PI
import kotlin.math.sin

/**
 * Shape of the played (wavy) part of the progress bar, as the vertical distance from the centre line.
 * Kept free of Android classes so it can be unit-tested.
 */
object WaveGeometry {
    /** Fraction of the amplitude that is left at the thumb: the wave settles to this height as it reaches the thumb. */
    const val END_FACTOR = 0.5f

    /**
     * Offset of the wave at [x]. It grows out of the baseline over the first wavelength, and over the last [taperLength]
     * before [thumbX] it eases down to [END_FACTOR] of its height. The wave therefore ends at a small offset, inside the round
     * thumb, and no steep or pointed end can stick out of the thumb (the stroke's own half width is added to that offset).
     */
    fun offset(x: Float, thumbX: Float, wavelength: Float, taperLength: Float, amplitude: Float, phase: Float): Float {
        val ramp = (x / wavelength).coerceIn(0f, 1f)
        val t = if (taperLength <= 0f) 1f else ((x - (thumbX - taperLength)) / taperLength).coerceIn(0f, 1f)
        val smooth = t * t * (3f - 2f * t)
        val tail = 1f - (1f - END_FACTOR) * smooth
        return amplitude * ramp * tail * sin((x / wavelength) * 2f * PI.toFloat() - phase)
    }
}
