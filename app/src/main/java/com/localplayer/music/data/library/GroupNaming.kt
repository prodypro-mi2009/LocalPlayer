package com.localplayer.music.data.library

/** Picks the display text of a group whose members spell the same name differently ("Pop", "pop", "POP"). */
object GroupNaming {
    /** @param variants spelling to track count. Most used spelling wins; ties prefer mixed case, then alphabetical. */
    fun pick(variants: List<Pair<String?, Int>>): String? {
        val named = variants.filter { !it.first.isNullOrBlank() }
        if (named.isEmpty()) return null
        return named
            .groupBy({ it.first!! }, { it.second })
            .map { (text, counts) -> text to counts.sum() }
            .sortedWith(
                compareByDescending<Pair<String, Int>> { it.second }
                    .thenBy { if (it.first == it.first.uppercase() || it.first == it.first.lowercase()) 1 else 0 }
                    .thenBy { it.first },
            )
            .first().first
    }
}
