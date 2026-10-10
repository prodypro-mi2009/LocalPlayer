package com.localplayer.music.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

class AudioPayloadTest {
    @Before fun setUp() = FileTagEngine.configure(android = false)

    private fun copyOf(name: String): File {
        val source = File(requireNotNull(javaClass.getResource("/fixtures/$name")).toURI())
        val dir = Files.createTempDirectory("payload").toFile().apply { deleteOnExit() }
        return File(dir, name).also { source.copyTo(it); it.deleteOnExit() }
    }

    private val cases = listOf(
        "t.mp3" to AudioContainer.MP3,
        "t.flac" to AudioContainer.FLAC,
        "t.m4a" to AudioContainer.MP4,
    )

    @Test fun fingerprintExistsForEverySupportedWritableFormat() {
        for ((name, container) in cases) assertNotNull(name, AudioPayload.sha256(copyOf(name), container))
    }

    @Test fun tagEditsNeverChangeTheAudioFingerprint() {
        for ((name, container) in cases) {
            val file = copyOf(name)
            val before = AudioPayload.sha256(file, container)
            FileTagEngine.write(
                file,
                container,
                mapOf(
                    MetadataField.TITLE to "A much longer title than before, to force the tag area to grow",
                    MetadataField.COMMENT to "x".repeat(4000),
                ),
            )
            assertEquals(name, before, AudioPayload.sha256(file, container))
        }
    }

    @Test fun aChangedAudioByteIsDetected() {
        for ((name, container) in cases) {
            val file = copyOf(name)
            val before = AudioPayload.sha256(file, container)
            RandomAccessFile(file, "rw").use { raf ->
                // Offset 20000 lies inside the audio data of all three small fixtures.
                raf.seek(20000)
                val b = raf.read()
                raf.seek(20000)
                raf.write(b xor 0x55)
            }
            assertNotEquals(name, before, AudioPayload.sha256(file, container))
        }
    }

    @Test fun unsupportedContainerGivesNull() {
        assertEquals(null, AudioPayload.sha256(copyOf("t.aac"), AudioContainer.AAC_ADTS))
    }
}
