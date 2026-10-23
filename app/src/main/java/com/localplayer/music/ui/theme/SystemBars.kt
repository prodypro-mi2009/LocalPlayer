package com.localplayer.music.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.localplayer.music.data.ThemeMode

/** Whether the app is currently dark: the user's choice, or the system setting when the mode is SYSTEM. */
fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Edge-to-edge window setup that follows the APP's theme (not the phone's), so the icons of the status bar and
 * the navigation bar always contrast with what the app draws behind them:
 *  - both bars are fully transparent (no scrim, no grey strip) and the content is drawn behind them;
 *  - the status/navigation icon colour follows the app theme (light icons on a dark app, dark icons on a light one);
 *  - the window's own background is the app background colour, so nothing of the platform theme (the grey of
 *    `Theme.Material`) can ever show through under a bar or during a transition;
 *  - the system's automatic contrast scrim of the 3-button navigation bar is switched off;
 *  - the content may extend into a display cutout on the short edges.
 * Insets themselves are consumed exactly once, by the top bars / bottom bars / Now Playing, not here.
 */
@Composable
fun ConfigureSystemBars(activity: ComponentActivity, dark: Boolean) {
    DisposableEffect(activity, dark) {
        val style = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(ColorDrawable(if (dark) AppColors.TrueBlack.toArgb() else AppColors.TrueWhite.toArgb()))
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        // Only touch the attributes when needed: re-assigning them forces a relayout of a window that was just recreated.
        if (Build.VERSION.SDK_INT >= 28 &&
            window.attributes.layoutInDisplayCutoutMode != WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        ) {
            window.attributes = window.attributes.also {
                it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        onDispose { }
    }
}

/**
 * While this composable is in the composition the status bar icons are forced to light (or dark) regardless of the
 * app theme, and restored afterwards. Used by screens that always draw a dark picture behind the status bar
 * (the lyrics page), so the clock and battery never end up dark-on-dark in the light theme.
 */
@Composable
fun ForceStatusBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, lightIcons) {
        val window = (view.context as? Activity)?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previous = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = !lightIcons
            onDispose { controller.isAppearanceLightStatusBars = previous }
        }
    }
}

