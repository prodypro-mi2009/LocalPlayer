package com.localplayer.music.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.localplayer.music.data.library.TextNormalizer

/**
 * Full-text search index (SQLite FTS4) over the cached tag values.
 * It is a derived index: rebuilt from the file metadata, never a source of truth.
 * Text is stored accent-folded and lower-cased ([TextNormalizer.fold]), so Greek search works
 * without touching the real metadata.
 */
class SearchIndex(private val database: AppDatabase) {

    data class Row(
        val trackId: Long,
        val title: String?,
        val artist: String?,
        val album: String?,
        val genre: String?,
        val year: Int?,
        val primaryArtist: String?,
    )

    fun upsert(rows: List<Row>) {
        if (rows.isEmpty()) return
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            val statement = db.compileStatement(
                "INSERT OR REPLACE INTO track_search(docid, title, artist, album, genre, year, primaryname) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
            )
            for (row in rows) {
                statement.clearBindings()
                statement.bindLong(1, row.trackId)
                statement.bindString(2, TextNormalizer.fold(row.title))
                statement.bindString(3, TextNormalizer.fold(row.artist))
                statement.bindString(4, TextNormalizer.fold(row.album))
                statement.bindString(5, TextNormalizer.fold(row.genre))
                statement.bindString(6, row.year?.toString().orEmpty())
                statement.bindString(7, TextNormalizer.fold(row.primaryArtist))
                statement.executeInsert()
            }
            statement.close()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun delete(trackIds: List<Long>) {
        if (trackIds.isEmpty()) return
        val db = database.openHelper.writableDatabase
        for (chunk in trackIds.chunked(400)) {
            val placeholders = chunk.joinToString(",") { "?" }
            db.execSQL("DELETE FROM track_search WHERE docid IN ($placeholders)", chunk.toTypedArray())
        }
    }

    companion object {
        const val CREATE_SQL =
            "CREATE VIRTUAL TABLE IF NOT EXISTS track_search USING fts4(" +
                "title, artist, album, genre, year, primaryname, tokenize=simple)"

        fun ensureTable(db: SupportSQLiteDatabase) = db.execSQL(CREATE_SQL)
    }
}
