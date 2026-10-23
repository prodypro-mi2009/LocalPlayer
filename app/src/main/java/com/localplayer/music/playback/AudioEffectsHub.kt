package com.localplayer.music.playback

import android.media.audiofx.Equalizer
import android.os.Handler
import android.os.Looper
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.localplayer.music.AppContainer
import com.localplayer.music.data.effects.EqModel
import com.localplayer.music.data.effects.ReplayGainMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** State of the equaliser as the UI should describe it. */
sealed interface EqStatus {
    /** Switched off: no effect object exists. */
    data object Off : EqStatus
    /** Running on the player's audio session; [centersHz] are the REAL bands of the device effect. */
    data class Active(val centersHz: List<Int>) : EqStatus
    /** Switched on but the device/effect could not be created (playback is unaffected). */
    data object Unavailable : EqStatus
}

/**
 * Applies ReplayGain and the equaliser to the app's ONE player. Everything is optional and off by default:
 *  - nothing is created while both features are off (no listener, no effect, no coroutine scope);
 *  - the effects are attached to the player's own audio session only (they cannot touch other apps);
 *  - ReplayGain cuts with the player's volume and boosts (when a tag asks for more than 0 dB and the peak allows it)
 *    with a LoudnessEnhancer; tags are read for the current and the next track only.
 * Call [refresh] after any related setting changed. All calls happen on the main thread.
 */
object AudioEffectsHub {
    private var container: AppContainer? = null
    private var player: ExoPlayer? = null
    private var listener: Player.Listener? = null
    private var scope: CoroutineScope? = null
    private var rgJob: Job? = null
    private var equalizer: Equalizer? = null
    private var enhancer: LoudnessEnhancer? = null
    private var effectSession = C.AUDIO_SESSION_ID_UNSET
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _eqStatus = MutableStateFlow<EqStatus>(EqStatus.Off)
    val eqStatus: StateFlow<EqStatus> = _eqStatus.asStateFlow()

    fun setPlayer(c: AppContainer?, p: ExoPlayer?) {
        if (p == null) {
            teardown()
            player = null
            return
        }
        container = c ?: container
        player = p
        refresh()
    }

    /** Brings everything in line with the current settings. Cheap when both features are off. */
    fun refresh() {
        val c = container ?: return
        val p = player ?: return
        val rg = c.settings.replayGainEnabled.value
        val eq = c.settings.equalizerEnabled.value
        if (!rg && !eq) {
            teardown()
            return
        }
        ensureListener(p)
        if (eq) applyEqualizer(p, c) else releaseEqualizer()
        if (rg) applyReplayGain(p, c) else clearReplayGain(p)
    }

    /** A track's tags changed (edited in the app): forget its cached gain and re-apply if it is playing. */
    fun onTrackChanged(trackId: Long) {
        val c = container ?: return
        c.replayGain.invalidate(trackId)
        // Called from the metadata save, which runs on Dispatchers.IO. The ExoPlayer may only be touched on its
        // application thread (main), otherwise Media3 throws "Player is accessed on the wrong thread".
        if (Looper.myLooper() == Looper.getMainLooper()) reapplyIfCurrent(trackId) else mainHandler.post { reapplyIfCurrent(trackId) }
    }

    private fun reapplyIfCurrent(trackId: Long) {
        val c = container ?: return
        val p = player ?: return
        if (c.settings.replayGainEnabled.value && p.currentMediaItem?.mediaId == trackId.toString()) applyReplayGain(p, c)
    }

    // ---- lifecycle --------------------------------------------------------------------------------------------

