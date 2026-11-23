package com.localplayer.music.data.metadata

import org.junit.Assume.assumeTrue
import java.io.File

/**
 * Test-only helper. On Windows, saving tags into an M4A file fails inside the tag library: the file is still held
 * open by the save attempt itself when the library tries to rename the original ("unable to rename the original file
 * to t.old"). Copies of the same file, and mp3/flac files, rename fine, so it is a Windows file-locking rule and not
 * a problem of the app: the app runs on Android (Linux rules), where renaming an open file is allowed.
 *
 * So on Windows only, a write that fails exactly that way is reported as SKIPPED (not as passed, not as failed).
 * On Linux/macOS, and for every other kind of error, the test fails as before. The app's real code is not touched.
 */
object TestTagWrite {
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    fun write(file: File, container: AudioContainer, changes: MetadataChanges, artwork: ArtworkChange? = null) {
        try {
            FileTagEngine.write(file, container, changes, artwork)
        } catch (e: MetadataException) {
            val renameLock = e.kind == FailureKind.WRITER_ERROR && e.detail.contains("rename", ignoreCase = true)
            assumeTrue(
                "Skipped on Windows: the tag library cannot rename an M4A file it still holds open (${e.detail})",
                !(windows && renameLock),
            )
            throw e
        }
    }
}
