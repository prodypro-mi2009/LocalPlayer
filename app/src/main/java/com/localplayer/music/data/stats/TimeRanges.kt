package com.localplayer.music.data.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Periods shown in Time Usage and Top 20. */
enum class StatsPeriod { TODAY, WEEK, MONTH, YEAR, PREVIOUS_MONTH }

/** An inclusive range of LOCAL calendar days; [fromKey]/[toKey] are yyyymmdd numbers as stored in the database. */
data class DayRange(val start: LocalDate, val end: LocalDate) {
    val fromKey: Int get() = TimeRanges.dayKey(start)
    val toKey: Int get() = TimeRanges.dayKey(end)
}

/** Pure calendar rules (device-local dates). The week starts on the locale's first day of week. */
object TimeRanges {
    fun dayKey(date: LocalDate): Int = date.year * 10_000 + date.monthValue * 100 + date.dayOfMonth

    fun range(period: StatsPeriod, today: LocalDate, firstDayOfWeek: DayOfWeek): DayRange = when (period) {
        StatsPeriod.TODAY -> DayRange(today, today)
        StatsPeriod.WEEK -> {
            val start = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            DayRange(start, start.plusDays(6))
        }
        StatsPeriod.MONTH -> DayRange(today.withDayOfMonth(1), today.with(TemporalAdjusters.lastDayOfMonth()))
        StatsPeriod.YEAR -> DayRange(today.withDayOfYear(1), today.with(TemporalAdjusters.lastDayOfYear()))
        StatsPeriod.PREVIOUS_MONTH -> {
            val first = today.withDayOfMonth(1).minusMonths(1)
            DayRange(first, first.with(TemporalAdjusters.lastDayOfMonth()))
        }
    }

    /** "1 h 05 min" style pieces: whole hours and the remaining minutes of [ms]. */
    fun hoursMinutes(ms: Long): Pair<Long, Int> {
        val totalMinutes = (ms.coerceAtLeast(0L)) / 60_000L
        return totalMinutes / 60 to (totalMinutes % 60).toInt()
    }
}
