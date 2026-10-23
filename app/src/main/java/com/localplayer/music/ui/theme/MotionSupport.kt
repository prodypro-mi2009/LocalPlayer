package com.localplayer.music.ui.theme

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** True when the user turned system animations off. Slides / scales then become short cross-fades. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Reads the system animation setting and re-reads it every time the app comes back to the foreground. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember { mutableStateOf(readReduceMotion(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reduce = readReduceMotion(context) }
    return reduce
}

fun readReduceMotion(context: Context): Boolean = try {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) <= 0f
} catch (_: Exception) {
    false
}

/**
 * Haptics only where they earn it (gesture commit points). Everything goes through View.performHapticFeedback,
 * so the system "touch feedback" setting is respected; no raw Vibrator is ever used.
 */
class Haptics(private val view: View) {
    private fun perform(constant: Int) {
        view.performHapticFeedback(constant)
    }

    /** A row / sheet is picked up. */
    fun pickUp() = perform(HapticFeedbackConstants.LONG_PRESS)

    /** A small step while dragging (a row swapped places). */
    fun tick() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        else HapticFeedbackConstants.CLOCK_TICK,
    )

    /** A dragged row is put down. */
    fun drop() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.VIRTUAL_KEY,
    )

    /** A gesture crossed its commit point. */
    fun threshold() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
        else HapticFeedbackConstants.CLOCK_TICK,
    )

    /** An action was confirmed. */
    fun confirm() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.VIRTUAL_KEY,
    )

    /** A switch / heart changed state. */
    fun toggle(on: Boolean) = perform(
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                if (on) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
            else -> if (on) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.CLOCK_TICK
        },
    )
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

/**
 * Touch feedback in the same frame: the element shrinks a little while pressed and springs back on release.
 * The scale is read inside graphicsLayer (draw phase), so a press causes no recomposition per frame.
 * Disabled (always 1) under reduced motion.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.97f): Modifier {
    val reduce = LocalReduceMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed && !reduce) pressedScale else 1f,
        animationSpec = Motion.PressSpring,
        label = "pressScale",
    )
    return graphicsLayer {
        val s = scale.value
        scaleX = s
        scaleY = s
    }
}
