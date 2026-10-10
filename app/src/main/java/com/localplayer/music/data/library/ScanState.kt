package com.localplayer.music.data.library

enum class ScanFailureKind {
    /** MediaStore could not be queried; nothing was changed in the index. */
    QUERY_FAILED,

    /** Something unexpected went wrong while reading or writing the index. */
    UNEXPECTED,
}

/** Why the last scan did not complete. The previously indexed library is kept as it was. */
data class ScanError(val kind: ScanFailureKind, val detail: String? = null)

data class ScanState(
    val isScanning: Boolean = false,
    /** Supported media files MediaStore reports on the device. */
    val discovered: Int = 0,
    /** New or changed files whose tags have to be read in this pass. */
    val toProcess: Int = 0,
    val processed: Int = 0,
    /** Files that could not be read in this pass (their existing index rows are kept). */
    val unreadable: Int = 0,
    /** Non-null when the last pass failed. Cleared when a new pass begins (retry). */
    val error: ScanError? = null,
)
