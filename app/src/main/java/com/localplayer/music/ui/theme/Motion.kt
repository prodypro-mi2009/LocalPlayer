package com.localplayer.music.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color

/**
 * The app's shared motion vocabulary. Every animation takes its curve and duration from here instead of
 * typing its own, so the whole app moves with one personality.
 *
 * Rules the values come from: things entering or leaving use an ease-out (starts fast, feels responsive);
 * UI animations stay under 300 ms unless they are a full-screen surface or an ambient background.
 */
object Motion {
    /** Strong ease-out for UI that enters or leaves. CSS: cubic-bezier(0.23, 1, 0.32, 1). */
    val EaseOut: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** iOS-like drawer curve for full-screen sheets. CSS: cubic-bezier(0.32, 0.72, 0, 1). */
    val EaseDrawer: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    /**
     * Ambient artwork-tint crossfade (window backdrop, Now Playing glow, Lyrics tint). 500 ms and the default
     * easing are deliberate (see MASTER-PHASE1.md row 5); do not shorten.
     */
    val AmbientTint: TweenSpec<Color> = tween(durationMillis = 500)
}
