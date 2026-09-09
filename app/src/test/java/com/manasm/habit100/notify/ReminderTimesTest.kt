package com.manasm.habit100.notify

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ReminderTimesTest {
    private val ny = ZoneId.of("America/New_York")
    private val day = LocalDate.of(2026, 3, 10)

    private fun at(hour: Int, minute: Int = 0) =
        day.atTime(hour, minute).atZone(ny).toInstant()

    @Test fun grace_trigger_is_today_at_9am_when_it_is_still_morning() {
        assertEquals(
            day.atTime(9, 0).atZone(ny).toInstant(),
            nextTrigger(ReminderKind.GRACE, ny, at(7, 30)),
        )
    }

    @Test fun grace_trigger_rolls_to_tomorrow_after_9am() {
        assertEquals(
            day.plusDays(1).atTime(9, 0).atZone(ny).toInstant(),
            nextTrigger(ReminderKind.GRACE, ny, at(9, 30)),
        )
    }

    @Test fun grace_trigger_at_exactly_9am_is_tomorrow_strictly_after_now() {
        assertEquals(
            day.plusDays(1).atTime(9, 0).atZone(ny).toInstant(),
            nextTrigger(ReminderKind.GRACE, ny, at(9, 0)),
        )
    }

    @Test fun evening_trigger_is_today_at_1930_before_then() {
        assertEquals(
            day.atTime(19, 30).atZone(ny).toInstant(),
            nextTrigger(ReminderKind.EVENING, ny, at(12, 0)),
        )
    }

    @Test fun evening_trigger_rolls_to_tomorrow_after_1930() {
        assertEquals(
            day.plusDays(1).atTime(19, 30).atZone(ny).toInstant(),
            nextTrigger(ReminderKind.EVENING, ny, at(21, 0)),
        )
    }

    @Test fun trigger_is_computed_in_the_habit_zone() {
        // 12:00 UTC on Mar 10 2026 is 08:00 EDT in New York -> next 9am NY is that same day
        val utcNoon = day.atTime(12, 0).atZone(ZoneId.of("UTC")).toInstant()
        assertEquals(day.atTime(9, 0).atZone(ny).toInstant(), nextTrigger(ReminderKind.GRACE, ny, utcNoon))
    }

    @Test fun grace_and_evening_use_9am_and_1930() {
        assertEquals(LocalTime.of(9, 0), GRACE_TIME)
        assertEquals(LocalTime.of(19, 30), EVENING_TIME)
    }
}
