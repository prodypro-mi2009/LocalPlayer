package com.localplayer.music.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageTest {
    @Test fun savedTagWins() {
        assertEquals(AppLanguage.GREEK, AppLanguage.fromTag("el", systemTag = "en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en", systemTag = "el"))
    }

    @Test fun missingOrUnknownFollowsSupportedSystemLanguageElseEnglish() {
        assertEquals(AppLanguage.GREEK, AppLanguage.fromTag(null, systemTag = "el"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag(null, systemTag = "de"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("xx", systemTag = "fr"))
    }

    @Test fun exactlyTwoLanguages() = assertEquals(listOf("en", "el"), AppLanguage.entries.map { it.tag })

    private fun names(path: String): Set<String> {
        val dir = listOf("app/src/main/res", "src/main/res").map(::File).first { it.exists() }
        return Regex("""<string\s+name="([^"]+)"(?![^>]*translatable="false")""")
            .findAll(File(dir, path).readText()).map { it.groupValues[1] }.toSet()
    }

    /** Resource completeness: every English string has a Greek one and vice versa. */
    @Test fun greekAndEnglishResourcesAreComplete() {
        val en = names("values/strings.xml")
        val el = names("values-el/strings.xml")
        assertEquals("missing in Greek", emptySet<String>(), en - el)
        assertEquals("missing in English", emptySet<String>(), el - en)
        assertTrue(en.contains("settings_language") && en.contains("settings_tab_order"))
    }
}
