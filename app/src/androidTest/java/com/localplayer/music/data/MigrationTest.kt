package com.localplayer.music.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localplayer.music.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs each real migration edge and each supported historical schema (3..7) through version 8,
 * preserving existing rows and asking Room to validate the final schema against its version 8 entities.
 * Requires a device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private val dbName = "migration-test"

    @Test
    fun migrate4To5_addsFavorites_andKeepsEverythingElse() {
        helper.createDatabase(dbName, 4).apply {
            execSQL(
                "INSERT INTO tracks (id, uri, mediaType, displayName, mimeType, sizeBytes, lastModified, title, artist, " +
                    "albumArtist, album, genre, year, trackNumber, discNumber, durationMs, hasArtwork, isAvailable, " +
                    "isExcluded, scannedAt) VALUES (1, 'content://media/external/audio/media/1', 0, 'a.mp3', " +
                    "'audio/mpeg', 1000, 5000, 'Song', 'Rack', NULL, 'Album', 'Pop', 2025, 1, 1, 180000, 0, 1, 0, 9)",
            )
            execSQL("INSERT INTO playlists (id, name, createdAt, updatedAt) VALUES (1, 'Mix', 1, 1)")
            execSQL("INSERT INTO playlist_tracks (playlistId, trackUri, position, addedAt) VALUES (1, 'content://media/external/audio/media/1', 0, 1)")
            execSQL("INSERT INTO listening_history (trackUri, playedAt) VALUES ('content://media/external/audio/media/1', 5)")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 5, true, AppDatabase.MIGRATION_4_5)
        db.query("SELECT COUNT(*) FROM tracks").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM playlist_tracks").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM listening_history").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.execSQL("INSERT INTO favorites (trackUri, addedAt) VALUES ('content://media/external/audio/media/1', 7)")
        db.query("SELECT COUNT(*) FROM favorites").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }

    @Test
    fun migrate3To4_keepsExistingTracks_andAddsTheNewTables() {
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO tracks (id, uri, mediaType, displayName, mimeType, sizeBytes, lastModified, title, artist, " +
                    "albumArtist, album, genre, year, trackNumber, discNumber, durationMs, hasArtwork, isAvailable, " +
                    "isExcluded, scannedAt) VALUES (7, 'content://media/external/audio/media/7', 0, 'a.mp3', " +
                    "'audio/mpeg', 1000, 5000, 'Song', 'Rack feat. X', NULL, 'Album X', 'Hip-Hop', 2025, 3, 1, " +
                    "180000, 1, 1, 0, 9)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 4, true, AppDatabase.MIGRATION_3_4)

        db.query("SELECT id, uri, title, artist, album, year, indexVersion, dateAdded, primaryArtistKey FROM tracks").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(7L, c.getLong(0))
            assertEquals("content://media/external/audio/media/7", c.getString(1))
            assertEquals("Song", c.getString(2))
            assertEquals("Rack feat. X", c.getString(3))
            assertEquals("Album X", c.getString(4))
            assertEquals(2025, c.getInt(5))
            // Rows are flagged for a one-time re-read of the file so the new columns get filled.
            assertEquals(0, c.getInt(6))
            assertEquals(0L, c.getLong(7))
            assertEquals(true, c.isNull(8))
        }

        // New tables work, and the playlist foreign key is enforced.
        db.execSQL("INSERT INTO playlists (id, name, createdAt, updatedAt) VALUES (1, 'P', 1, 1)")
        db.execSQL("INSERT INTO playlist_tracks (playlistId, trackUri, position, addedAt) VALUES (1, 'u', 0, 1)")
        db.execSQL("INSERT INTO listening_history (trackUri, playedAt) VALUES ('u', 1)")
        db.execSQL("INSERT INTO primary_artist_overrides (trackUri, primaryArtist) VALUES ('u', 'Rack')")
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    @Test
    fun migrate5To6_keepsEverything_andAddsFolderAndHiddenTables() {
        helper.createDatabase(dbName, 5).apply {
            execSQL(
                "INSERT INTO tracks (id, uri, mediaType, displayName, mimeType, sizeBytes, lastModified, title, artist, " +
                    "albumArtist, album, genre, year, trackNumber, discNumber, durationMs, hasArtwork, isAvailable, " +
                    "isExcluded, scannedAt) VALUES (3, 'content://media/external/audio/media/3', 0, 'a.mp3', " +
                    "'audio/mpeg', 1000, 5000, 'Song', 'Rack', NULL, 'Album', 'Pop', 2025, 1, 1, 180000, 0, 1, 0, 9)",
            )
            execSQL("INSERT INTO playlists (id, name, createdAt, updatedAt) VALUES (1, 'P', 1, 1)")
            execSQL("INSERT INTO favorites (trackUri, addedAt) VALUES ('content://media/external/audio/media/3', 2)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, AppDatabase.MIGRATION_5_6)

        db.query("SELECT id, title, folderKey FROM tracks").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(3L, c.getLong(0))
            assertEquals("Song", c.getString(1))
            // Filled in by the next scan, without re-reading the file.
            assertEquals(true, c.isNull(2))
        }
        db.query("SELECT name, sourceFolderKey, sourceIncludesSubfolders, folderSyncStatus FROM playlists").use { c ->
            c.moveToFirst()
            assertEquals("P", c.getString(0))
            assertEquals(true, c.isNull(1))
            assertEquals(1, c.getInt(2))
            assertEquals(0, c.getInt(3))
        }
        db.query("SELECT COUNT(*) FROM favorites").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        db.execSQL("INSERT INTO hidden_tracks (trackUri, hiddenAt, displayName, sizeBytes) VALUES ('u', 1, 'a.mp3', 10)")
        db.execSQL("INSERT INTO excluded_folders (prefix, excludedAt) VALUES ('external_primary:Podcasts/', 1)")
        db.execSQL("INSERT INTO library_folders (treeUri, folderKey, label, addedAt) VALUES ('t', 'k', 'L', 1)")
    }

    @Test fun migrate3To8_keepsTracks_andValidatesTheCurrentSchema() = assertUpgradeToEight(3)

    @Test fun migrate4To8_keepsExistingLibraryData_andValidatesTheCurrentSchema() = assertUpgradeToEight(4)

    @Test fun migrate5To8_keepsFavorites_andValidatesTheCurrentSchema() = assertUpgradeToEight(5)

    @Test fun migrate6To8_keepsFolderData_andValidatesTheCurrentSchema() = assertUpgradeToEight(6)

    @Test fun migrate7To8_keepsListeningStats_andValidatesTheCurrentSchema() = assertUpgradeToEight(7)

    /** Runs the registered migration chain from a real historical schema through version 8. */
    private fun assertUpgradeToEight(startVersion: Int) {
        val name = "migration-$startVersion-to-8"
        helper.createDatabase(name, startVersion).apply {
            execSQL(
                "INSERT INTO tracks (id, uri, mediaType, displayName, mimeType, sizeBytes, lastModified, title, artist, " +
                    "albumArtist, album, genre, year, trackNumber, discNumber, durationMs, hasArtwork, isAvailable, " +
                    "isExcluded, scannedAt) VALUES (7, 'content://test/7', 0, 'legacy.mp3', 'audio/mpeg', 1000, 5000, " +
                    "'Legacy Song', 'Legacy Artist', NULL, 'Legacy Album', 'Pop', 2020, 2, 1, 180000, 0, 1, 0, 9)",
            )
            if (startVersion >= 4) {
                execSQL("INSERT INTO playlists (id, name, createdAt, updatedAt) VALUES (1, 'Legacy Mix', 10, 20)")
                execSQL("INSERT INTO playlist_tracks (playlistId, trackUri, position, addedAt) VALUES (1, 'content://test/7', 0, 11)")
                execSQL("INSERT INTO listening_history (trackUri, playedAt) VALUES ('content://test/7', 12)")
                execSQL("INSERT INTO primary_artist_overrides (trackUri, primaryArtist) VALUES ('content://test/7', 'Override Artist')")
            }
            if (startVersion >= 5) {
                execSQL("INSERT INTO favorites (trackUri, addedAt) VALUES ('content://test/7', 13)")
            }
            if (startVersion >= 6) {
                execSQL("INSERT INTO hidden_tracks (trackUri, hiddenAt, displayName, sizeBytes) VALUES ('hidden', 14, 'hidden.mp3', 200)")
                execSQL("INSERT INTO excluded_folders (prefix, excludedAt) VALUES ('external_primary:Podcasts/', 15)")
                execSQL("INSERT INTO library_folders (treeUri, folderKey, label, addedAt) VALUES ('tree', 'folder', 'Folder', 16)")
            }
            if (startVersion >= 7) {
                execSQL("INSERT INTO listening_days (`day`, trackUri, listenedMs, plays, lastListenedAt) VALUES (20250101, 'content://test/7', 60000, 2, 17)")
            }
            close()
        }

        val migrations = arrayOf(
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
        )
        val db = helper.runMigrationsAndValidate(name, 8, true, *migrations)
        try {
            db.query("SELECT id, uri, title, artist, indexVersion, dateAdded FROM tracks").use { c ->
                assertEquals(1, c.count)
                c.moveToFirst()
                assertEquals(7L, c.getLong(0))
                assertEquals("content://test/7", c.getString(1))
                assertEquals("Legacy Song", c.getString(2))
                assertEquals("Legacy Artist", c.getString(3))
                assertEquals(0, c.getInt(4))
                assertEquals(0L, c.getLong(5))
            }
            assertEquals(if (startVersion >= 4) 1 else 0, countRows(db, "playlists"))
            assertEquals(if (startVersion >= 4) 1 else 0, countRows(db, "playlist_tracks"))
            assertEquals(if (startVersion >= 4) 1 else 0, countRows(db, "listening_history"))
            assertEquals(if (startVersion >= 4) 1 else 0, countRows(db, "primary_artist_overrides"))
            assertEquals(if (startVersion >= 5) 1 else 0, countRows(db, "favorites"))
            assertEquals(if (startVersion >= 6) 1 else 0, countRows(db, "hidden_tracks"))
            assertEquals(if (startVersion >= 6) 1 else 0, countRows(db, "excluded_folders"))
            assertEquals(if (startVersion >= 6) 1 else 0, countRows(db, "library_folders"))
            assertEquals(if (startVersion >= 7) 1 else 0, countRows(db, "listening_days"))
            if (startVersion >= 4) {
                db.query("SELECT position, addedAt FROM playlist_tracks WHERE playlistId = 1").use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals(0, c.getInt(0))
                    assertEquals(11L, c.getLong(1))
                }
                db.query("SELECT trackUri, playedAt FROM listening_history").use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals("content://test/7", c.getString(0))
                    assertEquals(12L, c.getLong(1))
                }
                db.query("SELECT name, coverFile FROM playlists WHERE id = 1").use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals("Legacy Mix", c.getString(0))
                    assertEquals(true, c.isNull(1))
                }
                db.query("SELECT primaryArtist FROM primary_artist_overrides WHERE trackUri = 'content://test/7'").use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals("Override Artist", c.getString(0))
                }
            }
            if (startVersion >= 5) {
                db.query("SELECT addedAt FROM favorites WHERE trackUri = 'content://test/7'").use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals(13L, c.getLong(0))
                }
            }
            if (startVersion >= 6) {
                db.query("SELECT hiddenAt, displayName, sizeBytes FROM hidden_tracks WHERE trackUri = 'hidden'").use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals(14L, c.getLong(0))
                    assertEquals("hidden.mp3", c.getString(1))
                    assertEquals(200L, c.getLong(2))
                }
                db.query("SELECT prefix, excludedAt FROM excluded_folders").use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals("external_primary:Podcasts/", c.getString(0))
                    assertEquals(15L, c.getLong(1))
                }
                db.query("SELECT treeUri, folderKey, label, addedAt FROM library_folders").use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals("tree", c.getString(0))
                    assertEquals("folder", c.getString(1))
                    assertEquals("Folder", c.getString(2))
                    assertEquals(16L, c.getLong(3))
                }
            }
            if (startVersion >= 7) {
                db.query("SELECT `day`, trackUri, listenedMs, plays, lastListenedAt FROM listening_days").use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals(20250101, c.getInt(0))
                    assertEquals("content://test/7", c.getString(1))
                    assertEquals(60_000L, c.getLong(2))
                    assertEquals(2, c.getInt(3))
                    assertEquals(17L, c.getLong(4))
                }
            }
        } finally {
            db.close()
        }
    }

    private fun countRows(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM `$table`").use { c -> c.moveToFirst(); c.getInt(0) }
}
