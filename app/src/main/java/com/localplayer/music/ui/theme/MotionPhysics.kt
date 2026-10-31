package com.localplayer.music.ui.theme

import kotlin.math.abs

/**
 * Pure physics helpers for the motion system. No Android / Compose imports, so they are JVM-testable.
 *
 * An Apple spring is described by (damping ratio, response in seconds); Compose wants a stiffness instead:
 * stiffness = (2π / response)².
 */
object MotionPhysics {
    /** Compose stiffness for an Apple-style spring [response] (seconds). 0.5 s -> about 157.9. */
    fun stiffnessForResponse(response: Float): Float {
        val w = (2.0 * Math.PI / response.toDouble())
        return (w * w).toFloat()
    }

    /** Scroll-view style deceleration rate used for momentum projection. */
    const val DECELERATION_RATE = 0.998f

    /**
     * Where a flick of [velocity] (px/s) would coast to, in px: `v/1000 * rate / (1 - rate)`.
     * Decisions (dismiss / snap) are taken on the PROJECTED position, not only on the raw offset.
     */
    fun project(velocity: Float, rate: Float = DECELERATION_RATE): Float =
        velocity / 1000f * rate / (1f - rate)

    /** A release at least this fast (px/s) carries momentum, so the settle spring may overshoot slightly. */
    const val MOMENTUM_VELOCITY_PX_PER_SEC = 700f

    fun carriesMomentum(v: Float): Boolean = abs(v) >= MOMENTUM_VELOCITY_PX_PER_SEC
}

/** Rules for the Now Playing sheet: pull DOWN to dismiss and the mirrored drag UP to open. */
object SheetPhysics {
    const val DISMISS_FRACTION = 0.25f
    const val UPWARD_CANCEL_VELOCITY_PX_PER_SEC = 300f

    /**
     * Pull-down: [offsetPx] is how far the sheet was pulled down (>= 0), [velocityPxPerSec] is POSITIVE when the
     * finger moves down. An upward flick (velocity < -300) always cancels the dismissal; otherwise the coast is
     * added to the offset - but only when the sheet was pulled at least [minFlingOffsetPx] (so a tiny touch with
     * a quick flick does not close it). Dismiss when `offset + coast > 25 %` of [extentPx].
     */
    fun shouldDismiss(
        offsetPx: Float,
        velocityPxPerSec: Float,
        extentPx: Float,
        minFlingOffsetPx: Float = 0f,
    ): Boolean {
        if (velocityPxPerSec < -UPWARD_CANCEL_VELOCITY_PX_PER_SEC) return false
        val coast = if (offsetPx >= minFlingOffsetPx) MotionPhysics.project(velocityPxPerSec) else 0f
        return offsetPx + coast > DISMISS_FRACTION * extentPx
    }

    const val OPEN_FRACTION = 0.25f
    const val DOWNWARD_CANCEL_VELOCITY_PX_PER_SEC = 300f

    /**
     * Drag-up: the mirror of [shouldDismiss]. [openedPx] is how far the sheet was pulled up from fully closed
     * (0..extent); [upwardVelocityPxPerSec] is POSITIVE when the finger moves UP (pass `-velocityY`). A downward
     * flick (upwardVelocity < -300) always cancels. The coast is added only when `openedPx >= minFlingOpenedPx`.
     * Opens when `openedPx + coast > 25 %` of [extentPx]; also opens at >= 50 % regardless of velocity (unless
     * a downward flick cancels it).
     */
    fun shouldOpen(
        openedPx: Float,
        upwardVelocityPxPerSec: Float,
        extentPx: Float,
        minFlingOpenedPx: Float = 0f,
    ): Boolean {
        if (upwardVelocityPxPerSec < -DOWNWARD_CANCEL_VELOCITY_PX_PER_SEC) return false
        if (openedPx >= 0.5f * extentPx) return true
        val coast = if (openedPx >= minFlingOpenedPx) MotionPhysics.project(upwardVelocityPxPerSec) else 0f
        return openedPx + coast > OPEN_FRACTION * extentPx
    }
}

/**
 * Pure mapping from the queue sheet's ONE float (its offset) to everything that reacts to it. No Compose / Android
 * imports, so it is JVM-testable. `p` = progress: 0 = closed, 1 = fully open.
 */
