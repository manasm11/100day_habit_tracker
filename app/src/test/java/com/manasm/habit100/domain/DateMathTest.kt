package com.manasm.habit100.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DateMathTest {
    private val ny = ZoneId.of("America/New_York")

    @Test fun day1_is_start_date() {
        val start = LocalDate.of(2026, 1, 10)
        val now = start.atStartOfDay(ny).toInstant().plusSeconds(3600)
        assertEquals(1, currentDayNumber(start, ny, now))
    }

    @Test fun day_advances_at_local_midnight() {
        val start = LocalDate.of(2026, 1, 10)
        val justBeforeMidnight = LocalDate.of(2026, 1, 10).atTime(23, 59).atZone(ny).toInstant()
        val justAfterMidnight = LocalDate.of(2026, 1, 11).atTime(0, 1).atZone(ny).toInstant()
        assertEquals(1, currentDayNumber(start, ny, justBeforeMidnight))
        assertEquals(2, currentDayNumber(start, ny, justAfterMidnight))
    }

    @Test fun uses_habit_zone_not_utc() {
        val start = LocalDate.of(2026, 1, 10)
        // 03:00 UTC on Jan 11 is still Jan 10 in New York -> day 1
        val instant = LocalDate.of(2026, 1, 11).atTime(3, 0).atZone(ZoneId.of("UTC")).toInstant()
        assertEquals(1, currentDayNumber(start, ny, instant))
    }

    @Test fun before_start_floors_to_day_1() {
        val start = LocalDate.of(2026, 1, 10)
        val now = LocalDate.of(2026, 1, 1).atStartOfDay(ny).toInstant()
        assertEquals(1, currentDayNumber(start, ny, now))
    }

    @Test fun far_future_day_number() {
        val start = LocalDate.of(2026, 1, 1)
        val now = LocalDate.of(2026, 4, 11).atStartOfDay(ny).toInstant() // 100 days later
        assertEquals(101, currentDayNumber(start, ny, now))
    }

    @Test fun date_for_day() {
        assertEquals(LocalDate.of(2026, 1, 1), dateForDay(LocalDate.of(2026, 1, 1), 1))
        assertEquals(LocalDate.of(2026, 4, 10), dateForDay(LocalDate.of(2026, 1, 1), 100))
    }

    @Test fun period_of_uses_zone() {
        val instant = LocalDate.of(2026, 10, 1).atTime(2, 0).atZone(ZoneId.of("UTC")).toInstant()
        // still Sept 30 in New York
        assertEquals("2026-09", periodOf(ny, instant))
    }
}