    private fun ensureListener(p: ExoPlayer) {
        if (listener != null) return
        val l = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val c = container ?: return
                if (c.settings.replayGainEnabled.value) applyReplayGain(p, c)
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                val c = container ?: return
                // A new audio session needs new effect objects (the old ones belong to the old session).
                releaseEqualizer(); releaseEnhancer()
                if (c.settings.equalizerEnabled.value) applyEqualizer(p, c)
                if (c.settings.replayGainEnabled.value) applyReplayGain(p, c)
            }
        }
        p.addListener(l)
        listener = l
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    private fun teardown() {
        listener?.let { player?.removeListener(it) }
        listener = null
        rgJob?.cancel(); rgJob = null
        scope?.cancel(); scope = null
        releaseEqualizer()
        releaseEnhancer()
        player?.let { resetVolume(it) }
        _eqStatus.value = EqStatus.Off
    }

    // ---- equaliser --------------------------------------------------------------------------------------------

    private fun applyEqualizer(p: ExoPlayer, c: AppContainer) {
        val session = p.audioSessionId
        if (session == C.AUDIO_SESSION_ID_UNSET) { _eqStatus.value = EqStatus.Unavailable; return }   // retried by onAudioSessionIdChanged
        try {
            var eq = equalizer
            if (eq == null || effectSession != session) {
                releaseEqualizer()
                eq = Equalizer(0, session)       // priority 0, attached to THIS player's session only
                equalizer = eq
                effectSession = session
            }
            val bands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val centers = IntArray(bands) { eq.getCenterFreq(it.toShort()) / 1000 }
            val gains = c.settings.eqPreset.value.gainsDb(c.settings.eqCustom.value)
            val levels = EqModel.mapToDevice(gains, centers, range[0].toInt(), range[1].toInt())
            for (i in 0 until bands) eq.setBandLevel(i.toShort(), levels[i].toShort())
            eq.setEnabled(true)
            _eqStatus.value = EqStatus.Active(centers.toList())
        } catch (e: Exception) {
            // Unsupported device / effect busy: playback continues unchanged.
            releaseEqualizer()
            _eqStatus.value = EqStatus.Unavailable
        }
    }

    private fun releaseEqualizer() {
        try { equalizer?.release() } catch (_: Exception) { }
        equalizer = null
        if (_eqStatus.value !is EqStatus.Unavailable) _eqStatus.value = EqStatus.Off
    }

    // ---- ReplayGain -------------------------------------------------------------------------------------------

    private fun applyReplayGain(p: ExoPlayer, c: AppContainer) {
        val item = p.currentMediaItem
        val trackId = item?.mediaId?.toLongOrNull()
        val uri = item?.let { MediaItemFactory.trackUri(it) }
        if (trackId == null || uri == null) { setGain(p, null); return }
        val repo = c.replayGain
        val cached = repo.peek(trackId)
        if (cached != null) {
            rgJob?.cancel(); rgJob = null
            setGain(p, gainFor(c, cached))
            prefetchNext(p, c)
        } else {
            rgJob?.cancel()
            // The tags of this track are not known yet: do not leave the previous track's volume / boost applied
            // while they load. Neutral (no gain) until the real value arrives.
            setGain(p, null)
            rgJob = scope?.launch {
                val info = repo.info(trackId, uri)
                if (player?.currentMediaItem?.mediaId == trackId.toString()) setGain(p, gainFor(c, info))
                prefetchNext(p, c)
            }
        }
    }

    private fun gainFor(c: AppContainer, info: com.localplayer.music.data.effects.ReplayGainInfo): Float? =
        ReplayGainMath.effectiveGainDb(info, c.settings.replayGainMode.value, c.settings.replayGainPreventClipping.value)

    private fun prefetchNext(p: ExoPlayer, c: AppContainer) {
        val next = p.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: return
        val item = p.getMediaItemAt(next)
        val id = item.mediaId.toLongOrNull() ?: return
        val uri = MediaItemFactory.trackUri(item) ?: return
        if (c.replayGain.peek(id) != null) return
        scope?.launch { c.replayGain.info(id, uri) }
    }

    private fun setGain(p: ExoPlayer, gainDb: Float?) {
        val plan = ReplayGainMath.plan(gainDb)
        PlaybackVolume.base = plan.volume
        if (!SleepTimer.isFading) p.volume = plan.volume
        if (plan.boostMb > 0) {
            try {
                val session = p.audioSessionId
                if (session != C.AUDIO_SESSION_ID_UNSET) {
                    val e = enhancer ?: LoudnessEnhancer(session).also { enhancer = it }
                    e.setTargetGain(plan.boostMb)
                    e.setEnabled(true)
                }
            } catch (ex: Exception) {
                releaseEnhancer()     // boost not available on this device: the track simply plays at its normal level
            }
        } else {
            releaseEnhancer()
        }
    }

    private fun clearReplayGain(p: ExoPlayer) {
        rgJob?.cancel(); rgJob = null
        releaseEnhancer()
        resetVolume(p)
        container?.replayGain?.clear()
    }

    private fun releaseEnhancer() {
        try { enhancer?.release() } catch (_: Exception) { }
        enhancer = null
    }

    private fun resetVolume(p: ExoPlayer) {
        PlaybackVolume.base = 1f
        if (!SleepTimer.isFading) p.volume = 1f
    }
}
