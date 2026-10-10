package com.localplayer.music.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupNamingTest {
    @Test fun mostUsedSpellingWins() =
        assertEquals("Pop", GroupNaming.pick(listOf("Pop" to 5, "pop" to 2, "POP" to 1)))

    @Test fun tiePrefersMixedCase() =
        assertEquals("Pop", GroupNaming.pick(listOf("pop" to 2, "Pop" to 2, "POP" to 2)))

    @Test fun nothingNamedIsNull() = assertNull(GroupNaming.pick(listOf(null to 3, " " to 1)))
}
