package com.localplayer.music.data.effects

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.localplayer.music.data.metadata.MetadataValidation
import java.io.File
import java.nio.ByteOrder

/**
 * Measures a song's loudness by decoding it with Android's own decoder (nothing is written, the file is only read).
 * Blocking and CPU heavy (a few seconds per song): callers run it on a background dispatcher and only when the user
 * asked for it. [active] is polled so a cancelled job stops decoding.
 */
object LoudnessAnalyzer {
    fun analyze(context: Context, uri: Uri, active: () -> Boolean = { true }): Loudness? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            decode(extractor, active)
        } catch (e: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    fun analyze(file: File, active: () -> Boolean = { true }): Loudness? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            decode(extractor, active)
        } catch (e: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    /** "-6.50" for a measured loudness (what the ReplayGain tag editor expects), null when it cannot be expressed. */
    fun gainText(lufs: Double?): String? = lufs?.let { MetadataValidation.canonicalGain(Ebur128.gainDb(it).toString()) }

    /** "0.987654" for a measured sample peak (linear, 1.0 = full scale), null for silence. */
    fun peakText(peak: Float): String? = MetadataValidation.canonicalPeak(peak.toString())

    private fun decode(extractor: MediaExtractor, active: () -> Boolean): Loudness? {
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return null
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var floatPcm = false
        var meter: Ebur128? = null
        var scratch = FloatArray(0)
        val codec = MediaCodec.createDecoderByType(mime)
        try {
            // Ask for float samples: 16-bit output clips at full scale, which would hide how far a peak goes past 1.0.
            // A decoder that cannot do it keeps 16-bit; the output format reported below says which one we got.
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!active()) return null
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val size = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val out = codec.outputFormat
                    channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    rate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    floatPcm = out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        out.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (o >= 0) {
                    if (info.size > 0) {
                        val buffer = codec.getOutputBuffer(o)!!
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.order(ByteOrder.nativeOrder())
                        val m = meter ?: Ebur128(rate, channels).also { meter = it }
                        val total = if (floatPcm) info.size / 4 else info.size / 2
                        val frames = total / channels
                        if (scratch.size < frames * channels) scratch = FloatArray(frames * channels)
                        if (floatPcm) {
                            buffer.asFloatBuffer().get(scratch, 0, frames * channels)
                        } else {
                            val shorts = buffer.asShortBuffer()
                            for (n in 0 until frames * channels) scratch[n] = shorts.get() / 32768f
                        }
                        m.add(scratch, frames)
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
        return meter?.finish()?.let { Loudness(it.blocks, it.peak, floatPcm) }
    }
}
