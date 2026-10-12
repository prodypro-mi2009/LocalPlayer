package com.localplayer.music.ui.nav

/**
 * The pure rules of the back stack (no Compose, no Android), so they can be unit tested on the JVM.
 * [NavState] only applies the result of these functions to its observable list.
 *
 * Why this exists: Now Playing used to call `push(Lyrics)` every time, even when Lyrics was already on the
 * stack (Lyrics -> mini player -> Now Playing -> Lyrics), so the stack grew by one entry per round trip and the
 * back arrow had to be pressed that many times. Singleton screens (one instance per stack) fix that.
 */
object NavRules {
    /** Screens that may exist at most once in the stack. Re-opening one goes back to the existing entry. */
    fun isSingleton(screen: Screen): Boolean = when (screen) {
        Screen.Lyrics, Screen.Queue, Screen.Settings, Screen.Search, Screen.Recent,
        Screen.HiddenSongs, Screen.LibraryFolders, Screen.Colors, Screen.Fonts,
        Screen.SettingsAppearance, Screen.SettingsLanguage, Screen.SettingsLibrary, Screen.SettingsLyrics,
        Screen.SettingsYourMix, Screen.SettingsAbout, Screen.YourMix, Screen.TimeUsage, Screen.Top20 -> true
        else -> false
    }

    /**
     * Normal push. Pushing the screen that is already on top is ignored (double taps, repeated events),
     * and singleton screens are never duplicated.
     */
    fun push(stack: List<Screen>, screen: Screen): List<Screen> {
        if (stack.lastOrNull() == screen) return stack
        if (isSingleton(screen)) return openSingleton(stack, screen)
        return stack + screen
    }

    /**
     * Opens a singleton: if it is already in the stack, everything ABOVE it is dropped (so the stack ends with that
     * entry, which keeps its saved scroll position); otherwise it is pushed.
     */
    fun openSingleton(stack: List<Screen>, screen: Screen): List<Screen> {
        val existing = stack.lastIndexOf(screen)
        return if (existing >= 0) stack.subList(0, existing + 1).toList() else stack + screen
    }

    /** One step back. Returns null at the root (nothing to pop). */
    fun pop(stack: List<Screen>): List<Screen>? =
        if (stack.size <= 1) null else stack.subList(0, stack.size - 1).toList()

    /** Replaces the top entry with [screen] (used after creating things). A singleton still stays unique. */
    fun replaceTop(stack: List<Screen>, screen: Screen): List<Screen> {
        val base = if (stack.size > 1) stack.subList(0, stack.size - 1).toList() else stack
        return push(base, screen)
    }

    /**
     * Turns [list] into [target] with the fewest writes: the common prefix is left untouched (so an observable
     * list does not recompose entries that did not change), the rest is dropped and appended.
     */
    fun applyTo(list: MutableList<Screen>, target: List<Screen>) {
        var common = 0
        while (common < list.size && common < target.size && list[common] == target[common]) common++
        while (list.size > common) list.removeAt(list.lastIndex)
        for (i in common until target.size) list.add(target[i])
    }
}
