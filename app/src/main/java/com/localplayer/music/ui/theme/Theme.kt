package com.localplayer.music.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.localplayer.music.data.ThemeMode

/** All fixed colors of the app live here. */
object AppColors {
    /** Main background of the dark theme: true black (AMOLED). */
    val TrueBlack = Color(0xFF000000)

    /** Main background of the light theme: true white. */
    val TrueWhite = Color(0xFFFFFFFF)

    /**
     * The Favorite heart. A FIXED colour on purpose: no dynamic colour, artwork tone or user colour setting may
     * change it (it is also the one colour the "custom colours" feature must never override).
     */
    val FavoriteRed = Color(0xFFFF1744)

    /** Seed-based fallback palette used on Android 8-11 or when dynamic color is off. */
    val SeedPrimaryDark = Color(0xFF6FDBA0)
    val SeedPrimaryLight = Color(0xFF006D40)
}

private val fallbackDark = darkColorScheme(
    primary = AppColors.SeedPrimaryDark,
    onPrimary = Color(0xFF00391E),
    primaryContainer = Color(0xFF005230),
    onPrimaryContainer = Color(0xFF8CF8BB),
    secondary = Color(0xFFB3CCBB),
    tertiary = Color(0xFFA3CDDD),
    // Controlled neutral steps above true black, so rows, cards and the navigation pill stay visible.
    surfaceContainerLowest = AppColors.TrueBlack,
    surfaceContainerLow = Color(0xFF0C0C0C),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF262626),
)

private val fallbackLight = lightColorScheme(
    primary = AppColors.SeedPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8CF8BB),
    onPrimaryContainer = Color(0xFF002111),
    secondary = Color(0xFF4E6354),
    tertiary = Color(0xFF3A6473),
    surfaceContainerLowest = AppColors.TrueWhite,
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE0E0E0),
)

/**
 * Material You dynamic color on Android 12+ (if enabled), a stable seed palette otherwise.
 * Whatever the palette, the MAIN BACKGROUND is always exactly #000000 (dark) or #FFFFFF (light);
 * the subtle surface containers keep their tonal values so rows, cards and sheets stay distinguishable.
 */
@Composable
fun LocalPlayerTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = themeMode.isDark(isSystemInDarkTheme())
    val context = LocalContext.current
    val base: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> fallbackDark
        else -> fallbackLight
    }
    val pure = if (dark) AppColors.TrueBlack else AppColors.TrueWhite
    val scheme = base.copy(background = pure, surface = pure)
    MaterialTheme(colorScheme = scheme, content = content)
}
