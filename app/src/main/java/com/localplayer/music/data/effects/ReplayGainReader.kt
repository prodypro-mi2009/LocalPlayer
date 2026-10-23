package com.localplayer.music.data.effects

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagTextField
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import java.io.File

/**
 * Reads the ReplayGain tags that are already inside an audio file (read only, nothing is written or analysed):
 *  - MP3: ID3v2 TXXX frames  REPLAYGAIN_TRACK_GAIN / _PEAK / REPLAYGAIN_ALBUM_GAIN / _PEAK
 *  - FLAC: Vorbis comments with the same names
 *  - M4A: iTunes free-form atoms (----:com.apple.iTunes:replaygain_*), read by [Mp4Freeform] (the tag library hides them)
 * Missing, unreadable or malformed values simply stay null (the track then plays unchanged).
 * Needs FileTagEngine.configure() to have been called (as for all tag access in the app).
 */
object ReplayGainReader {
    private const val TG = "REPLAYGAIN_TRACK_GAIN"
    private const val TP = "REPLAYGAIN_TRACK_PEAK"
    private const val AG = "REPLAYGAIN_ALBUM_GAIN"
    private const val AP = "REPLAYGAIN_ALBUM_PEAK"

    /** [extension] ("mp3", "flac", "m4a") selects the reader, so the file name does not matter (e.g. /proc/self/fd/N). */
    fun read(file: File, extension: String): ReplayGainInfo = try {
        if (extension.equals("m4a", true) || extension.equals("mp4", true)) {
            java.io.RandomAccessFile(file, "r").use { fromMap(Mp4Freeform.read(it)) }
        } else {
            fromTag(AudioFileIO.readAs(file, extension).tag)
        }
    } catch (e: Exception) {
        ReplayGainInfo()
    }

    fun fromMap(found: Map<String, String>) = ReplayGainInfo(
        trackGainDb = ReplayGainMath.parseGainDb(found[TG]),
        trackPeak = ReplayGainMath.parsePeak(found[TP]),
        albumGainDb = ReplayGainMath.parseGainDb(found[AG]),
        albumPeak = ReplayGainMath.parsePeak(found[AP]),
    )

    fun fromTag(tag: Tag?): ReplayGainInfo {
        if (tag == null) return ReplayGainInfo()
        val found = HashMap<String, String>()
        val it = tag.fields
        while (it.hasNext()) {
            val f = it.next()
            val (name, value) = try {
                when {
                    f is AbstractID3v2Frame && f.identifier == "TXXX" -> {
                        val body = f.body as? FrameBodyTXXX ?: continue
                        body.description to body.userFriendlyValue.removePrefix(body.description).trimStart('"', ' ', ';', '=')
                    }
                    f is TagTextField -> f.id to f.content
                    else -> continue
                }
            } catch (e: Exception) {
                continue
            }
            val key = name.uppercase().substringAfterLast(':')
            if (key in setOf(TG, TP, AG, AP) && key !in found) found[key] = value
        }
        return fromMap(found)
    }
}
