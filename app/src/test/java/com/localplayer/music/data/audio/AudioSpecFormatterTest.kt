package com.localplayer.music.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioSpecFormatterTest {
    @Test fun mp3() = assertEquals("48.0 kHz • 128 kbps • MP3", AudioSpecFormatter.format(AudioSpecs(48000, 128, "MP3")))
    @Test fun flac() = assertEquals("44.1 kHz • 1411 kbps • FLAC", AudioSpecFormatter.format(AudioSpecs(44100, 1411, "FLAC")))
    @Test fun unknownValuesAreLeftOutNotInvented() {
        assertEquals("44.1 kHz • MP3", AudioSpecFormatter.format(AudioSpecs(44100, null, "MP3")))
        assertEquals("FLAC", AudioSpecFormatter.format(AudioSpecs(null, null, "FLAC")))
    }
    @Test fun nothingKnown() = assertNull(AudioSpecFormatter.format(AudioSpecs(null, null, null)))
    @Test fun formatNames() {
        assertEquals("MP3", AudioSpecFormatter.formatName("audio/mpeg", "mp3"))
        assertEquals("AAC", AudioSpecFormatter.formatName("audio/mp4a-latm", "m4a"))
        assertEquals("M4A", AudioSpecFormatter.formatName(null, "m4a"))
    }
    @Test fun averageBitrate() {
        assertEquals(128, AudioSpecFormatter.averageBitrateKbps(1_920_000, 120_000)) // 128 kbps for 2 minutes
        assertNull(AudioSpecFormatter.averageBitrateKbps(0, 1000))
    }
}
