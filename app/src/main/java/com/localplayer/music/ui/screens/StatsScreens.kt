package com.localplayer.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.music.R
import com.localplayer.music.data.stats.DayRange
import com.localplayer.music.data.stats.StatsPeriod
import com.localplayer.music.data.stats.TimeRanges
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.DetailTopBar
import com.localplayer.music.ui.components.EmptyState
import com.localplayer.music.ui.components.LocalBottomBarInset
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.ui.nav.Screen
import com.localplayer.music.ui.theme.artistFont
import com.localplayer.music.ui.theme.songTitleFont
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.time.temporal.WeekFields

@Composable
private fun rememberLocale(): Locale = LocalContext.current.resources.configuration.locales[0] ?: Locale.getDefault()

private fun periodLabel(p: StatsPeriod): Int = when (p) {
    StatsPeriod.TODAY -> R.string.stats_today
    StatsPeriod.WEEK -> R.string.stats_week
    StatsPeriod.MONTH -> R.string.stats_month
    StatsPeriod.YEAR -> R.string.stats_year
    StatsPeriod.PREVIOUS_MONTH -> R.string.stats_prev_month
}

/** "5 Oct 2026 – 11 Oct 2026" (a single day for Today): the exact calendar days a number covers. */
private fun rangeText(period: StatsPeriod, range: DayRange, locale: Locale): String {
    val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    return if (period == StatsPeriod.TODAY) f.format(range.start) else "${f.format(range.start)} – ${f.format(range.end)}"
}

@Composable
private fun durationText(ms: Long): String {
    val (h, m) = TimeRanges.hoursMinutes(ms)
    return if (h == 0L) stringResource(R.string.stats_minutes_only, m) else stringResource(R.string.stats_hours_minutes, h, m)
}

/** Listening time for today, this week, this month, this year and the previous month, with the days each covers. */
@Composable
fun TimeUsageScreen(nav: NavState, viewModel: LibraryViewModel) {
    val locale = rememberLocale()
    val today = remember { LocalDate.now() }
    val first = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val periods = listOf(StatsPeriod.TODAY, StatsPeriod.WEEK, StatsPeriod.MONTH, StatsPeriod.YEAR, StatsPeriod.PREVIOUS_MONTH)
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = stringResource(R.string.stats_title), onBack = { nav.pop() })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.stats_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var anyData = false
            val totals = periods.map { period ->
                val range = remember(period, today, first) { TimeRanges.range(period, today, first) }
                val ms by viewModel.listenedMs(range).collectAsStateWithLifecycle(initialValue = 0L)
                val plays by viewModel.playsIn(range).collectAsStateWithLifecycle(initialValue = 0)
                if (ms > 0L || plays > 0) anyData = true
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(periodLabel(period)), style = MaterialTheme.typography.titleMedium)
                            Text(
                                rangeText(period, range, locale),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(durationText(ms), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.stats_plays_count, plays),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (!anyData) {
                Text(
                    stringResource(R.string.stats_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { nav.push(Screen.Top20) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.stats_top20_open))
            }
            Spacer(Modifier.height(16.dp + LocalBottomBarInset.current))
        }
    }
}

/** The 20 most played songs of today / this week / this month / this year. */
@Composable
fun Top20Screen(nav: NavState, viewModel: LibraryViewModel) {
    val locale = rememberLocale()
    val today = remember { LocalDate.now() }
    val first = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val options = listOf(StatsPeriod.TODAY, StatsPeriod.WEEK, StatsPeriod.MONTH, StatsPeriod.YEAR)
    var selected by rememberSaveable { mutableIntStateOf(2) }
    val period = options[selected.coerceIn(0, options.lastIndex)]
    val range = remember(period, today, first) { TimeRanges.range(period, today, first) }
    val rows by viewModel.top20(range).collectAsStateWithLifecycle(initialValue = null)

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = stringResource(R.string.stats_top20_title), onBack = { nav.pop() })
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEachIndexed { index, p ->
                FilterChip(
                    selected = index == selected,
                    onClick = { selected = index },
                    label = { Text(stringResource(periodLabel(p))) },
                )
            }
        }
        Text(
            rangeText(period, range, locale),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val list = rows
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(stringResource(R.string.stats_top20_empty))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp + LocalBottomBarInset.current)) {
                itemsIndexed(list, key = { _, r -> r.track.id }) { index, row ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .clickable(role = Role.Button) { viewModel.playTrack(row.track.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(40.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.track.title,
                                style = MaterialTheme.typography.bodyLarge.songTitleFont(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                row.track.primaryArtist ?: row.track.artist ?: stringResource(R.string.unknown_artist),
                                style = MaterialTheme.typography.bodySmall.artistFont(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            stringResource(R.string.stats_plays_count, row.plays),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
