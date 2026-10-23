package com.localplayer.music.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.Alignment
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

    /** Track change on the Now Playing screen: new content in / old content out. Skipping is frequent, so short. */
    const val TRACK_IN_MS = 240
    const val TRACK_OUT_MS = 160

    /**
     * Visibility threshold of PIXEL springs. The default (0.01) leaves a long invisible tail and keeps gesture
     * handlers waiting for it; half a pixel is the last change anyone can see.
     */
    const val PIXEL_THRESHOLD = 0.5f

    /** Apple spring (damping ratio, response in seconds) as a Compose spring. stiffness = (2π / response)². */
    fun appleSpring(dampingRatio: Float, response: Float, visibilityThreshold: Float? = null): SpringSpec<Float> =
        spring(
            dampingRatio = dampingRatio,
            stiffness = MotionPhysics.stiffnessForResponse(response),
            visibilityThreshold = visibilityThreshold,
        )

    /** Default settle after a drag: no overshoot. */
    val SettleCritical: SpringSpec<Float> = appleSpring(1.0f, 0.4f, PIXEL_THRESHOLD)

    /** Settle after a FAST release only: a slight overshoot that carries the finger's momentum. */
    val SettleMomentum: SpringSpec<Float> = appleSpring(0.8f, 0.3f, PIXEL_THRESHOLD)

    /** Press feedback (scale factor 0..1), default threshold. */
    val PressSpring: SpringSpec<Float> = appleSpring(1.0f, 0.2f)

    /** The spring for a release with [velocity] px/s: momentum only after a fast release and never under reduced motion. */
    fun settleSpring(velocity: Float, reduceMotion: Boolean): SpringSpec<Float> =
        if (!reduceMotion && MotionPhysics.carriesMomentum(velocity)) SettleMomentum else SettleCritical

    /** Library tab content fading in after the user switched tabs. */
    const val TAB_FADE_MS = 140

    /** Reduced-motion presentation of the Now Playing sheet: opacity only. */
    const val PLAYER_FADE_MS = 200

    /** Pick-up / press feedback on a draggable row. */
    const val PICKUP_MS = 160

    /** A lyric line becoming (or ceasing to be) the active one. */
    const val LYRIC_ACTIVE_MS = 220

    /** Small state colour changes (tab selected, toggles). 100–160 ms band; 150 ms, default ease. */
    val ColorChange: TweenSpec<Color> = tween(durationMillis = 150)

    /** Mini player bar appearing / disappearing. Rare event; exit is quicker than entrance. */
    const val BAR_IN_MS = 220
    const val BAR_OUT_MS = 160

    /** Screen push/pop. Navigation is very frequent, so this is deliberately short and subtle. */
    const val SCREEN_IN_MS = 260
    const val SCREEN_OUT_MS = 200
    /** Optional hold before the leaving screen starts to fade (0 = cross-fade at once). */
    const val SCREEN_OUT_DELAY_MS = 0

    /** A popped screen stays composed while it animates out; wait this long before dropping its saved state. */
    const val SCREEN_CLEANUP_MS = 500

    // ---- Transition helpers. [reduce] = fade only (no slide / expand). ---------------------------------

    fun screenEnter(forward: Boolean, reduce: Boolean): EnterTransition {
        val fade = fadeIn(tween(SCREEN_IN_MS, easing = EaseOut))
        if (reduce) return fade
        val sign = if (forward) 1 else -1
        return slideInHorizontally(tween(SCREEN_IN_MS, easing = EaseOut)) { sign * it / 24 } + fade
    }

    fun screenExit(forward: Boolean, reduce: Boolean): ExitTransition {
        val fade = fadeOut(tween(SCREEN_OUT_MS, delayMillis = SCREEN_OUT_DELAY_MS, easing = EaseOut))
        if (reduce) return fade
        val sign = if (forward) 1 else -1
        return slideOutHorizontally(tween(SCREEN_OUT_MS + SCREEN_OUT_DELAY_MS, easing = EaseOut)) { -sign * it / 24 } + fade
    }

    /**
     * Back: the screen we return to is already fully there, BELOW the leaving one (see targetContentZIndex in
     * AppRoot); only the leaving screen moves a little to the right and fades away, revealing it. Nothing
     * overlaps in a ghostly half-faded state and nothing disappears abruptly at the end.
     */
    const val SCREEN_BACK_MS = 260

    fun screenBackExit(reduce: Boolean): ExitTransition {
        val fade = fadeOut(tween(SCREEN_BACK_MS, easing = EaseOut))
        if (reduce) return fade
        return slideOutHorizontally(tween(SCREEN_BACK_MS, easing = EaseOut)) { it / 16 } + fade
    }

    fun trackEnter(forward: Boolean, reduce: Boolean): EnterTransition {
        val fade = fadeIn(tween(TRACK_IN_MS, easing = EaseOut))
        if (reduce) return fade
        val sign = if (forward) 1 else -1
        return slideInHorizontally(tween(TRACK_IN_MS, easing = EaseOut)) { sign * it / 5 } + fade
    }

    fun trackExit(forward: Boolean, reduce: Boolean): ExitTransition {
        val fade = fadeOut(tween(TRACK_OUT_MS, easing = EaseOut))
        if (reduce) return fade
        val sign = if (forward) 1 else -1
        return slideOutHorizontally(tween(TRACK_OUT_MS, easing = EaseOut)) { -sign * it / 5 } + fade
    }

    fun barEnter(reduce: Boolean): EnterTransition {
        val fade = fadeIn(tween(BAR_IN_MS, easing = EaseOut))
        if (reduce) return fade
        return expandVertically(tween(BAR_IN_MS, easing = EaseOut), expandFrom = Alignment.Bottom) + fade
    }

    fun barExit(reduce: Boolean): ExitTransition {
        val fade = fadeOut(tween(BAR_OUT_MS, easing = EaseOut))
        if (reduce) return fade
        return shrinkVertically(tween(BAR_OUT_MS, easing = EaseOut), shrinkTowards = Alignment.Bottom) + fade
    }
}
