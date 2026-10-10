package com.localplayer.music.ui.theme

/**
 * The shape of the artwork-derived background (Now Playing, Lyrics and the window backdrop), as pure maths so it
 * can be unit tested. `fraction` is the position from the top of the area (0 = top edge, 1 = bottom edge).
 *
 * Two problems of the old two-colour gradient `listOf(tone@50%, Color.Transparent)`:
 *  1. `Color.Transparent` is TRANSPARENT BLACK, and gradients interpolate the colour channels too, so half way the
 *     colour had already been mixed with black: the picture turned dark/grey far too early.
 *  2. A straight line gives a visible "edge" where the tint ends.
 * The fix is to keep the SAME hue in every stop and only change its alpha along an eased curve ([stops]).
 */
object ArtworkGradient {
    /** Tint strength at the very top (before the brightness correction of [topAlphaFor]). */
    const val TOP_ALPHA = 0.85f

    /**
     * Fraction of the height where the tint has (almost) gone: past it the screen is the plain background colour.
     * Chosen so the tint is down to ~12 % at 2/5 of the height ("almost black" starts there), and the way down to
     * that point is gradual instead of steep.
     */
    const val FADE_END = 0.56f

    /** Uniform black darkening over the lyrics page (keeps white text readable on bright covers). */
    const val LYRICS_SCRIM = 0.36f

    /** Smootherstep: 0 at the edges, 1 in the middle, zero slope at both ends (no visible start/end line). */
    private fun smootherStep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * x * (x * (x * 6f - 15f) + 10f)
    }

    /** Share of the top tint that is left at [fraction] of the height: 1 at the top, 0 from [fadeEnd] on. */
    fun remaining(fraction: Float, fadeEnd: Float = FADE_END): Float =
        1f - smootherStep(fraction / fadeEnd)

    fun alpha(fraction: Float, topAlpha: Float = TOP_ALPHA, fadeEnd: Float = FADE_END): Float =
        topAlpha * remaining(fraction, fadeEnd)

    /**
     * Very bright artwork colours (yellow, white-ish) would wash out the white text of the top bar, so the tint is
     * weakened for them; dark colours keep the full strength. [luminance] is 0..1 (relative luminance of the tone).
     */
    fun topAlphaFor(luminance: Float): Float {
        val excess = ((luminance - 0.45f) / 0.55f).coerceIn(0f, 1f)
        return TOP_ALPHA * (1f - 0.4f * excess)
    }

    /** [steps]+1 (fraction, alpha) pairs from the top (fraction 0) to [FADE_END]-and-below (alpha 0 at fraction 1). */
    fun stops(topAlpha: Float = TOP_ALPHA, fadeEnd: Float = FADE_END, steps: Int = 10): List<Pair<Float, Float>> {
        val list = ArrayList<Pair<Float, Float>>(steps + 2)
        for (i in 0..steps) {
            val f = fadeEnd * i / steps
            list += f to alpha(f, topAlpha, fadeEnd)
        }
        list += 1f to 0f
        return list
    }

    /**
     * Lyrics page: the same tone, but it never disappears (the whole page stays tinted and reaches the bottom of the
     * screen, under the floating bars, without a black block): from [top] at the top to [bottom] at the bottom.
     */
    fun lyricsAlpha(fraction: Float, top: Float = 0.78f, bottom: Float = 0.30f): Float {
        val s = smootherStep(fraction)
        return top + (bottom - top) * s
    }

    fun lyricsStops(top: Float = 0.78f, bottom: Float = 0.30f, steps: Int = 8): List<Pair<Float, Float>> =
        (0..steps).map { i -> val f = i.toFloat() / steps; f to lyricsAlpha(f, top, bottom) }
}
