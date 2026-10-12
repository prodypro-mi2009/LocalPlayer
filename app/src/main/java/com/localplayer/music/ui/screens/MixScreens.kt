package com.localplayer.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.music.R
import com.localplayer.music.data.mix.MixNotice
import com.localplayer.music.data.mix.MixStrategy
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.SongMenuController
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.ui.nav.Screen

internal fun strategyLabel(s: MixStrategy): Int = when (s) {
    MixStrategy.MOST_PLAYED -> R.string.mix_strategy_most_played
    MixStrategy.FORGOTTEN -> R.string.mix_strategy_forgotten
    MixStrategy.RANDOM -> R.string.mix_strategy_random
    MixStrategy.SMART -> R.string.mix_strategy_smart
}

internal fun noticeText(n: MixNotice): Int? = when (n) {
    MixNotice.NONE -> null
    MixNotice.NO_HISTORY -> R.string.mix_notice_no_history
    MixNotice.PARTIAL_HISTORY -> R.string.mix_notice_partial
    MixNotice.EMPTY_LIBRARY -> R.string.mix_notice_empty_library
    MixNotice.FEWER_THAN_REQUESTED -> R.string.mix_notice_fewer
}

/** The generated Your Mix: the strategy and why (when it had to fall back), refresh, settings, and the songs. */
@Composable
fun YourMixScreen(nav: NavState, viewModel: LibraryViewModel, menu: SongMenuController) {
    val mix by viewModel.mix.collectAsStateWithLifecycle()
    val settings by viewModel.settings.mixSettings.collectAsStateWithLifecycle()
    // Reloads when the screen opens or the Mix settings change; the repository decides whether to rebuild.
    LaunchedEffect(settings) { viewModel.loadMix() }

    TrackListDetail(
        title = stringResource(R.string.your_mix_card_title),
        nav = nav,
        viewModel = viewModel,
        menu = menu,
        tracks = if (mix.loading) null else mix.tracks,
        emptyText = stringResource(noticeText(mix.notice) ?: R.string.mix_notice_empty_library),
        heroSubtitle = stringResource(strategyLabel(settings.strategy)),
        header = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                noticeText(mix.notice)?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.loadMix(force = true) }) { Text(stringResource(R.string.mix_refresh)) }
                    OutlinedButton(onClick = { nav.push(Screen.SettingsYourMix) }) { Text(stringResource(R.string.settings)) }
                }
            }
        },
    )
}
