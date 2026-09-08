package com.manasm.habit100.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class MarkableDayTest {
    private val ny = ZoneId.of("America/New_York")
    private val start = LocalDate.of(2026, 1, 1)
    private val cutoff = LocalTime.of(10, 0)

    /** now = [dayNumber]'s calendar date at [time] local. */
    private fun at(dayNumber: Int, time: LocalTime) =
        dateForDay(start, dayNumber).atTime(time).atZone(ny).toInstant()

    @Test fun day_1_has_no_grace_day() {
        // 6am on day 1 — there is no "yesterday" to fall back to
        assertEquals(1, markableDay(start, ny, at(1, LocalTime.of(6, 0)), emptySet(), cutoff))
    }

    @Test fun before_cutoff_with_yesterday_unmarked_returns_yesterday() {
        // 8am on calendar day 5, day 4 not done -> you can still finish day 4
        assertEquals(4, markableDay(start, ny, at(5, LocalTime.of(8, 0)), setOf(1, 2, 3), cutoff))
    }

    @Test fun before_cutoff_with_yesterday_already_done_returns_today() {
        // day 4 done -> nothing pending, markable day is today (5)
        assertEquals(5, markableDay(start, ny, at(5, LocalTime.of(8, 0)), setOf(1, 2, 3, 4), cutoff))
    }

    @Test fun after_cutoff_returns_today_even_if_yesterday_unmarked() {
        // 11am on day 5 -> day 4 has locked as a miss, markable day is 5
        assertEquals(5, markableDay(start, ny, at(5, LocalTime.of(11, 0)), setOf(1, 2, 3), cutoff))
    }

    @Test fun at_cutoff_exactly_the_grace_day_has_locked() {
        // grace is strictly before the cutoff
        assertEquals(5, markableDay(start, ny, at(5, LocalTime.of(10, 0)), setOf(1, 2, 3), cutoff))
    }

    @Test fun grace_window_is_measured_in_the_habit_zone() {
        // 09:00 UTC on day 5 == 04:00 in New York -> within grace, day 4 unmarked
        val instant = dateForDay(start, 5).atTime(9, 0).atZone(ZoneId.of("UTC")).toInstant()
        assertEquals(4, markableDay(start, ny, instant, setOf(1, 2, 3), cutoff))
    }

    @Test fun before_start_floors_to_1() {
        val earlier = LocalDate.of(2025, 12, 20).atStartOfDay(ny).toInstant()
        assertEquals(1, markableDay(start, ny, earlier, emptySet(), cutoff))
    }
}
