package com.localplayer.music.data.metadata

/**
 * Free space a safe tag edit needs. During an edit these coexist: the private backup, the working
 * copy, the verification copy read back out of the real file, and the rewritten original itself.
 */
object StorageEstimate {
    /** Tag growth (new artwork or long lyrics), filesystem slack and temp files of the tag library. */
    const val HEADROOM_BYTES = 48L * 1024 * 1024

    /** Needed BEFORE the edit starts: backup + working + verification copy + the rewrite of the original. */
    fun requiredBeforeEdit(fileSize: Long): Long = 4L * fileSize.coerceAtLeast(0) + HEADROOM_BYTES

    /** Needed once the backup already exists (it is already stored): working + verification + rewrite. */
    fun requiredAfterBackup(fileSize: Long): Long = 3L * fileSize.coerceAtLeast(0) + HEADROOM_BYTES

    /** Needed on the volume that holds the original while it is rewritten in place. */
    fun requiredOnMediaVolume(fileSize: Long): Long = fileSize.coerceAtLeast(0) + HEADROOM_BYTES / 3
}
