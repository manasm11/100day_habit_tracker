package com.manasm.habit100.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class HabitRulesTest {
    private val z = ZoneId.of("America/New_York")
    private val start = LocalDate.of(2026, 1, 1)
    private val jvmDefaultZone = TimeZone.getDefault()

    @After fun restoreJvmZone() = TimeZone.setDefault(jvmDefaultZone)

    /** now = the moment described by [dayNumber] at 12:00 local. */
    private fun nowForDay(dayNumber: Int): Instant =
        dateForDay(start, dayNumber).atTime(12, 0).atZone(z).toInstant()

    // `evaluate` derives misses positionally (any elapsed day not explicitly DONE) and ignores
    // DayStatus.MISSED log entries by design — so `input()` only supplies DONE days.
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
        // done on days 1,3,5,7; today = day 11. Elapsed unmarked misses: 2,4,6,8,9,10.
        // Day 8 is a miss (consecutive=1), day 9 is the 2nd consecutive miss -> fails at day 9.
        // Total misses (5) never reaches the budget of 10, so never-twice is what fires.
        val done = intArrayOf(1, 3, 5, 7)
        val s = HabitRules.evaluate(input(done = done), nowForDay(11))
        assertEquals(FailureReason.TWO_IN_A_ROW, s.failureReason)
        assertEquals(9, s.failedOnDay)
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

    // --- graduation ---

    @Test fun graduates_when_day_100_marked_done_with_clean_record() {
        val done = (1..100).toList().toIntArray()
        val s = HabitRules.evaluate(input(done = done), nowForDay(100))
        assertEquals(HabitState.GRADUATED, s.state)
        assertEquals(100, s.doneCount)
        assertEquals(100, s.bestStreak)
        assertEquals(0, s.missCount)
    }

    @Test fun graduates_past_window_with_misses_within_budget() {
        // done every day except days 10,20 (non-consecutive) ; today day 101
        val done = (1..100).filter { it != 10 && it != 20 }.toIntArray()
        val s = HabitRules.evaluate(input(done = done), nowForDay(101))
        assertEquals(HabitState.GRADUATED, s.state)
        assertEquals(2, s.missCount)
    }

    @Test fun day_100_implied_miss_still_graduates_if_not_two_in_row() {
        // done days 1..99, today day 101 -> day 100 implied miss (1 total), day 99 done
        val done = (1..99).toList().toIntArray()
        val s = HabitRules.evaluate(input(done = done), nowForDay(101))
        assertEquals(HabitState.GRADUATED, s.state)
        assertEquals(1, s.missCount)
    }

    @Test fun day_99_and_100_both_missed_fails_two_in_row_on_100() {
        val done = (1..98).toList().toIntArray()
        val s = HabitRules.evaluate(input(done = done), nowForDay(101))
        assertEquals(HabitState.FAILED, s.state)
        assertEquals(FailureReason.TWO_IN_A_ROW, s.failureReason)
        assertEquals(100, s.failedOnDay)
    }

    @Test fun day_100_today_unmarked_is_still_forming() {
        val done = (1..99).toList().toIntArray()
        val s = HabitRules.evaluate(input(done = done), nowForDay(100))
        assertEquals(HabitState.FORMING, s.state)
        assertEquals(true, s.canMarkToday)
    }

    @Test fun tuneup_track_length_30_graduates() {
        val done = (1..30).toList().toIntArray()
        val s = HabitRules.evaluate(input(trackLength = 30, done = done), nowForDay(30))
        assertEquals(HabitState.GRADUATED, s.state)
        assertEquals(30, s.effectiveDay)
    }

    @Test fun tuneup_fails_two_in_a_row_on_day_30() {
        val done = (1..28).toList().toIntArray() // days 29, 30 both elapsed unmarked
        val s = HabitRules.evaluate(input(trackLength = 30, done = done), nowForDay(31))
        assertEquals(HabitState.FAILED, s.state)
        assertEquals(FailureReason.TWO_IN_A_ROW, s.failureReason)
        assertEquals(30, s.failedOnDay)
    }

    @Test fun tuneup_fails_on_budget() {
        // done on odd days 1..21 -> misses 2,4,...,22 = 11, none consecutive
        val done = (1..21 step 2).toList().toIntArray()
        val s = HabitRules.evaluate(input(trackLength = 30, done = done), nowForDay(23))
        assertEquals(HabitState.FAILED, s.state)
        assertEquals(FailureReason.BUDGET_EXCEEDED, s.failureReason)
        assertEquals(22, s.failedOnDay)
    }

    @Test fun eleventh_miss_that_is_also_second_consecutive_reports_two_in_a_row() {
        // isolated misses on even days 2..18 (9), then days 20 & 21 missed -> misses 10 & 11
        // are a consecutive pair. never-twice wins the tie over budget-exceeded.
        val done = intArrayOf(1, 3, 5, 7, 9, 11, 13, 15, 17, 19)
        val s = HabitRules.evaluate(input(done = done), nowForDay(22))
        assertEquals(HabitState.FAILED, s.state)
        assertEquals(FailureReason.TWO_IN_A_ROW, s.failureReason)
        assertEquals(21, s.failedOnDay)
        assertEquals(11, s.missCount)
    }

    @Test fun cannot_mark_today_past_the_window() {
        val s = HabitRules.evaluate(input(), nowForDay(105))
        assertFalse(s.canMarkToday)
    }

    @Test fun evaluate_uses_input_zone_not_jvm_default() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu")) // UTC-10
        // 23:00 UTC Jan 1 == 13:00 Jan 2 in Kiritimati (UTC+14) -> day 2;
        // in the JVM-default Honolulu zone it would still be Jan 1 -> day 1.
        val now = LocalDate.of(2026, 1, 1).atTime(23, 0).atZone(ZoneId.of("UTC")).toInstant()
        val ri = RuleInput(
            startDate = start, zoneId = ZoneId.of("Pacific/Kiritimati"),
            trackLength = 100, dayLogs = emptyList(),
        )
        assertEquals(2, HabitRules.evaluate(ri, now).currentDayNumber)
    }

    // --- at-risk ---

    @Test fun at_risk_when_yesterday_missed_and_today_unmarked() {
        // today day 4; days 1..2 done, day 3 (yesterday) missed
        val s = HabitRules.evaluate(input(done = intArrayOf(1, 2)), nowForDay(4))
        assertEquals(HabitState.FORMING, s.state)
        assertEquals(true, s.atRisk)
    }

    @Test fun not_at_risk_once_today_marked() {
        val s = HabitRules.evaluate(input(done = intArrayOf(1, 2, 4)), nowForDay(4))
        assertEquals(false, s.atRisk)
    }

    @Test fun not_at_risk_when_yesterday_was_done() {
        val s = HabitRules.evaluate(input(done = intArrayOf(1, 2, 3)), nowForDay(4))
        assertEquals(false, s.atRisk)
    }

    @Test fun not_at_risk_on_day_1() {
        val s = HabitRules.evaluate(input(), nowForDay(1))
        assertEquals(false, s.atRisk)
    }

    // --- best streak ---

    @Test fun best_streak_is_longest_run_of_done() {
        // done 1,2,3 (streak 3) miss 4, done 5,6 (streak 2), today 7
        val s = HabitRules.evaluate(input(done = intArrayOf(1, 2, 3, 5, 6)), nowForDay(7))
        assertEquals(3, s.bestStreak)
    }

    // --- DST + date-line ---

    @Test fun day_number_survives_spring_dst_gap() {
        val z = ZoneId.of("America/New_York")
        val start = LocalDate.of(2026, 3, 7) // DST begins Mar 8, 2026
        val now = LocalDate.of(2026, 3, 10).atTime(12, 0).atZone(z).toInstant()
        assertEquals(4, currentDayNumber(start, z, now))
    }

    @Test fun day_number_uses_far_east_zone() {
        val z = ZoneId.of("Pacific/Kiritimati") // UTC+14
        val start = LocalDate.of(2026, 1, 1)
        // 23:00 UTC Jan 1 == 13:00 Jan 2 in Kiritimati -> day 2
        val now = LocalDate.of(2026, 1, 1).atTime(23, 0).atZone(ZoneId.of("UTC")).toInstant()
        assertEquals(2, currentDayNumber(start, z, now))
    }

    // --- morning grace window (mark yesterday until 10:00 local) ---

    /** now = calendar day [calDay] at [hour]:[minute] local. */
    private fun at(calDay: Int, hour: Int, minute: Int = 0): Instant =
        dateForDay(start, calDay).atTime(hour, minute).atZone(z).toInstant()

    private fun graceCutoffInstant(calDay: Int): Instant =
        dateForDay(start, calDay).atTime(10, 0).atZone(z).toInstant()

    @Test fun grace_window_keeps_yesterday_as_the_markable_day() {
        // 08:00 on calendar day 5; days 1-3 done, day 4 not yet marked
        val s = HabitRules.evaluate(input(done = intArrayOf(1, 2, 3)), at(5, 8))
        assertEquals(4, s.currentDayNumber)          // the day in play
        assertEquals(5, s.calendarDayNumber)
        assertEquals(graceCutoffInstant(5), s.graceDeadline)
        assertEquals(true, s.canMarkToday)
        assertEquals(0, s.missCount)                 // day 4 is pending, not a miss, until 10:00
        assertEquals(HabitState.FORMING, s.state)
    }

    @Test fun grace_window_closes_at_the_cutoff_and_yesterday_becomes_a_miss() {
        val s = HabitRules.evaluate(input(done = intArrayOf(1, 2, 3)), at(5, 10, 1))
        assertEquals(5, s.currentDayNumber)
        assertEquals(5, s.calendarDayNumber)
        assertNull(s.graceDeadline)
        assertEquals(1, s.missCount)                 // day 4 has locked as a miss
    }

    @Test fun no_grace_day_on_day_1() {
        val s = HabitRules.evaluate(input(), at(1, 6))
        assertEquals(1, s.currentDayNumber)
        assertEquals(1, s.calendarDayNumber)
        assertNull(s.graceDeadline)
    }

    @Test fun grace_day_is_not_offered_once_yesterday_is_done() {
        val s = HabitRules.evaluate(input(done = intArrayOf(1, 2, 3, 4)), at(5, 8))
        assertEquals(5, s.currentDayNumber)          // today, not the already-done day 4
        assertEquals(true, s.canMarkToday)
    }

    @Test fun at_risk_during_grace_when_the_day_before_the_grace_day_was_missed() {
        // 08:00 day 5; days 1,2 done, day 3 missed, day 4 (grace day) unmarked
        val s = HabitRules.evaluate(input(done = intArrayOf(1, 2)), at(5, 8))
        assertEquals(4, s.currentDayNumber)
        assertEquals(HabitState.FORMING, s.state)
        assertEquals(true, s.atRisk)                 // miss day 4 by 10:00 -> 3 & 4 two in a row -> fail
    }

    @Test fun marking_the_grace_day_clears_the_risk_and_advances_to_today() {
        // days 1,2 done, day 3 missed, day 4 (the grace day) now done -> markable day moves to 5
        val s = HabitRules.evaluate(input(done = intArrayOf(1, 2, 4)), at(5, 8))
        assertEquals(5, s.currentDayNumber)
        assertEquals(false, s.atRisk)
        assertEquals(1, s.missCount)                 // only day 3
        assertEquals(true, s.canMarkToday)           // day 5 is a normal current day
    }

    @Test fun grace_lets_you_still_mark_day_100() {
        // calendar day 101, 08:00; days 1-99 done, day 100 not yet marked
        val s = HabitRules.evaluate(input(done = (1..99).toList().toIntArray()), at(101, 8))
        assertEquals(100, s.currentDayNumber)
        assertEquals(HabitState.FORMING, s.state)
        assertEquals(true, s.canMarkToday)
    }

    @Test fun grace_marking_day_100_graduates() {
        val s = HabitRules.evaluate(input(done = (1..100).toList().toIntArray()), at(101, 8))
        assertEquals(HabitState.GRADUATED, s.state)
    }

    @Test fun day_100_missed_after_grace_with_day_99_missed_fails_two_in_a_row() {
        // calendar day 101, 11:00; days 1-98 done, 99 & 100 never marked
        val s = HabitRules.evaluate(input(done = (1..98).toList().toIntArray()), at(101, 11))
        assertEquals(HabitState.FAILED, s.state)
        assertEquals(FailureReason.TWO_IN_A_ROW, s.failureReason)
        assertEquals(100, s.failedOnDay)
    }

    // --- undo the markable day ---

    @Test fun can_undo_the_markable_day_when_it_is_marked() {
        val s = HabitRules.evaluate(input(done = intArrayOf(1, 2, 3)), nowForDay(3))
        assertEquals(true, s.todayMarkedDone)
        assertEquals(true, s.canUndoMark)
    }

    @Test fun cannot_undo_when_the_markable_day_is_not_marked() {
        val s = HabitRules.evaluate(input(done = intArrayOf(1, 2)), nowForDay(3))
        assertEquals(false, s.canUndoMark)
    }

    @Test fun cannot_undo_after_graduation() {
        val s = HabitRules.evaluate(input(done = (1..100).toList().toIntArray()), nowForDay(100))
        assertEquals(HabitState.GRADUATED, s.state)
        assertEquals(false, s.canUndoMark)
    }
}
