package com.manasm.habit100.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HabitRulesTest {
    private val z = ZoneId.of("America/New_York")
    private val start = LocalDate.of(2026, 1, 1)

    /** now = the moment described by [dayNumber] at 12:00 local. */
    private fun nowForDay(dayNumber: Int): Instant =
        dateForDay(start, dayNumber).atTime(12, 0).atZone(z).toInstant()

    private fun input(trackLength: Int = 100, vararg done: Int) = RuleInput(
        startDate = start, zoneId = z, trackLength = trackLength,
        dayLogs = done.map { DayLog(it, DayStatus.DONE) },
    )

    @Test fun fresh_habit_day1() {
        val s = HabitRules.evaluate(input(), nowForDay(1))
        assertEquals(1, s.currentDayNumber)
        assertEquals(0, s.doneCount)
        assertEquals(0, s.missCount)
        assertEquals(10, s.missesLeft)
        assertEquals(HabitState.FORMING, s.state)
        assertEquals(true, s.canMarkToday)
        assertEquals(false, s.todayMarkedDone)
        assertNull(s.failureReason)
    }

    @Test fun marked_today_blocks_second_mark() {
        val s = HabitRules.evaluate(input(done = intArrayOf(1)), nowForDay(1))
        assertEquals(1, s.doneCount)
        assertEquals(true, s.todayMarkedDone)
        assertEquals(false, s.canMarkToday)
    }

    @Test fun elapsed_unmarked_past_days_count_as_misses() {
        // day 5 today; days 1 and 3 done; days 2 and 4 are elapsed unmarked -> misses
        val s = HabitRules.evaluate(input(done = intArrayOf(1, 3)), nowForDay(5))
        assertEquals(2, s.doneCount)
        assertEquals(2, s.missCount)
        assertEquals(8, s.missesLeft)
        assertEquals(HabitState.FORMING, s.state)
    }

    @Test fun current_day_not_yet_a_miss() {
        // day 3 today, only day 1 done. day 2 = miss, day 3 = pending (not miss)
        val s = HabitRules.evaluate(input(done = intArrayOf(1)), nowForDay(3))
        assertEquals(1, s.missCount)
    }

    @Test fun two_consecutive_misses_fail_immediately_even_under_budget() {
        // days 1 done, 2 & 3 missed (elapsed), today = day 4
        val s = HabitRules.evaluate(input(done = intArrayOf(1)), nowForDay(4))
        assertEquals(HabitState.FAILED, s.state)
        assertEquals(FailureReason.TWO_IN_A_ROW, s.failureReason)
        assertEquals(3, s.failedOnDay)
        assertEquals(2, s.missCount) // stops counting at failure day
    }

    @Test fun never_twice_beats_budget_priority() {
        // misses on days 2,4,6,8 (non-consecutive, done between), then 9 & 10 consecutive.
        // total would be 6 but two-in-a-row fires first at day 10.
        val done = intArrayOf(1, 3, 5, 7)
        val s = HabitRules.evaluate(input(done = done), nowForDay(11))
        assertEquals(FailureReason.TWO_IN_A_ROW, s.failureReason)
        assertEquals(10, s.failedOnDay)
    }

    @Test fun eleventh_miss_fails_on_budget() {
        // done on every odd day 1..21 -> misses on 2,4,...,22 = 11 misses, none consecutive
        val done = (1..21 step 2).toList().toIntArray()
        val s = HabitRules.evaluate(input(done = done), nowForDay(23))
        assertEquals(HabitState.FAILED, s.state)
        assertEquals(FailureReason.BUDGET_EXCEEDED, s.failureReason)
        assertEquals(22, s.failedOnDay)
    }

    @Test fun exactly_ten_misses_is_not_failure() {
        val done = (1..19 step 2).toList().toIntArray() // misses on 2..20 = 10
        val s = HabitRules.evaluate(input(done = done), nowForDay(21))
        assertEquals(HabitState.FORMING, s.state)
        assertEquals(10, s.missCount)
        assertEquals(0, s.missesLeft)
    }
}
