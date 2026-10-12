package com.localplayer.music.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavRulesTest {
    private val home = listOf<Screen>(Screen.Home)

    /** Applies the rules exactly like the UI does: Now Playing's Lyrics button = push(Lyrics). */
    private fun lyricsRoundTrips(start: List<Screen>, times: Int): List<Screen> {
        var stack = NavRules.push(start, Screen.Lyrics)
        repeat(times) {
            // mini player -> Now Playing -> Lyrics button again
            stack = NavRules.push(stack, Screen.Lyrics)
        }
        return stack
    }

    @Test fun repeatedLyricsOpensNeverGrowTheStack() {
        val album = home + Screen.Album("believer", "Evolve")
        val stack = lyricsRoundTrips(album, times = 7)
        assertEquals(album + Screen.Lyrics, stack)
    }

    @Test fun oneBackPressLeavesLyricsAfterAnyNumberOfRoundTrips() {
        val album = home + Screen.Album("believer", "Evolve")
        val stack = lyricsRoundTrips(album, times = 25)
        assertEquals(album, NavRules.pop(stack))
    }

    @Test fun lyricsThenQueueThenLyricsReturnsToTheExistingLyricsEntry() {
        var stack = NavRules.push(home, Screen.Lyrics)
        stack = NavRules.push(stack, Screen.Queue)
        stack = NavRules.push(stack, Screen.Lyrics)
        assertEquals(home + Screen.Lyrics, stack)
    }

    @Test fun queueAndSettingsAndSearchAndRecentAreSingletons() {
        for (screen in listOf(Screen.Queue, Screen.Settings, Screen.Search, Screen.Recent)) {
            var stack = NavRules.push(home, screen)
            stack = NavRules.push(stack, Screen.Artist("a", "A"))
            stack = NavRules.push(stack, screen)
            assertEquals("$screen", home + screen, stack)
        }
    }

    @Test fun pushingTheTopScreenAgainIsIgnoredForEveryScreen() {
        val album = Screen.Album("k", "T")
        val stack = home + album
        assertEquals(stack, NavRules.push(stack, album))
    }

    @Test fun detailScreensStillStackNormally() {
        var stack = home
        stack = NavRules.push(stack, Screen.Album("k", "T"))
        stack = NavRules.push(stack, Screen.Artist("a", "A"))
        stack = NavRules.push(stack, Screen.Album("k", "T")) // not on top any more: allowed, like any real navigation
        assertEquals(4, stack.size)
    }

    @Test fun popAtRootReturnsNullAndNeverEmptiesTheStack() {
        assertNull(NavRules.pop(home))
        assertEquals(home, NavRules.pop(home + Screen.Settings))
    }

    @Test fun replaceTopKeepsSingletonsUnique() {
        val stack = home + Screen.Queue + Screen.Lyrics
        assertEquals(home + Screen.Queue + Screen.Lyrics, NavRules.replaceTop(stack, Screen.Lyrics))
        assertEquals(home + Screen.Queue, NavRules.replaceTop(home + Screen.Queue + Screen.Lyrics, Screen.Queue))
        assertEquals(home + Screen.Playlist(3), NavRules.replaceTop(home + Screen.Settings, Screen.Playlist(3)))
    }

    @Test fun openSingletonDropsOnlyTheEntriesAboveIt() {
        val stack = home + Screen.Settings + Screen.Lyrics + Screen.Queue
        assertEquals(home + Screen.Settings + Screen.Lyrics, NavRules.openSingleton(stack, Screen.Lyrics))
    }

    @Test fun screensRoundTripThroughEncodeDecode() {
        val all = listOf(
            Screen.Search, Screen.Recent, Screen.Settings, Screen.Queue, Screen.Lyrics,
            Screen.LyricsEditor(9), Screen.Artist("k", "n"), Screen.Album("g", "t"), Screen.Genre("g", "n"),
            Screen.Year(1999), Screen.Playlist(4), Screen.AddTracks(4), Screen.EditMetadata(12),
        )
        for (s in all) assertEquals(s, decodeScreen(s.encode()))
    }

    // ---- the same operations the way NavState performs them on its observable list ----

    private class FakeNav {
        val stack = ArrayList<Screen>(listOf(Screen.Home))
        fun push(s: Screen) = NavRules.applyTo(stack, NavRules.push(stack.toList(), s))
        fun pop(): Boolean {
            val next = NavRules.pop(stack.toList()) ?: return false
            NavRules.applyTo(stack, next)
            return true
        }
    }

    @Test fun navStateStyleApplicationKeepsTheStackConsistent() {
        val nav = FakeNav()
        nav.push(Screen.Album("believer", "Evolve"))
        nav.push(Screen.Lyrics)
        repeat(10) { nav.push(Screen.Lyrics) }          // Now Playing -> Lyrics, ten times
        assertEquals(listOf(Screen.Home, Screen.Album("believer", "Evolve"), Screen.Lyrics), nav.stack)
        assertEquals(true, nav.pop())                   // ONE press of the arrow / device Back
        assertEquals(listOf(Screen.Home, Screen.Album("believer", "Evolve")), nav.stack)
        assertEquals(true, nav.pop())
        assertEquals(listOf<Screen>(Screen.Home), nav.stack)
        assertEquals(false, nav.pop())                  // at the root: nothing left to pop
        assertEquals(listOf<Screen>(Screen.Home), nav.stack)
    }

    @Test fun mixedLyricsQueueSettingsRoundTripsNeverNeedMoreBackPressesThanDistinctScreens() {
        val nav = FakeNav()
        val script = listOf(
            Screen.Lyrics, Screen.Queue, Screen.Lyrics, Screen.Settings, Screen.Lyrics, Screen.Queue, Screen.Queue,
            Screen.Lyrics, Screen.Lyrics,
        )
        script.forEach(nav::push)
        var presses = 0
        while (nav.pop()) presses++
        assertEquals(listOf<Screen>(Screen.Home), nav.stack)
        // Settings, Lyrics, Queue are the only distinct singletons that can still be on the stack.
        assertEquals(true, presses <= 3)
    }

    @Test fun applyToDoesNotRewriteTheUnchangedPrefix() {
        val list = arrayListOf<Screen>(Screen.Home, Screen.Settings, Screen.Lyrics)
        NavRules.applyTo(list, listOf(Screen.Home, Screen.Settings))
        assertEquals(listOf(Screen.Home, Screen.Settings), list)
        NavRules.applyTo(list, listOf(Screen.Home, Screen.Queue, Screen.Lyrics))
        assertEquals(listOf(Screen.Home, Screen.Queue, Screen.Lyrics), list)
        NavRules.applyTo(list, listOf(Screen.Home))
        assertEquals(listOf<Screen>(Screen.Home), list)
    }
}

