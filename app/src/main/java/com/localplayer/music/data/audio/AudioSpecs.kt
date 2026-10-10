package com.localplayer.music.data.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Real technical values of an audio file. Any value that cannot be determined is null (never invented). */
data class AudioSpecs(val sampleRateHz: Int?, val bitrateKbps: Int?, val format: String?)

object AudioSpecFormatter {
    /** "48.0 kHz • 128 kbps • MP3", leaving out whatever is unknown. Null when nothing is known. */
    fun format(specs: AudioSpecs): String? {
        val parts = listOfNotNull(
            specs.sampleRateHz?.takeIf { it > 0 }?.let { "%.1f kHz".format(java.util.Locale.ROOT, it / 1000.0) },
            specs.bitrateKbps?.takeIf { it > 0 }?.let { "$it kbps" },
            specs.format?.takeIf { it.isNotBlank() },
        )
        return if (parts.isEmpty()) null else parts.joinToString(" • ")
    }

    /** Readable codec name from the decoder MIME type, falling back to the file extension. */
    fun formatName(mime: String?, extension: String): String? = when (mime?.lowercase()) {
        "audio/mpeg" -> "MP3"
        "audio/flac" -> "FLAC"
        "audio/mp4a-latm", "audio/aac" -> "AAC"
        "audio/alac" -> "ALAC"
        "audio/vorbis" -> "Vorbis"
        "audio/opus" -> "Opus"
        else -> extension.takeIf { it.isNotBlank() }?.uppercase()
    }

    /** Average bitrate from file size and duration (used when the container does not state one). */
    fun averageBitrateKbps(sizeBytes: Long, durationMs: Long): Int? =
        if (sizeBytes > 0 && durationMs > 0) ((sizeBytes * 8L) / durationMs).toInt() else null // bits per ms = kbps
}

/** Reads the specs through MediaExtractor (no copy of the file), cached per file version. */
class AudioSpecReader(private val context: Context) {
    private val cache = LruCache<String, AudioSpecs>(64)

    suspend fun read(uri: String, displayName: String, sizeBytes: Long, durationMs: Long, lastModified: Long): AudioSpecs {
        val key = "$uri|$lastModified"
        cache.get(key)?.let { return it }
        val specs = withContext(Dispatchers.IO) { extract(uri, displayName, sizeBytes, durationMs) }
        cache.put(key, specs)
        return specs
    }

    private fun extract(uri: String, displayName: String, sizeBytes: Long, durationMs: Long): AudioSpecs {
        val extension = displayName.substringAfterLast('.', "")
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    format = candidate
                    break
                }
            }
            val stated = format?.takeIf { it.containsKey(MediaFormat.KEY_BIT_RATE) }?.getInteger(MediaFormat.KEY_BIT_RATE)
            AudioSpecs(
                sampleRateHz = format?.takeIf { it.containsKey(MediaFormat.KEY_SAMPLE_RATE) }
                    ?.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                bitrateKbps = stated?.takeIf { it > 0 }?.let { it / 1000 }
                    ?: AudioSpecFormatter.averageBitrateKbps(sizeBytes, durationMs),
                format = AudioSpecFormatter.formatName(format?.getString(MediaFormat.KEY_MIME), extension),
            )
        } catch (e: Exception) {
            AudioSpecs(null, AudioSpecFormatter.averageBitrateKbps(sizeBytes, durationMs), AudioSpecFormatter.formatName(null, extension))
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {
                // Nothing useful to do.
            }
        }
    }
}
