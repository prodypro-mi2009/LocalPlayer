package com.localplayer.music.ui.screens

import com.localplayer.music.ui.components.FadingLazyColumn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.localplayer.music.ui.components.SettingsCustomRow
import com.localplayer.music.ui.components.SettingsGroup
import com.localplayer.music.ui.components.SettingsLinkRow
import com.localplayer.music.ui.components.SettingsNote
import com.localplayer.music.ui.components.SettingsScaffold
import com.localplayer.music.ui.components.SettingsTopBar
import androidx.compose.foundation.layout.size
import com.localplayer.music.ui.components.ArtworkImage
import com.localplayer.music.ui.components.SettingsChipRow
import com.localplayer.music.ui.components.artworkRef
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
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
private fun rememberLocale(): Locale = LocalLocale.current.platformLocale

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

/** Listening time: period chips, one big total, two small stat cards, and a bar chart of the last 7 days. */
@Composable
fun TimeUsageScreen(nav: NavState, viewModel: LibraryViewModel) {
    val locale = rememberLocale()
    val today = remember { LocalDate.now() }
    val first = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val periods = listOf(StatsPeriod.TODAY, StatsPeriod.WEEK, StatsPeriod.MONTH, StatsPeriod.YEAR, StatsPeriod.PREVIOUS_MONTH)
    var selected by rememberSaveable { mutableIntStateOf(1) }
    val period = periods[selected.coerceIn(0, periods.lastIndex)]
    val range = remember(period, today, first) { TimeRanges.range(period, today, first) }
    val ms by viewModel.listenedMs(range).collectAsStateWithLifecycle(initialValue = 0L)
    val plays by viewModel.playsIn(range).collectAsStateWithLifecycle(initialValue = 0)
    // Days that have already happened inside the range (a month in progress is divided by the days so far).
    val elapsedDays = remember(range, today) {
        val end = if (range.end.isAfter(today)) today else range.end
        (java.time.temporal.ChronoUnit.DAYS.between(range.start, end) + 1).coerceAtLeast(1L)
    }
    val last7 = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
    val dayMs = last7.map { day -> viewModel.listenedMs(DayRange(day, day)).collectAsStateWithLifecycle(initialValue = 0L).value }

    SettingsScaffold(title = stringResource(R.string.stats_title), onBack = { nav.pop() }) {
        SettingsChipRow(periods.map { stringResource(periodLabel(it)) }, selected, { selected = it })

        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text(
                    stringResource(R.string.stats_total_time),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    durationText(ms),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    rangeText(period, range, locale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(stringResource(R.string.stats_songs_played), plays.toString(), Modifier.weight(1f))
            StatCard(stringResource(R.string.stats_daily_average), durationText(ms / elapsedDays), Modifier.weight(1f))
        }

        SettingsGroup(title = stringResource(R.string.stats_last7)) {
            item {
                SettingsCustomRow {
                    val maxMs = (dayMs.maxOrNull() ?: 0L).coerceAtLeast(1L)
                    val barArea = 120.dp
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        last7.forEachIndexed { i, day ->
                            val value = dayMs[i]
                            val isToday = i == last7.lastIndex
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (value > 0L) (value / 60_000L).toString() else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                Box(Modifier.height(barArea), contentAlignment = Alignment.BottomCenter) {
                                    Box(
                                        Modifier.fillMaxWidth().height((barArea * (value.toFloat() / maxMs)).coerceAtLeast(4.dp))
                                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                            .background(
                                                if (isToday) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.primary.copy(alpha = if (value > 0L) 0.45f else 0.15f),
                                            ),
                                    )
                                }
                                Text(
                                    day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        SettingsGroup(note = stringResource(R.string.stats_note)) {
            item { SettingsLinkRow(stringResource(R.string.stats_top20_open), onClick = { nav.push(Screen.Top20) }) }
        }
        if (ms == 0L && plays == 0) {
            SettingsNote(stringResource(R.string.stats_empty))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** The 20 most played songs of today / this week / this month / this year, as connected cards. */
@Composable
fun Top20Screen(nav: NavState, viewModel: LibraryViewModel) {
    val locale = rememberLocale()
    val today = remember { LocalDate.now() }
    val first = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val options = listOf(StatsPeriod.TODAY, StatsPeriod.WEEK, StatsPeriod.MONTH, StatsPeriod.YEAR)
    var selected by rememberSaveable { mutableIntStateOf(2) }
    val period = options[selected.coerceIn(0, options.lastIndex)]
    val range = remember(period, today, first) { TimeRanges.range(period, today, first) }

    Column(Modifier.fillMaxSize()) {
        SettingsTopBar(title = stringResource(R.string.stats_top20_title), onBack = { nav.pop() })
        SettingsChipRow(
            options.map { stringResource(periodLabel(it)) }, selected, { selected = it },
            Modifier.padding(horizontal = 16.dp),
        )
        Text(
            rangeText(period, range, locale),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
        // A new period starts a NEW list: its own scroll state (so it opens at the top) and no rows of the previous
        // period (a LazyColumn would otherwise keep the old scroll position and follow the first song it still knows).
        key(range) {
            val rows by viewModel.top20(range).collectAsStateWithLifecycle(initialValue = null)
        val list = rows
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(stringResource(R.string.stats_top20_empty))
            else -> FadingLazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp + LocalBottomBarInset.current),
            ) {
                itemsIndexed(list, key = { _, r -> r.track.id }) { index, row ->
                    val top = if (index == 0) 24.dp else 5.dp
                    val bottom = if (index == list.lastIndex) 24.dp else 5.dp
                    Surface(
                        shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 68.dp)
                                .clickable(role = Role.Button) { viewModel.playTrack(row.track.id) }
                                .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Rank: the first three are accented.
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = if (index < 3) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(34.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "${index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (index < 3) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            ArtworkImage(artwork = row.track.artworkRef(), size = 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    row.track.title,
                                    style = MaterialTheme.typography.bodyLarge.songTitleFont(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    row.track.primaryArtist ?: row.track.artist ?: stringResource(R.string.unknown_artist),
                                    style = MaterialTheme.typography.bodyMedium.artistFont(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                pluralStringResource(R.plurals.stats_plays_count, row.plays, row.plays),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        }
    }
}
