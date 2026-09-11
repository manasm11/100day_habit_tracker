package com.manasm.habit100.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * §15 — a quit habit can name the slip out loud on the day it happens, instead of waiting
 * for the next morning's rollover to derive it from an unmarked day.
 */
class ExplicitSlipTest {
    private val z = ZoneId.of("America/New_York")
    private val start = LocalDate.of(2026, 1, 1)

    private fun nowForDay(dayNumber: Int): Instant =
        dateForDay(start, dayNumber).atTime(12, 0).atZone(z).toInstant()

    private fun input(done: List<Int> = emptyList(), slipped: List<Int> = emptyList()) = RuleInput(
        startDate = start, zoneId = z, trackLength = 100,
        dayLogs = done.map { DayLog(it, DayStatus.DONE) } +
            slipped.map { DayLog(it, DayStatus.MISSED) },
    )

    @Test fun an_unmarked_current_day_stays_pending() {
        // Regression guard: without an explicit slip row, today is still not a miss.
        val s = HabitRules.evaluate(input(done = listOf(1, 2)), nowForDay(3))
        assertEquals(0, s.missCount)
        assertFalse(s.todaySlipped)
        assertNull(s.undoSlipDayNumber)
        assertTrue(s.canMarkToday)
    }

    @Test fun an_explicit_slip_counts_as_a_miss_the_moment_it_is_logged() {
        val s = HabitRules.evaluate(input(done = listOf(1, 2), slipped = listOf(3)), nowForDay(3))
        assertEquals(2, s.doneCount)
        assertEquals(1, s.missCount)
        assertEquals(9, s.missesLeft)
        assertEquals(HabitState.FORMING, s.state)
        assertTrue(s.todaySlipped)
        assertFalse("a slipped day is not markable until the slip is undone", s.canMarkToday)
    }

    @Test fun a_live_slip_can_be_undone_on_the_day_it_was_logged() {
        val s = HabitRules.evaluate(input(done = listOf(1, 2), slipped = listOf(3)), nowForDay(3))
        assertEquals(3, s.undoSlipDayNumber)
    }

    @Test fun an_explicit_slip_after_a_missed_day_ends_the_attempt_at_once() {
        // Day 2 elapsed unmarked (a miss); slipping on day 3 is the second in a row.
        val s = HabitRules.evaluate(input(done = listOf(1), slipped = listOf(3)), nowForDay(3))
        assertEquals(HabitState.FAILED, s.state)
        assertEquals(FailureReason.TWO_IN_A_ROW, s.failureReason)
        assertEquals(3, s.failedOnDay)
    }

    @Test fun a_fatal_slip_offers_no_undo() {
        // The confirmation dialog warns before this one — once the attempt is over, it is over.
        val s = HabitRules.evaluate(input(done = listOf(1), slipped = listOf(3)), nowForDay(3))
        assertNull(s.undoSlipDayNumber)
        assertFalse(s.canUndoMark)
    }

    @Test fun explicit_slips_spend_the_budget_and_the_eleventh_ends_the_attempt() {
        // Slips on every other day: 1,3,5,...,21 -> 11 slips, with done days between them.
        val slipped = (1..21 step 2).toList()
        val done = (2..20 step 2).toList()
        val s = HabitRules.evaluate(input(done = done, slipped = slipped), nowForDay(21))
        assertEquals(HabitState.FAILED, s.state)
        assertEquals(FailureReason.BUDGET_EXCEEDED, s.failureReason)
        assertEquals(21, s.failedOnDay)
    }

    @Test fun a_slip_logged_yesterday_is_still_undoable_inside_the_grace_window() {
        // 09:00 on day 4 — yesterday (day 3) is unmarked, so it is still the day in play.
        val now = dateForDay(start, 4).atTime(9, 0).atZone(z).toInstant()
        val s = HabitRules.evaluate(input(done = listOf(1, 2), slipped = listOf(3)), now)
        assertEquals(3, s.currentDayNumber)
        assertEquals(3, s.undoSlipDayNumber)
        assertTrue(s.todaySlipped)
    }

    @Test fun once_the_grace_window_closes_the_slip_is_final() {
        val now = dateForDay(start, 4).atTime(11, 0).atZone(z).toInstant()
        val s = HabitRules.evaluate(input(done = listOf(1, 2), slipped = listOf(3)), now)
        assertEquals(4, s.currentDayNumber)
        assertNull(s.undoSlipDayNumber)
        assertFalse(s.todaySlipped)
        assertEquals(1, s.missCount)
    }
}
