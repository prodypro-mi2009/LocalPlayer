package com.localplayer.music.data

import android.content.Context
import android.os.Build
import com.localplayer.music.data.mix.MixRefresh
import com.localplayer.music.data.mix.MixSettings
import com.localplayer.music.data.personalization.ControlColors
import com.localplayer.music.data.personalization.FontChoices
import com.localplayer.music.data.personalization.LyricsAlignment
import com.localplayer.music.data.personalization.PureMode
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

    private val _controlColors = MutableStateFlow(ControlColors.parse(prefs.getString(KEY_CONTROL_COLORS, null)))
    /** The user's own colours per button group (empty = normal theme colours). */
    val controlColors: StateFlow<ControlColors> = _controlColors.asStateFlow()

    fun setControlColors(colors: ControlColors) {
        prefs.edit().apply { colors.serialize()?.let { putString(KEY_CONTROL_COLORS, it) } ?: remove(KEY_CONTROL_COLORS) }.apply()
        _controlColors.value = colors
    }

    private val _fontChoices = MutableStateFlow(FontChoices.parse(prefs.getString(KEY_FONT_CHOICES, null)))
    /** The user's font per text category (empty = default fonts). */
    val fontChoices: StateFlow<FontChoices> = _fontChoices.asStateFlow()

    fun setFontChoices(choices: FontChoices) {
        prefs.edit().apply { choices.serialize()?.let { putString(KEY_FONT_CHOICES, it) } ?: remove(KEY_FONT_CHOICES) }.apply()
        _fontChoices.value = choices
    }

    private val _customFontVersion = MutableStateFlow(prefs.getLong(KEY_CUSTOM_FONT, 0L))
    /** 0 = no imported font file; otherwise changes every time a font is imported (so the UI reloads it). */
    val customFontVersion: StateFlow<Long> = _customFontVersion.asStateFlow()

    fun setCustomFontVersion(version: Long) {
        prefs.edit().putLong(KEY_CUSTOM_FONT, version).apply()
        _customFontVersion.value = version
    }

    private val _pureMode = MutableStateFlow(PureMode.fromKey(prefs.getString(KEY_PURE_MODE, null)))
    /** Pure black / pure white appearance (OFF = normal theme). */
    val pureMode: StateFlow<PureMode> = _pureMode.asStateFlow()

    fun setPureMode(mode: PureMode) {
        prefs.edit().putString(KEY_PURE_MODE, mode.key).apply()
        _pureMode.value = mode
    }

    private val _lyricsAlignment = MutableStateFlow(LyricsAlignment.fromKey(prefs.getString(KEY_LYRICS_ALIGNMENT, null)))
    val lyricsAlignment: StateFlow<LyricsAlignment> = _lyricsAlignment.asStateFlow()

    fun setLyricsAlignment(value: LyricsAlignment) {
        prefs.edit().putString(KEY_LYRICS_ALIGNMENT, value.key).apply()
        _lyricsAlignment.value = value
    }

    private val _yourMixCardDismissed = MutableStateFlow(prefs.getBoolean(KEY_YOUR_MIX_DISMISSED, false))
    /** True after the user tapped the Your Mix card away on Home; Settings > Your Mix brings it back. */
    val yourMixCardDismissed: StateFlow<Boolean> = _yourMixCardDismissed.asStateFlow()

    fun setYourMixCardDismissed(value: Boolean) {
        prefs.edit().putBoolean(KEY_YOUR_MIX_DISMISSED, value).apply()
        _yourMixCardDismissed.value = value
    }

    private val _mixSettings = MutableStateFlow(MixSettings.parse(prefs.getString(KEY_MIX_SETTINGS, null)))
    /** How Your Mix is built (strategy, size, window, recency weight, variety, avoid-recent). */
    val mixSettings: StateFlow<MixSettings> = _mixSettings.asStateFlow()

    fun setMixSettings(value: MixSettings) {
        val v = value.normalized()
        prefs.edit().putString(KEY_MIX_SETTINGS, v.serialize()).apply()
        _mixSettings.value = v
    }

    /** The last generated Mix, so it stays the same between launches until it needs a refresh. */
    var mixSnapshot: MixRefresh.Snapshot?
        get() = MixRefresh.Snapshot.parse(prefs.getString(KEY_MIX_SNAPSHOT, null))
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY_MIX_SNAPSHOT) else putString(KEY_MIX_SNAPSHOT, value.serialize()) }.apply()
        }

    private val _autoUpdateCheck = MutableStateFlow(prefs.getBoolean(KEY_AUTO_UPDATE, true))
    /** Check GitHub Releases for a newer version at most once a day after the app opens. */
    val autoUpdateCheck: StateFlow<Boolean> = _autoUpdateCheck.asStateFlow()

    fun setAutoUpdateCheck(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_UPDATE, value).apply()
        _autoUpdateCheck.value = value
    }

    var lastUpdateCheckAt: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) { prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply() }

    /** The newest version an automatic check found (empty = none), shown in About. */
    var knownNewerVersion: String
        get() = prefs.getString(KEY_KNOWN_NEWER, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_KNOWN_NEWER, value).apply() }

    companion object {
        private const val KEY_AUTO_UPDATE = "autoUpdateCheck"
        private const val KEY_LAST_UPDATE_CHECK = "lastUpdateCheckAt"
        private const val KEY_KNOWN_NEWER = "knownNewerVersion"
        private const val KEY_MIX_SETTINGS = "mixSettings"
        private const val KEY_MIX_SNAPSHOT = "mixSnapshot"
        private const val KEY_PURE_MODE = "pureMode"
        private const val KEY_LYRICS_ALIGNMENT = "lyricsAlignment"
        private const val KEY_YOUR_MIX_DISMISSED = "yourMixCardDismissed"
        private const val KEY_CONTROL_COLORS = "controlColors"
        private const val KEY_FONT_CHOICES = "fontChoices"
        private const val KEY_CUSTOM_FONT = "customFontVersion"
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
