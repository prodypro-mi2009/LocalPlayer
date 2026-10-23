package com.localplayer.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.localplayer.music.ui.theme.LocalReduceMotion
import com.localplayer.music.ui.theme.Motion
import com.localplayer.music.ui.theme.SheetPhysics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The vertical position of the full-screen Now Playing sheet, shared by everything that moves it: the pull-down
 * inside the sheet, the drag-up from the mini player and the tap on the mini player.
 *
 * [offsetPx] is the translationY of the sheet: [extentPx] = fully closed (below the screen), 0 = fully open.
 * While a finger is down the offset follows it 1:1 ([dragBy] / [dragTo]); on release the sheet settles with a
 * spring that continues with the finger's own velocity ([settle] / [open] / [close]). Every animation can be
 * grabbed at any moment and continues from its CURRENT value.
 */
@Stable
class PlayerSheetState(
    val extentPx: Float,
    initiallyOpen: Boolean,
    private val scope: CoroutineScope,
    private val reduceMotion: () -> Boolean,
) {
    /** 0 = open, [extentPx] = closed. Bounds keep a spring overshoot from pushing the sheet above the screen top. */
    val offsetPx: Animatable<Float, AnimationVector1D> =
        Animatable(if (initiallyOpen) 0f else extentPx).also { it.updateBounds(0f, extentPx) }

    /** Opacity of the sheet. Only used under reduced motion (fade instead of slide). */
    val contentAlpha: Animatable<Float, AnimationVector1D> = Animatable(1f)

    private val composedState = derivedStateOf { offsetPx.value < extentPx }

    /**
     * The sheet content is composed only while any part of it is on screen: nothing is composed or drawn when
     * closed. A derived state, so reading it recomposes only when it flips, not on every animation frame.
     */
    val isComposed: Boolean get() = composedState.value

    /** The target state (what the user asked for). This is what is saved across rotation / recreation. */
    var isOpen: Boolean by mutableStateOf(initiallyOpen)
        private set

    /** True from the first finger movement until the release has been handled. */
    var isDragging: Boolean by mutableStateOf(false)
        private set

    private var job: Job? = null
    private var dragAccum = 0f
    private var grabbed = false

    /** The current offset including movement that was queued but not yet applied. */
    val currentOffset: Float get() = if (isDragging) dragAccum else offsetPx.value

    /** 1:1 movement while a finger is down. Also cancels any running animation. */
    suspend fun dragTo(px: Float) {
        job?.cancel()
        offsetPx.snapTo(px.coerceIn(0f, extentPx))
    }

    /**
     * Moves the sheet by [deltaPx] (positive = down). Non-suspending: deltas are accumulated synchronously and
     * applied immediately, so the sheet's edge follows the finger frame by frame with no lost or reordered events.
     */
    fun dragBy(deltaPx: Float) {
        if (!isDragging) {
            isDragging = true
            grabbed = false
            job?.cancel()
            dragAccum = offsetPx.value
        }
        dragAccum = (dragAccum + deltaPx).coerceIn(0f, extentPx)
        val target = dragAccum
        // UNDISPATCHED: runs right now, in the current call, so the value is set in the same frame.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { offsetPx.snapTo(target) }
    }

    fun open(velocityPxPerSec: Float = 0f) = animateTo(toOpen = true, velocityPxPerSec)

    fun close(velocityPxPerSec: Float = 0f) = animateTo(toOpen = false, velocityPxPerSec)

    /**
     * Release of a drag-up from the closed state (or of any partial position): decides open / close from where
     * the sheet is heading ([SheetPhysics.shouldOpen]) and continues with the finger's velocity.
     * [upwardVelocityPxPerSec] is POSITIVE when the finger moves up.
     */
    fun settle(openedPx: Float, upwardVelocityPxPerSec: Float, minFlingOpenedPx: Float = 0f) {
        if (SheetPhysics.shouldOpen(openedPx, upwardVelocityPxPerSec, extentPx, minFlingOpenedPx)) {
            open(velocityPxPerSec = -upwardVelocityPxPerSec)
        } else {
            close(velocityPxPerSec = -upwardVelocityPxPerSec)
        }
    }

    /** Ends a drag session that did not end in a release handled by [open] / [close] (the sheet stays where it is). */
    fun endDrag() {
        isDragging = false
    }

    /** A finger touched the sheet while it was animating: freeze it where it is (no jump). */
    fun grab() {
        if (isDragging) return
        if (job?.isActive == true) {
            job?.cancel()
            grabbed = true
        }
    }

    /** The finger lifted without moving the sheet: let it settle from where it was frozen. */
    fun releaseGrab() {
        if (grabbed && !isDragging) {
            grabbed = false
            settle(openedPx = extentPx - offsetPx.value, upwardVelocityPxPerSec = 0f)
        }
        grabbed = false
    }

    // [velocityPxPerSec] is in the offset's own direction (positive = down), as springs expect it.
    private fun animateTo(toOpen: Boolean, velocityPxPerSec: Float) {
        isOpen = toOpen
        isDragging = false
        grabbed = false
        job?.cancel()
        val reduce = reduceMotion()
        job = scope.launch {
            if (reduce) {
                // No slide: opacity only. A drag may have moved the sheet; the release snaps it.
                if (toOpen) {
                    val wasHidden = offsetPx.value >= extentPx
                    offsetPx.snapTo(0f)
                    if (wasHidden) {
                        contentAlpha.snapTo(0f)
                        contentAlpha.animateTo(1f, tween(Motion.PLAYER_FADE_MS, easing = Motion.EaseOut))
                    } else {
                        contentAlpha.snapTo(1f)
                    }
                } else {
                    contentAlpha.animateTo(0f, tween(Motion.PLAYER_FADE_MS, easing = Motion.EaseOut))
                    offsetPx.snapTo(extentPx)
                    contentAlpha.snapTo(1f)
                }
            } else {
                contentAlpha.snapTo(1f)
                val target = if (toOpen) 0f else extentPx
                offsetPx.animateTo(
                    targetValue = target,
                    animationSpec = Motion.settleSpring(velocityPxPerSec, reduceMotion = false),
                    initialVelocity = velocityPxPerSec,
                )
            }
        }
    }
}

/**
 * Creates the shared sheet state. The extent is the window height, so "closed" is always completely off screen.
 * If the extent changes (rotation) a new state keeps the current open / closed target and re-snaps.
 */
@Composable
fun rememberPlayerSheetState(initiallyOpen: Boolean): PlayerSheetState {
    val density = LocalDensity.current
    val configHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val windowHeightPx = LocalWindowInfo.current.containerSize.height.toFloat()
    val extent = maxOf(configHeightPx, windowHeightPx)
    val scope = rememberCoroutineScope()
    val reduce by rememberUpdatedState(LocalReduceMotion.current)
    return remember(extent) { PlayerSheetState(extent, initiallyOpen, scope) { reduce } }
}
