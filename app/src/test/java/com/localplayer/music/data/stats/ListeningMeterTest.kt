package com.localplayer.music.data.stats

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningMeterTest {
    private class Rec(val uri: String, val ms: Long, val plays: Int)
    private val log = ArrayList<Rec>()
    private val meter = ListeningMeter { uri, ms, plays -> log.add(Rec(uri, ms, plays)) }
    private fun totalMs(uri: String? = null) = log.filter { uri == null || it.uri == uri }.sumOf { it.ms }
    private fun plays(uri: String? = null) = log.filter { uri == null || it.uri == uri }.sumOf { it.plays }

    @Test fun continuousPlayCountsRealTimeAndOnePlayAfterThirtySeconds() {
        meter.startItem("a", 180_000, 0, playing = true)
        meter.tick(10_000); meter.tick(20_000); meter.tick(29_000)
        assertEquals(0, plays())
        meter.tick(40_000)
        meter.release(45_000)
        assertEquals(45_000L, totalMs())
        assertEquals(1, plays())
    }

    @Test fun fastSkipAddsTimeButNoPlay() {
        meter.startItem("a", 180_000, 0, playing = true)
        meter.startItem("b", 180_000, 8_000, playing = true)   // skipped after 8 s
        meter.release(10_000)
        assertEquals(8_000L, totalMs("a")); assertEquals(0, plays("a"))
        assertEquals(2_000L, totalMs("b")); assertEquals(0, plays("b"))
    }

    @Test fun pauseStopsTheClockAndResumeContinues() {
        meter.startItem("a", 180_000, 0, playing = true)
        meter.setPlaying(false, 20_000)
        meter.tick(500_000)                                    // long pause: nothing accumulates
        meter.setPlaying(true, 600_000)
        meter.release(615_000)
        assertEquals(35_000L, totalMs())
        assertEquals(1, plays())
    }

    @Test fun repeatedCallbacksNeverCountTwice() {
        meter.startItem("a", 180_000, 0, playing = true)
        repeat(5) { meter.setPlaying(true, 10_000) }           // duplicate "playing" events
        meter.tick(10_000); meter.tick(10_000)
        meter.setPlaying(false, 20_000); meter.setPlaying(false, 25_000)
        assertEquals(20_000L, totalMs())
        meter.release(30_000)
        assertEquals(20_000L, totalMs())
    }

    @Test fun notPlayingAtStartDoesNotCount() {
        meter.startItem("a", 180_000, 0, playing = false)
        meter.tick(60_000)
        meter.release(90_000)
        assertEquals(0, log.size)
    }

    @Test fun sameSongRepeatedIsACountPerInstanceButOnlyOneEach() {
        meter.startItem("a", 20_000, 0, playing = true)
        meter.startItem("a", 20_000, 20_000, playing = true)   // repeat-one restart
        meter.release(40_000)
        assertEquals(2, plays("a"))
        assertEquals(40_000L, totalMs("a"))
    }

    @Test fun criterionIsThirtySecondsOrHalfOfShortSongs() {
        assertEquals(30_000L, PlayCriterion.thresholdMs(300_000))
        assertEquals(30_000L, PlayCriterion.thresholdMs(60_000))
        assertEquals(25_000L, PlayCriterion.thresholdMs(50_000))
        assertEquals(5_000L, PlayCriterion.thresholdMs(10_000))
        assertEquals(3_000L, PlayCriterion.thresholdMs(4_000))
        assertEquals(2_000L, PlayCriterion.thresholdMs(2_000))
        assertEquals(30_000L, PlayCriterion.thresholdMs(0))
        meter.startItem("s", 10_000, 0, playing = true)
        meter.release(5_000)
        assertEquals(1, plays())
    }

    @Test fun clockStallIsCapped() {
        meter.startItem("a", 600_000, 0, playing = true)
        meter.release(10_000_000)
        assertTrue(totalMs() <= 120_000L)
    }

    @Test fun durationLearnedLaterIsUsed() {
        meter.startItem("a", 0, 0, playing = true)
        meter.setDuration(10_000)
        meter.release(5_000)
        assertEquals(1, plays())
    }
}

class TimeRangesTest {
    private val monday = DayOfWeek.MONDAY

    @Test fun periodsUseLocalCalendarDays() {
        val d = LocalDate.of(2026, 10, 11)                       // a Sunday
        assertEquals(20261011, TimeRanges.range(StatsPeriod.TODAY, d, monday).fromKey)
        val w = TimeRanges.range(StatsPeriod.WEEK, d, monday)
        assertEquals(LocalDate.of(2026, 10, 5), w.start); assertEquals(LocalDate.of(2026, 10, 11), w.end)
        val wSun = TimeRanges.range(StatsPeriod.WEEK, d, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 10, 11), wSun.start); assertEquals(LocalDate.of(2026, 10, 17), wSun.end)
        val m = TimeRanges.range(StatsPeriod.MONTH, d, monday)
        assertEquals(20261001, m.fromKey); assertEquals(20261031, m.toKey)
        val y = TimeRanges.range(StatsPeriod.YEAR, d, monday)
        assertEquals(20260101, y.fromKey); assertEquals(20261231, y.toKey)
        val p = TimeRanges.range(StatsPeriod.PREVIOUS_MONTH, d, monday)
        assertEquals(20260901, p.fromKey); assertEquals(20260930, p.toKey)
    }

    @Test fun previousMonthAcrossYearAndLeapFebruary() {
        val jan = TimeRanges.range(StatsPeriod.PREVIOUS_MONTH, LocalDate.of(2027, 1, 15), monday)
        assertEquals(20261201, jan.fromKey); assertEquals(20261231, jan.toKey)
        val mar = TimeRanges.range(StatsPeriod.PREVIOUS_MONTH, LocalDate.of(2028, 3, 1), monday)
        assertEquals(20280201, mar.fromKey); assertEquals(20280229, mar.toKey)
    }

    @Test fun weekCanStartInThePreviousMonth() {
        val w = TimeRanges.range(StatsPeriod.WEEK, LocalDate.of(2026, 10, 1), monday)   // Thursday
        assertEquals(20260928, w.fromKey); assertEquals(20261004, w.toKey)
    }

    @Test fun hoursMinutes() {
        assertEquals(1L to 5, TimeRanges.hoursMinutes(3_900_000))
        assertEquals(0L to 0, TimeRanges.hoursMinutes(59_999))
        assertEquals(0L to 0, TimeRanges.hoursMinutes(-5))
    }
}
