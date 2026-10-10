package com.localplayer.music.data.library

import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** Audio-only MediaStore helpers shared by both scanners. There is no video support at all. */
object MediaStoreAudio {
    /** Exactly the supported library formats. .mp4 is NOT imported, even if it only contains audio. */
    val SUPPORTED_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac")

    /** All shared/external volumes (Android 10+), the plain external collection before that. */
    fun collection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    fun isSupportedName(displayName: String): Boolean =
        displayName.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS

    /** 0/1 columns of items that are not music (ringtones, alarms, notification sounds, voice recordings). */
    fun nonMusicFlagColumns(): List<String> = buildList {
        add(MediaStore.Audio.AudioColumns.IS_RINGTONE)
        add(MediaStore.Audio.AudioColumns.IS_ALARM)
        add(MediaStore.Audio.AudioColumns.IS_NOTIFICATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(MediaStore.Audio.AudioColumns.IS_RECORDING)
    }

    /** MediaStore writes "<unknown>" (and sometimes blanks) for missing text tags. */
    fun cleanText(value: String?): String? {
        val text = value?.trim()
        return if (text.isNullOrEmpty() || text == MediaStore.UNKNOWN_STRING) null else text
    }

    /** Columns that tell which folder an item is in: RELATIVE_PATH + VOLUME_NAME (Android 10+), else DATA. */
    @Suppress("DEPRECATION")
    fun folderColumns(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.VOLUME_NAME)
        } else {
            listOf(MediaStore.MediaColumns.DATA)
        }

    /** [first] / [second] are the cursor indexes of [folderColumns]. Null when the folder cannot be determined. */
    fun folderKey(cursor: Cursor, first: Int, second: Int): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            FolderKeys.fromRelativePath(cursor.getString(second), cursor.getString(first))
        } else {
            FolderKeys.fromDataPath(cursor.getString(first))
        }
    } catch (e: Exception) {
        null
    }
}
