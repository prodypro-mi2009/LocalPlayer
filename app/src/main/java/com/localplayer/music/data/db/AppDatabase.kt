package com.localplayer.music.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        HistoryEntity::class,
        PrimaryArtistOverrideEntity::class,
        FavoriteEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        /**
         * 3 -> 4: keeps every cached track row (and its id), adds the derived library columns,
         * the playlist / history / classification tables and their indexes. Existing rows get
         * indexVersion = 0, so the next scan re-reads each file once and fills the new columns.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrationStatements.forEach { db.execSQL(it) }
            }
        }

        /** 4 -> 5: favourites (app data). Nothing else changes; every existing row is kept. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorites` (`trackUri` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`trackUri`))",
                )
            }
        }

        /** Exposed so the migration test can show exactly what runs. */
        val migrationStatements: List<String> = listOf(
            "ALTER TABLE `tracks` ADD COLUMN `primaryArtist` TEXT",
            "ALTER TABLE `tracks` ADD COLUMN `primaryArtistKey` TEXT",
            "ALTER TABLE `tracks` ADD COLUMN `autoPrimaryArtist` TEXT",
            "ALTER TABLE `tracks` ADD COLUMN `albumKey` TEXT",
            "ALTER TABLE `tracks` ADD COLUMN `albumGroupKey` TEXT",
            "ALTER TABLE `tracks` ADD COLUMN `genreKey` TEXT",
            "ALTER TABLE `tracks` ADD COLUMN `titleSort` TEXT",
            "ALTER TABLE `tracks` ADD COLUMN `artistSort` TEXT",
            "ALTER TABLE `tracks` ADD COLUMN `albumSort` TEXT",
            "ALTER TABLE `tracks` ADD COLUMN `dateAdded` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `tracks` ADD COLUMN `indexVersion` INTEGER NOT NULL DEFAULT 0",
            "CREATE INDEX IF NOT EXISTS `index_tracks_primaryArtistKey` ON `tracks` (`primaryArtistKey`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_albumGroupKey` ON `tracks` (`albumGroupKey`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_genreKey` ON `tracks` (`genreKey`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_year` ON `tracks` (`year`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_titleSort` ON `tracks` (`titleSort`)",
            "CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `playlist_tracks` (`playlistId` INTEGER NOT NULL, `trackUri` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `trackUri`), " +
                "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE INDEX IF NOT EXISTS `index_playlist_tracks_playlistId_position` ON `playlist_tracks` (`playlistId`, `position`)",
            "CREATE INDEX IF NOT EXISTS `index_playlist_tracks_trackUri` ON `playlist_tracks` (`trackUri`)",
            "CREATE TABLE IF NOT EXISTS `listening_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`trackUri` TEXT NOT NULL, `playedAt` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_listening_history_playedAt` ON `listening_history` (`playedAt`)",
            "CREATE INDEX IF NOT EXISTS `index_listening_history_trackUri` ON `listening_history` (`trackUri`)",
            "CREATE TABLE IF NOT EXISTS `primary_artist_overrides` (`trackUri` TEXT NOT NULL, " +
                "`primaryArtist` TEXT NOT NULL, PRIMARY KEY(`trackUri`))",
        )

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "library.db")
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                // Versions 1-2 (early folder-picker builds) held only a rebuildable index.
                // Version 3 -> 4 is a real migration; there is no destructive path from 3.
                .fallbackToDestructiveMigrationFrom(true, 1, 2)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        SearchIndex.ensureTable(db)
                    }
                })
                .build()
    }
}
