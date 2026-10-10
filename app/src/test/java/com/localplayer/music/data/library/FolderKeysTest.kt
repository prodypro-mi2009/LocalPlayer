package com.localplayer.music.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderKeysTest {
    @Test fun mediaStoreRelativePathBecomesKey() {
        assertEquals("external_primary:Music/Rock/", FolderKeys.fromRelativePath("external_primary", "Music/Rock/"))
        assertEquals("1234-abcd:Music/", FolderKeys.fromRelativePath("1234-ABCD", "Music/"))
        assertEquals("external_primary:", FolderKeys.fromRelativePath("external_primary", ""))
        assertNull(FolderKeys.fromRelativePath("external_primary", null))
    }

    @Test fun safDocumentIdGivesTheSameKeyAsMediaStore() {
        assertEquals(
            FolderKeys.fromRelativePath("external_primary", "Music/Rock/"),
            FolderKeys.fromFolderDocumentId("primary:Music/Rock"),
        )
        assertEquals("1234-abcd:Music/", FolderKeys.fromFolderDocumentId("1234-ABCD:Music"))
        assertEquals("external_primary:", FolderKeys.fromFolderDocumentId("primary:"))
    }

    @Test fun providersWithoutAPathAreRejected() {
        assertNull(FolderKeys.fromFolderDocumentId("msf:123"))
        assertNull(FolderKeys.fromFolderDocumentId("raw:/storage/emulated/0/Download"))
        assertNull(FolderKeys.fromFolderDocumentId(""))
        assertNull(FolderKeys.fromFolderDocumentId("nocolon"))
    }

    @Test fun legacyDataPaths() {
        assertEquals("external_primary:Music/Rock/", FolderKeys.fromDataPath("/storage/emulated/0/Music/Rock/a.mp3"))
        assertEquals("external_primary:Music/", FolderKeys.fromDataPath("/sdcard/Music/a.mp3"))
        assertEquals("1234-abcd:Songs/", FolderKeys.fromDataPath("/storage/1234-ABCD/Songs/a.mp3"))
        assertEquals("external_primary:", FolderKeys.fromDataPath("/storage/emulated/0/a.mp3"))
        assertNull(FolderKeys.fromDataPath("/data/local/a.mp3"))
        assertNull(FolderKeys.fromDataPath(null))
    }

    @Test fun coversWholeSegmentsOnly() {
        val rock = "external_primary:Music/Rock/"
        assertTrue(FolderKeys.covers(rock, rock))
        assertTrue(FolderKeys.covers(rock, "external_primary:Music/Rock/Live/"))
        assertFalse(FolderKeys.covers(rock, "external_primary:Music/Rockabilly/"))
        assertFalse(FolderKeys.covers(rock, "external_primary:Music/"))
        assertFalse(FolderKeys.covers(rock, null))
        assertFalse(FolderKeys.covers(rock, "external_primary:Music/Rock/Live/", includeSubfolders = false))
        assertTrue(FolderKeys.covers("external_primary:", "external_primary:Anything/"))
        assertFalse(FolderKeys.covers("external_primary:", "1234-abcd:Anything/"))
    }

    @Test fun parts() {
        val key = "external_primary:Music/Rock/"
        assertEquals("external_primary", FolderKeys.volumeOf(key))
        assertEquals("Music/Rock", FolderKeys.relativePathOf(key))
        assertEquals("Rock", FolderKeys.nameOf(key))
        assertEquals("external_primary:Music/", FolderKeys.parentOf(key))
        assertEquals("external_primary:", FolderKeys.parentOf("external_primary:Music/"))
        assertNull(FolderKeys.parentOf("external_primary:"))
    }

    @Test fun mediaStoreUris() {
        assertTrue(FolderKeys.isMediaStoreUri("content://media/external/audio/media/42"))
        assertFalse(FolderKeys.isMediaStoreUri("content://com.android.externalstorage.documents/tree/x/document/y"))
    }
}