object QueueSheetMotion {
    /** Black scrim over the Now Playing content: alpha = 0.45 * p (measured on the reference app). */
    const val SCRIM_MAX_ALPHA = 0.45f

    /** Now Playing scales down around its centre: 1 - 0.022 * p (a subtle depth cue). */
    const val SCALE_DROP = 0.022f

    /** The floating toolbar only exists from this progress on (and only while the sheet is open / opening). */
    const val TOOLBAR_VISIBLE_FROM = 0.88f

    /** One haptic tick when a finger-driven opening crosses this progress. */
    const val HAPTIC_PROGRESS = 0.25f

    fun progress(offsetPx: Float, extentPx: Float): Float =
        if (extentPx <= 0f) 0f else (1f - offsetPx / extentPx).coerceIn(0f, 1f)

    fun scrimAlpha(p: Float): Float = SCRIM_MAX_ALPHA * p.coerceIn(0f, 1f)

    fun contentScale(p: Float): Float = 1f - SCALE_DROP * p.coerceIn(0f, 1f)

    /** [isOpen] is the TARGET state: the toolbar leaves as soon as the sheet starts to close. */
    fun toolbarVisible(p: Float, isOpen: Boolean): Boolean = isOpen && p >= TOOLBAR_VISIBLE_FROM

    fun crossedHapticPoint(p: Float): Boolean = p >= HAPTIC_PROGRESS
}

/**
 * The mini player IS the sheet: dragging it up grows that same rounded rectangle. Both edges move together, the
 * bottom one more slowly (progress = 1 - offset/extent; 0 = closed in the mini player, 1 = full screen):
 *  - the top edge rises from the mini player's top to the screen top, finishing at [TOP_DONE] of the gesture;
 *  - the bottom edge leaves the mini player's bottom and is pushed down over the tab bar (which is shoved off the
 *    screen by exactly the distance the edge travels, [Frame.navShift]). It starts gently, speeds up and settles
 *    smoothly, and has fully covered the bottom when the top edge is at [BOTTOM_AT_TOP] of its own way
 *    (e.g. top 50 % -> bottom ~23 %, top 90 % -> bottom 100 %);
 *  - the corners are big and stay round until about the middle of the gesture, then relax to square at the end.
 */
object SheetMorph {
    /** Share of the gesture at which the top edge reaches the screen top. */
    const val TOP_DONE = 0.85f

    /** The bottom edge has fully covered the bottom of the screen when the top edge has covered this share of its way. */
    const val BOTTOM_AT_TOP = 0.9f

    /** Progress from which the big corners start to relax towards square (reaching 0 at the end). */
    const val CORNER_RELAX_FROM = 0.55f

    /** Share of the gesture over which the sheet fades in over the (identical-looking) mini player. */
    const val FADE_DONE_AT = 0.25f

    /** [top]/[bottom]: screen Y of the window's edges; [inset]: side margin; [corner]: radius; [navShift]: tab bar push (px). */
    class Frame(val top: Float, val bottom: Float, val inset: Float, val corner: Float, val navShift: Float)

    fun smooth(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun progress(offsetPx: Float, extentPx: Float): Float =
        if (extentPx <= 0f) 1f else (1f - offsetPx / extentPx).coerceIn(0f, 1f)

    fun fade(progress: Float): Float = smooth(progress / FADE_DONE_AT)

    /** How far (0..1) the bottom edge has travelled when the top edge has travelled [topShare] (0..1) of its way. */
    fun bottomShare(topShare: Float): Float {
        val t = (topShare / BOTTOM_AT_TOP).coerceIn(0f, 1f)
        return smooth(t * t)
    }

    fun frame(progress: Float, height: Float, miniTop: Float, miniBottom: Float, insetPx: Float, cornerPx: Float): Frame {
        val g = (progress / TOP_DONE).coerceIn(0f, 1f)
        val n = bottomShare(g)
        val top = miniTop * (1f - g)
        val bottom = miniBottom + (height - miniBottom) * n
        val inset = insetPx * (1f - smooth(g))
        val corner = cornerPx * (1f - smooth((progress - CORNER_RELAX_FROM) / (1f - CORNER_RELAX_FROM)))
        return Frame(top, bottom, inset, corner, bottom - miniBottom)
    }
}
