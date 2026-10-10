package com.localplayer.music.ui.components

/**
 * The user-configurable order of the library tabs (Search is a fixed control and not part of it).
 * Pure functions so the persistence format and the repair rules are unit tested.
 *
 * Stored as a comma separated list of [LibraryTab] names in SharedPreferences (no database change).
 */
object TabOrder {
    val Default: List<LibraryTab> = LibraryTab.entries.toList()

    /** Missing / malformed / outdated input never throws: unknown names and duplicates are dropped and missing tabs appended in default order. */
    fun parse(raw: String?): List<LibraryTab> {
        if (raw.isNullOrBlank()) return Default
        val seen = LinkedHashSet<LibraryTab>()
        raw.split(',').forEach { token ->
            val tab = LibraryTab.entries.firstOrNull { it.name == token.trim() }
            if (tab != null) seen.add(tab)
        }
        if (seen.isEmpty()) return Default
        Default.forEach { seen.add(it) }
        return seen.toList()
    }

    fun serialize(order: List<LibraryTab>): String = order.joinToString(",") { it.name }

    /** Moves the tab at [index] by [delta] (-1 up, +1 down); out-of-range moves return the list unchanged. */
    fun move(order: List<LibraryTab>, index: Int, delta: Int): List<LibraryTab> {
        val to = index + delta
        if (index !in order.indices || to !in order.indices) return order
        return order.toMutableList().apply { add(to, removeAt(index)) }
    }
}