class AppearanceRoutesTest {
    @org.junit.Test fun colorsAndFontsAreSingletonsAndSurviveEncoding() {
        for (screen in listOf(Screen.Colors, Screen.Fonts)) {
            org.junit.Assert.assertTrue(NavRules.isSingleton(screen))
            org.junit.Assert.assertEquals(screen, decodeScreen(screen.encode()))
        }
    }
}

class SettingsRoutesTest {
    @org.junit.Test fun everySettingsSubScreenIsASingletonAndSurvivesEncoding() {
        val all = listOf(
            Screen.SettingsAppearance, Screen.SettingsLanguage, Screen.SettingsLibrary,
            Screen.SettingsLyrics, Screen.SettingsYourMix, Screen.SettingsAbout,
        )
        for (screen in all) {
            org.junit.Assert.assertTrue(NavRules.isSingleton(screen))
            org.junit.Assert.assertEquals(screen, decodeScreen(screen.encode()))
        }
        org.junit.Assert.assertEquals(all.size, all.map { it.encode() }.toSet().size)
    }

    @org.junit.Test fun appearanceToColorsIsOneBackPressPerLevel() {
        val home = listOf<Screen>(Screen.Home)
        var stack = NavRules.push(NavRules.push(NavRules.push(home, Screen.Settings), Screen.SettingsAppearance), Screen.Colors)
        stack = NavRules.push(stack, Screen.Colors)                       // double tap: ignored
        org.junit.Assert.assertEquals(listOf(Screen.Home, Screen.Settings, Screen.SettingsAppearance, Screen.Colors), stack)
        stack = NavRules.pop(stack)!!
        org.junit.Assert.assertEquals(Screen.SettingsAppearance, stack.last())
        stack = NavRules.pop(stack)!!
        org.junit.Assert.assertEquals(Screen.Settings, stack.last())
    }

    @org.junit.Test fun statsAndMixScreensAreSingletonsAndSurviveEncoding() {
        for (screen in listOf(Screen.YourMix, Screen.TimeUsage, Screen.Top20)) {
            org.junit.Assert.assertTrue(NavRules.isSingleton(screen))
            org.junit.Assert.assertEquals(screen, decodeScreen(screen.encode()))
        }
    }
}
