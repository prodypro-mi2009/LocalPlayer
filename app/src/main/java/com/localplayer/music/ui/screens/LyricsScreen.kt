package com.localplayer.music.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.music.R
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.LibraryViewModel.LyricsLoad
import com.localplayer.music.ui.components.BlurredBackdrop
import com.localplayer.music.ui.components.DetailTopBar
import com.localplayer.music.ui.components.EmptyState
import com.localplayer.music.ui.components.FollowButtonHint
import com.localplayer.music.ui.components.PlainLyrics
import com.localplayer.music.ui.components.SyncedLyricsList
import com.localplayer.music.ui.components.rememberActiveLyricIndex
import com.localplayer.music.ui.components.rememberBlurredArtwork
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.ui.theme.ArtworkGradient
import com.localplayer.music.ui.theme.ForceStatusBarIcons
import com.localplayer.music.ui.theme.lyricsVerticalBrush

/**
 * Lyrics of the song that is playing now (it follows song changes). Plain lyrics scroll normally;
 * LRC lyrics are synchronized with the real playback position.
 *
 * Background: the SAME artwork tone as Now Playing ([tone], or the theme colour when artwork colours are off),
 * as a smooth vertical tint that stays visible over the WHOLE height and continues behind the floating bottom bars
 * (no black or grey block at the bottom), plus the blurred artwork as a faint texture and a light uniform darkening
 * that keeps the white text readable on bright covers. The page is always dark, so its text is white and the status
 * bar icons are forced light. If the blurred artwork cannot be made, the tint alone is used.
 */
@Composable
fun LyricsScreen(nav: NavState, viewModel: LibraryViewModel, tone: Color?) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val now = state.nowPlaying
    val blurred = rememberBlurredArtwork(now?.artwork)
    val tint by animateColorAsState(
        targetValue = tone ?: MaterialTheme.colorScheme.primaryContainer,
        animationSpec = tween(durationMillis = 500),
        label = "lyricsTint",
    )
    ForceStatusBarIcons(lightIcons = true)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BlurredBackdrop(blurred)
        Box(Modifier.fillMaxSize().background(lyricsVerticalBrush(tint)))
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = ArtworkGradient.LYRICS_SCRIM)))
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(Modifier.fillMaxSize()) {
                DetailTopBar(title = now?.title ?: stringResource(R.string.lyrics), onBack = { nav.pop() })
                if (now == null) {
                    EmptyState(stringResource(R.string.queue_empty))
                } else {
                    // Everything below belongs to ONE song; a new song rebuilds it from scratch.
                    key(now.mediaId) { LyricsBody(viewModel, now.mediaId.toLongOrNull(), state.isPlaying) }
                }
            }
        }
    }
}

@Composable
private fun LyricsBody(viewModel: LibraryViewModel, trackId: Long?, isPlaying: Boolean) {
    val load by produceState<LyricsLoad?>(initialValue = null, trackId) {
        value = trackId?.let { viewModel.loadLyrics(it) } ?: LyricsLoad.None
    }
    when (val result = load) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        LyricsLoad.None -> EmptyState(stringResource(R.string.no_lyrics))
        LyricsLoad.Failed -> EmptyState(stringResource(R.string.lyrics_failed))
        is LyricsLoad.Loaded -> {
            val lyrics = result.lyrics
            if (!lyrics.isSynced) {
                PlainLyrics(lyrics.plainText)
            } else {
                var follow by remember { mutableStateOf(true) }
                val active = rememberActiveLyricIndex(viewModel, lyrics.lines, isPlaying)
                Box(Modifier.fillMaxSize()) {
                    SyncedLyricsList(
                        lines = lyrics.lines,
                        activeIndex = active,
                        follow = follow,
                        onUserScroll = { follow = false },
                        onSeek = {
                            viewModel.seekTo(it)
                            follow = true
                        },
                    )
                    FollowButtonHint(visible = !follow, onClick = { follow = true })
                }
            }
        }
    }
}
