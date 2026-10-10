package com.localplayer.music.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class AudioSnifferTest {
    private fun fixture(name: String): File =
        File(requireNotNull(javaClass.getResource("/fixtures/$name")) { "missing fixture $name" }.toURI())

    @Test fun mp3() = assertEquals(AudioContainer.MP3, AudioSniffer.sniff(fixture("t.mp3"), "t.mp3"))
    @Test fun flac() = assertEquals(AudioContainer.FLAC, AudioSniffer.sniff(fixture("t.flac"), "t.flac"))
    @Test fun m4a() = assertEquals(AudioContainer.MP4, AudioSniffer.sniff(fixture("t.m4a"), "t.m4a"))
    @Test fun rawAacIsNotM4a() = assertEquals(AudioContainer.AAC_ADTS, AudioSniffer.sniff(fixture("t.aac"), "t.aac"))
    @Test fun mp4ContainerNamedAacIsDetectedAsMp4() =
        assertEquals(AudioContainer.MP4, AudioSniffer.sniff(fixture("t.m4a"), "renamed.aac"))
    @Test fun capabilities() {
        assertEquals(MetadataField.entries.toSet(), WriteCapability.forContainer(AudioContainer.MP3).writableFields)
        assertEquals(emptySet<MetadataField>(), WriteCapability.forContainer(AudioContainer.AAC_ADTS).writableFields)
    }
}
