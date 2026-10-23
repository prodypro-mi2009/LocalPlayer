package com.localplayer.music.data.effects

import android.content.Context
import android.net.Uri
import android.util.LruCache
import com.localplayer.music.data.metadata.AudioSniffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads ReplayGain tags of ONE track when it is about to play (never a library scan) and keeps a small in-memory
 * cache. Only used while ReplayGain is switched on. The file is opened read-only through the content resolver; the
 * file itself is never modified and nothing is analysed or decoded.
 */
class ReplayGainRepository(private val context: Context) {
    private val cache = LruCache<Long, ReplayGainInfo>(96)

    fun peek(trackId: Long): ReplayGainInfo? = cache.get(trackId)

    fun invalidate(trackId: Long) { cache.remove(trackId) }

    fun clear() = cache.evictAll()

    suspend fun info(trackId: Long, uri: String): ReplayGainInfo = cache.get(trackId) ?: withContext(Dispatchers.IO) {
        val result = try {
            context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { pfd ->
                val file = File("/proc/self/fd/${pfd.fd}")
                val container = AudioSniffer.sniff(file, Uri.parse(uri).lastPathSegment.orEmpty())
                if (!container.tagWritable) ReplayGainInfo() else ReplayGainReader.read(file, container.workingExtension)
            } ?: ReplayGainInfo()
        } catch (e: Exception) {
            ReplayGainInfo()
        }
        cache.put(trackId, result)
        result
    }
}
