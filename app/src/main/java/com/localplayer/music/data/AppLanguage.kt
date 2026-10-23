package com.localplayer.music.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** The two interface languages. Music metadata is never translated. */
enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    GREEK("el"),
    ;

    companion object {
        /** Unknown / missing value -> [systemTag] if it is supported, otherwise English. */
        fun fromTag(tag: String?, systemTag: String = Locale.getDefault().language): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: entries.firstOrNull { it.tag == systemTag } ?: ENGLISH

        /** Wraps [base] so every resource lookup (strings.xml / values-el) uses the saved language. */
        fun wrap(base: Context): Context {
            val saved = base.getSharedPreferences(AppSettings.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(AppSettings.KEY_LANGUAGE, null)
            val locale = Locale.forLanguageTag(fromTag(saved).tag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            return base.createConfigurationContext(config)
        }
    }
}
