package com.localplayer.music.ui.nav

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver

/**
 * Back stack that survives rotation and process restarts. All decisions (no duplicates of Lyrics / Queue /
 * Settings / Search / Recent, ignoring a repeated push of the top screen, pop at the root) live in [NavRules];
 * this class only applies them to an observable list with the smallest possible number of writes.
 */
class NavState(initial: List<Screen> = listOf(Screen.Home)) {
    val stack = mutableStateListOf<Screen>().apply { addAll(initial.ifEmpty { listOf(Screen.Home) }) }

    val current: Screen get() = stack.last()

    fun push(screen: Screen) = applyTarget(NavRules.push(stack.toList(), screen))

    /** Returns false when already at the root. */
    fun pop(): Boolean {
        val next = NavRules.pop(stack.toList()) ?: return false
        applyTarget(next)
        return true
    }

    /** Removes the top screen and opens [screen] instead (used after creating things). */
    fun replaceTop(screen: Screen) = applyTarget(NavRules.replaceTop(stack.toList(), screen))

    private fun applyTarget(target: List<Screen>) = NavRules.applyTo(stack, target)

    companion object {
        val Saver = listSaver<NavState, String>(
            save = { state -> state.stack.map { it.encode() } },
            restore = { saved -> NavState(saved.map { decodeScreen(it) }) },
        )
    }
}
