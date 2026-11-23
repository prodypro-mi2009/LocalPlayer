package com.localplayer.music.ui.screens

import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.text.style.TextAlign
import com.localplayer.music.ui.components.AppDropdownMenu
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.AppMenuItem
import com.localplayer.music.ui.components.TopBarMetrics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
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
import com.localplayer.music.ui.nav.Screen
import com.localplayer.music.ui.theme.ArtworkGradient
import com.localplayer.music.ui.theme.ForceStatusBarIcons
import com.localplayer.music.ui.theme.LocalPureMode
import com.localplayer.music.ui.theme.Motion
import com.localplayer.music.ui.theme.fallbackTint
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
fun LyricsScreen(
    nav: NavState,
    viewModel: LibraryViewModel,
    tone: Color?,
    blurMode: Boolean = false,
    blurredCover: androidx.compose.ui.graphics.ImageBitmap? = null,
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val now = state.nowPlaying
    // With the "Blurred cover" choice the page uses the shared blurred cover; otherwise the old faint texture (as before).
    val blurred = rememberBlurredArtwork(if (blurMode) null else now?.artwork)
    val tint = animateColorAsState(
        targetValue = tone ?: fallbackTint(),
        animationSpec = Motion.AmbientTint,
        label = "lyricsTint",
    )
    val pure = LocalPureMode.current.isOn
    // The lyrics are read from the audio FILE; they reload after a save (editor or Internet search).
    val revision by viewModel.lyricsRevision.collectAsStateWithLifecycle()
    val trackId = now?.mediaId?.toLongOrNull()
    val load by produceState<LyricsLoad?>(initialValue = null, trackId, revision) {
        value = null
        value = trackId?.let { viewModel.loadLyrics(it) } ?: LyricsLoad.None
    }
    // Pure black / white: the page is the plain theme background with theme text (no artwork, no tint, no scrim).
    if (!pure) ForceStatusBarIcons(lightIcons = true)

    Box(Modifier.fillMaxSize().background(if (pure) MaterialTheme.colorScheme.background else Color.Black)) {
        if (!pure && blurMode) {
            // The same blurred cover as Now Playing, under a black veil (the lyrics text is always white).
            com.localplayer.music.ui.components.BlurredCoverBackground(
                image = blurredCover,
                veil = ArtworkGradient.LYRICS_SCRIM + 0.06f,
                veilColor = Color.Black,
            )
        } else if (!pure) {
            BlurredBackdrop(blurred)
            Box(Modifier.fillMaxSize().drawBehind { drawRect(lyricsVerticalBrush(tint.value)) })
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = ArtworkGradient.LYRICS_SCRIM)))
        }
        CompositionLocalProvider(LocalContentColor provides (if (pure) MaterialTheme.colorScheme.onBackground else Color.White)) {
            Column(Modifier.fillMaxSize()) {
                DetailTopBar(
                    title = now?.title ?: stringResource(R.string.lyrics),
                    onBack = { nav.pop() },
                    actions = {
                        if (trackId != null && load != null) {
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                                ) {
                                    IconButton(onClick = { menu = true }, modifier = Modifier.size(TopBarMetrics.ButtonSize)) {
                                        Icon(AppIcons.MoreVert, contentDescription = stringResource(R.string.more_actions_cd))
                                    }
                                }
                                AppDropdownMenu(expanded = menu, onDismiss = { menu = false }) {
                                    // The search never starts by itself, only from here (also when the file already has lyrics).
                                    AppMenuItem(AppIcons.Search, stringResource(R.string.internet_search)) {
                                        menu = false
                                        nav.push(Screen.LyricsSearch(trackId))
                                    }
                                    AppMenuItem(AppIcons.Edit, stringResource(R.string.edit_lyrics)) {
                                        menu = false
                                        nav.push(Screen.LyricsEditor(trackId))
                                    }
                                }
                            }
                        }
                    },
                )
                if (now == null) {
                    EmptyState(stringResource(R.string.queue_empty))
                } else {
                    // Everything below belongs to ONE song; a new song rebuilds it from scratch.
                    key(now.mediaId) {
                        LyricsBody(
                            viewModel, load, state.isPlaying,
                            onSearch = { trackId?.let { nav.push(Screen.LyricsSearch(it)) } },
                            onEdit = { trackId?.let { nav.push(Screen.LyricsEditor(it)) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsBody(viewModel: LibraryViewModel, load: LyricsLoad?, isPlaying: Boolean, onSearch: () -> Unit, onEdit: () -> Unit) {
    val alignment by viewModel.settings.lyricsAlignment.collectAsStateWithLifecycle()
    val textAlign = alignment.toTextAlign()
    when (val result = load) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        LyricsLoad.None -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    stringResource(R.string.no_lyrics),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                // The Greek labels are long ("Επεξεργασία στίχων"): there the two buttons sit one under the other, full width,
                // instead of squeezing the second one into three lines. Every other language keeps the row.
                val greek = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language == "el"
                val searchButton: @Composable (Modifier) -> Unit = { modifier ->
                    Button(onClick = onSearch, modifier = modifier, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                        Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.internet_search))
                    }
                }
                val editButton: @Composable (Modifier) -> Unit = { modifier ->
                    FilledTonalButton(onClick = onEdit, modifier = modifier, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                        Icon(AppIcons.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.edit_lyrics))
                    }
                }
                if (greek) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        searchButton(Modifier.fillMaxWidth())
                        editButton(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        searchButton(Modifier)
                        editButton(Modifier)
                    }
                }
            }
        }
        LyricsLoad.Failed -> EmptyState(stringResource(R.string.lyrics_failed))
        is LyricsLoad.Loaded -> {
            val lyrics = result.lyrics
            if (!lyrics.isSynced) {
                PlainLyrics(lyrics.plainText, textAlign = textAlign)
            } else {
                var follow by remember { mutableStateOf(true) }
                val fill by viewModel.settings.lyricsFill.collectAsStateWithLifecycle()
                val active = rememberActiveLyricIndex(viewModel, lyrics.lines, isPlaying)
                Box(Modifier.fillMaxSize()) {
                    SyncedLyricsList(
                        lines = lyrics.lines,
                        activeIndex = active,
                        follow = follow,
                        textAlign = textAlign,
                        fill = fill,
                        isPlaying = isPlaying,
                        positionMs = viewModel::currentPositionMs,
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
