package com.localplayer.music.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderRulesTest {
    private val day = 24L * 60 * 60 * 1000

    @Test fun overviewMarksExcludedAndChildren() {
        val rows = FolderOverview.build(
            counts = mapOf("v:Music/" to 3, "v:Music/Rock/" to 5, "v:Podcasts/" to 2),
            excluded = setOf("v:Music/"),
        )
        val byKey = rows.associateBy { it.key }
        assertTrue(byKey.getValue("v:Music/").excluded)
        assertEquals("v:Music/", byKey.getValue("v:Music/Rock/").excludedBy)
        assertFalse(byKey.getValue("v:Podcasts/").excluded)
        assertNull(byKey.getValue("v:Podcasts/").excludedBy)
    }

    @Test fun excludedFolderWithoutSongsIsStillListed() {
        val rows = FolderOverview.build(counts = emptyMap(), excluded = setOf("v:Old/"))
        assertEquals(1, rows.size)
        assertEquals(0, rows.single().trackCount)
        assertTrue(rows.single().excluded)
    }

    @Test fun hiddenEntryFollowsAMovedFile() {
        val plan = HiddenReconciler.plan(
            orphans = listOf(HiddenReconciler.Orphan("old", 1000, "a.mp3", 500)),
            candidates = listOf(
                HiddenReconciler.Candidate("new", "a.mp3", 500),
                HiddenReconciler.Candidate("other", "b.mp3", 500),
            ),
            now = 2000,
        )
        assertEquals(mapOf("old" to "new"), plan.rekey)
        assertTrue(plan.drop.isEmpty())
    }

    @Test fun ambiguousMatchIsNotGuessed() {
        val plan = HiddenReconciler.plan(
            orphans = listOf(HiddenReconciler.Orphan("old", 1000, "a.mp3", 500)),
            candidates = listOf(
                HiddenReconciler.Candidate("n1", "a.mp3", 500),
                HiddenReconciler.Candidate("n2", "a.mp3", 500),
            ),
            now = 2000,
        )
        assertTrue(plan.rekey.isEmpty())
    }

    @Test fun twoOrphansCannotClaimOneFile() {
        val plan = HiddenReconciler.plan(
            orphans = listOf(
                HiddenReconciler.Orphan("o1", 1000, "a.mp3", 500),
                HiddenReconciler.Orphan("o2", 1000, "a.mp3", 500),
            ),
            candidates = listOf(HiddenReconciler.Candidate("n", "a.mp3", 500)),
            now = 2000,
        )
        assertTrue(plan.rekey.isEmpty())
    }

    @Test fun oldOrphansAreDroppedRecentOnesKept() {
        val now = 100 * day
        val plan = HiddenReconciler.plan(
            orphans = listOf(
                HiddenReconciler.Orphan("recent", now - 2 * day, "a.mp3", 1),
                HiddenReconciler.Orphan("old", now - 40 * day, "b.mp3", 1),
                HiddenReconciler.Orphan("unknown", now - 40 * day, null, null),
            ),
            candidates = emptyList(),
            now = now,
        )
        assertEquals(listOf("old", "unknown"), plan.drop)
    }

    @Test fun folderSyncAddsNewAndRemovesGone() {
        val diff = FolderPlaylistSync.diff(current = listOf("a", "b", "c"), desired = listOf("a", "c", "d", "e"))
        assertEquals(listOf("d", "e"), diff.add)
        assertEquals(listOf("b"), diff.remove)
        assertTrue(diff.changed)
        assertFalse(diff.folderLooksUnavailable)
    }

    @Test fun repeatedSyncChangesNothing() {
        val diff = FolderPlaylistSync.diff(current = listOf("a", "b"), desired = listOf("b", "a"))
        assertFalse(diff.changed)
    }

    @Test fun duplicatesInTheFolderListAreAddedOnce() {
        val diff = FolderPlaylistSync.diff(current = emptyList(), desired = listOf("a", "a", "b"))
        assertEquals(listOf("a", "b"), diff.add)
    }

    @Test fun emptyFolderDoesNotWipeThePlaylist() {
        val diff = FolderPlaylistSync.diff(current = listOf("a", "b"), desired = emptyList())
        assertTrue(diff.folderLooksUnavailable)
        assertFalse(diff.changed)
        assertTrue(diff.remove.isEmpty())
    }

    @Test fun emptyFolderAndEmptyPlaylistIsFine() {
        val diff = FolderPlaylistSync.diff(emptyList(), emptyList())
        assertFalse(diff.folderLooksUnavailable)
        assertFalse(diff.changed)
    }
}
