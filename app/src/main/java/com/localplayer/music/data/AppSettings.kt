package com.localplayer.music.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Small persistent app settings (SharedPreferences; local only). Every setting is exposed as a StateFlow. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _useMediaStoreScanner = MutableStateFlow(
        prefs.getBoolean(KEY_MEDIASTORE, false) && mediaStoreScannerSupported(),
    )
    /** ON = MediaStore-only fast scanner. Always false below Android 11. */
    val useMediaStoreScanner: StateFlow<Boolean> = _useMediaStoreScanner.asStateFlow()

    fun setUseMediaStoreScanner(enabled: Boolean) {
        val value = enabled && mediaStoreScannerSupported()
        prefs.edit().putBoolean(KEY_MEDIASTORE, value).apply()
        _useMediaStoreScanner.value = value
    }

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!) }
            .getOrDefault(ThemeMode.SYSTEM),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC, true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
        _dynamicColor.value = enabled
    }

    private val _artworkColors = MutableStateFlow(prefs.getBoolean(KEY_ARTWORK_COLORS, true))
    /** Derive player colors from the current album artwork. */
    val artworkColors: StateFlow<Boolean> = _artworkColors.asStateFlow()

    fun setArtworkColors(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ARTWORK_COLORS, enabled).apply()
        _artworkColors.value = enabled
    }

    private val _backgroundVersion = MutableStateFlow(prefs.getLong(KEY_BACKGROUND, 0L))
    /** 0 = no custom background; otherwise changes every time the picture changes (cache key). */
    val backgroundVersion: StateFlow<Long> = _backgroundVersion.asStateFlow()

    fun setBackgroundVersion(version: Long) {
        prefs.edit().putLong(KEY_BACKGROUND, version).apply()
        _backgroundVersion.value = version
    }

    private val _backgroundDim = MutableStateFlow(clampDim(prefs.getFloat(KEY_BACKGROUND_DIM, DEFAULT_DIM)))
    /** How strongly the custom background is darkened/veiled (0 = picture as is, 1 = fully covered). */
    val backgroundDim: StateFlow<Float> = _backgroundDim.asStateFlow()

    fun setBackgroundDim(value: Float) {
        val v = clampDim(value)
        prefs.edit().putFloat(KEY_BACKGROUND_DIM, v).apply()
        _backgroundDim.value = v
    }

    private val _language = MutableStateFlow(AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, null)))
    /** Interface language (English / Greek). Applied by recreating the activity with a wrapped context. */
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.tag).apply()
        _language.value = language
    }

    private val _tabOrder = MutableStateFlow(prefs.getString(KEY_TAB_ORDER, null))
    /** Raw saved tab order (parsed and repaired by `TabOrder.parse`); null = default order. */
    val tabOrder: StateFlow<String?> = _tabOrder.asStateFlow()

    fun setTabOrder(raw: String?) {
        prefs.edit().apply { if (raw == null) remove(KEY_TAB_ORDER) else putString(KEY_TAB_ORDER, raw) }.apply()
        _tabOrder.value = raw
    }

    companion object {
        const val PREFS_NAME = "settings"
        const val KEY_LANGUAGE = "language"
        private const val KEY_TAB_ORDER = "tabOrder"
        const val DEFAULT_DIM = 0.78f
        /** Keeps the persisted value inside 0..1 (and recovers from NaN). */
        fun clampDim(value: Float): Float = if (value.isNaN()) DEFAULT_DIM else value.coerceIn(0f, 1f)

        fun mediaStoreScannerSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        private const val KEY_MEDIASTORE = "useMediaStoreScanner"
        private const val KEY_THEME = "themeMode"
        private const val KEY_DYNAMIC = "dynamicColor"
        private const val KEY_ARTWORK_COLORS = "artworkColors"
        private const val KEY_BACKGROUND = "backgroundVersion"
        private const val KEY_BACKGROUND_DIM = "backgroundDim"
    }
}
