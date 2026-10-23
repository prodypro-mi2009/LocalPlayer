package com.localplayer.music.data.effects

import com.localplayer.music.data.metadata.FileTagEngine
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v24Frame
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class ReplayGainReaderTest {
    @Before fun setUp() = FileTagEngine.configure(false)

    private fun fixture(name: String): File {
        val src = File(javaClass.classLoader!!.getResource("fixtures/$name")!!.toURI())
        return File.createTempFile("rgtest", name.substringAfterLast('.').let { ".$it" }).also { src.copyTo(it, overwrite = true); it.deleteOnExit() }
    }

    @Test fun untaggedFiles_giveEmptyInfo() {
        for (n in listOf("t.mp3", "t.flac", "t.m4a")) {
            val f = fixture(n)
            assertTrue(n, ReplayGainReader.read(f, n.substringAfterLast('.')).isEmpty)
        }
    }

    @Test fun flac_vorbisComments() {
        val f = fixture("t.flac")
        val af = AudioFileIO.read(f)
        val tag = (af.tagOrCreateAndSetDefault as org.jaudiotagger.tag.flac.FlacTag).vorbisCommentTag
        tag.setField(tag.createField("REPLAYGAIN_TRACK_GAIN", "-7.25 dB"))
        tag.setField(tag.createField("REPLAYGAIN_TRACK_PEAK", "0.977"))
        tag.setField(tag.createField("replaygain_album_gain", "-5.10 dB"))
        af.commit()
        val info = ReplayGainReader.read(f, "flac")
        assertEquals(-7.25f, info.trackGainDb!!, 0.001f)
        assertEquals(0.977f, info.trackPeak!!, 0.001f)
        assertEquals(-5.10f, info.albumGainDb!!, 0.001f)
        assertNull(info.albumPeak)
        // the file name does not matter, only the given extension
        val renamed = File(f.parentFile, "no-extension-file").also { f.copyTo(it, overwrite = true); it.deleteOnExit() }
        assertEquals(-7.25f, ReplayGainReader.read(renamed, "flac").trackGainDb!!, 0.001f)
    }

    @Test fun mp3_txxxFrames() {
        val f = fixture("t.mp3")
        val af = AudioFileIO.read(f)
        val tag = af.tagOrCreateAndSetDefault as AbstractID3v2Tag
        fun txxx(desc: String, value: String) {
            val frame = ID3v24Frame("TXXX"); frame.body = FrameBodyTXXX(0, desc, value); tag.addField(frame)
        }
        txxx("REPLAYGAIN_TRACK_GAIN", "-4.50 dB"); txxx("REPLAYGAIN_TRACK_PEAK", "1.020000")
        txxx("replaygain_album_gain", "-2.00 dB"); txxx("REPLAYGAIN_ALBUM_PEAK", "0.9")
        af.commit()
        val info = ReplayGainReader.read(f, "mp3")
        assertEquals(-4.5f, info.trackGainDb!!, 0.001f)
        assertEquals(1.02f, info.trackPeak!!, 0.001f)
        assertEquals(-2.0f, info.albumGainDb!!, 0.001f)
        assertEquals(0.9f, info.albumPeak!!, 0.001f)
    }

    @Test fun m4a_freeformAtoms() {
        // rg.m4a = t.m4a with three iTunes free-form atoms (replaygain_track_gain/peak, album_gain) added to ilst
        val f = fixture("rg.m4a")
        val info = ReplayGainReader.read(f, "m4a")
        assertEquals(-3.3f, info.trackGainDb!!, 0.001f)
        assertEquals(0.95f, info.trackPeak!!, 0.001f)
        assertEquals(-1.5f, info.albumGainDb!!, 0.001f)
        assertNull(info.albumPeak)
    }

    @Test fun malformedValues_areIgnored() {
        val f = fixture("t.flac")
        val af = AudioFileIO.read(f)
        val tag = (af.tagOrCreateAndSetDefault as org.jaudiotagger.tag.flac.FlacTag).vorbisCommentTag
        tag.setField(tag.createField("REPLAYGAIN_TRACK_GAIN", "loud"))
        tag.setField(tag.createField("REPLAYGAIN_TRACK_PEAK", "-1"))
        af.commit()
        assertTrue(ReplayGainReader.read(f, "flac").isEmpty)
    }

    @Test fun unreadableFile_isEmptyNotCrash() {
        val junk = File.createTempFile("junk", ".mp3").also { it.writeBytes(ByteArray(100)); it.deleteOnExit() }
        assertTrue(ReplayGainReader.read(junk, "mp3").isEmpty)
        assertTrue(ReplayGainReader.read(File("/nonexistent/x.flac"), "flac").isEmpty)
    }
}
