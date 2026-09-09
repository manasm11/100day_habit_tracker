package com.manasm.habit100.notify

import com.manasm.habit100.domain.FailureReason
import com.manasm.habit100.domain.HabitState
import com.manasm.habit100.domain.RuleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemindersTest {

    private fun snapshot(
        state: HabitState = HabitState.FORMING,
        markableDay: Int = 5,
        calendarDay: Int = 5,
        marked: Boolean = false,
        canMark: Boolean = true,
    ) = RuleSnapshot(
        currentDayNumber = markableDay,
        calendarDayNumber = calendarDay,
        effectiveDay = minOf(markableDay, 100),
        doneCount = 0,
        missCount = 0,
        missesLeft = 10,
        bestStreak = 0,
        atRisk = false,
        state = state,
        failureReason = if (state == HabitState.FAILED) FailureReason.TWO_IN_A_ROW else null,
        failedOnDay = if (state == HabitState.FAILED) 3 else null,
        canMarkToday = canMark,
        todayMarkedDone = marked,
        canUndoMark = false,
        undoDayNumber = null,
        graceDeadline = null,
    )

    @Test fun nothing_when_there_is_no_active_habit() {
        assertNull(reminderFor(ReminderKind.GRACE, habitName = null, snapshot = null))
        assertNull(reminderFor(ReminderKind.EVENING, habitName = null, snapshot = null))
    }

    @Test fun nothing_when_the_attempt_has_failed() {
        val s = snapshot(state = HabitState.FAILED)
        assertNull(reminderFor(ReminderKind.GRACE, "Read", s))
        assertNull(reminderFor(ReminderKind.EVENING, "Read", s))
    }

    @Test fun nothing_when_the_attempt_has_graduated() {
        val s = snapshot(state = HabitState.GRADUATED, markableDay = 100, calendarDay = 101)
        assertNull(reminderFor(ReminderKind.GRACE, "Read", s))
        assertNull(reminderFor(ReminderKind.EVENING, "Read", s))
    }

    @Test fun grace_reminder_when_yesterday_is_still_unmarked_in_the_window() {
        val s = snapshot(markableDay = 4, calendarDay = 5, marked = false)
        val c = reminderFor(ReminderKind.GRACE, "Read", s)!!
        assertEquals("Mark yesterday for Read", c.title)
        assertEquals("Yesterday isn't marked. Do it before 10:00 AM or it counts as a miss.", c.body)
    }

    @Test fun no_grace_reminder_when_markable_and_calendar_day_agree() {
        // Either it's not the morning, or yesterday was already marked — a marked grace day
        // advances markableDay so markable == calendar. Both cases: no grace reminder.
        val s = snapshot(markableDay = 5, calendarDay = 5)
        assertNull(reminderFor(ReminderKind.GRACE, "Read", s))
    }

    @Test fun evening_reminder_when_today_is_unmarked_and_markable() {
        val s = snapshot(markableDay = 7, calendarDay = 7, canMark = true, marked = false)
        val c = reminderFor(ReminderKind.EVENING, "Meditate", s)!!
        assertEquals("Time for Meditate", c.title)
        assertEquals("Day 7 of 100 — mark it done.", c.body)
    }

    @Test fun no_evening_reminder_when_today_is_already_done() {
        val s = snapshot(markableDay = 7, calendarDay = 7, canMark = false, marked = true)
        assertNull(reminderFor(ReminderKind.EVENING, "Meditate", s))
    }

    @Test fun no_evening_reminder_during_a_grace_day_the_grace_reminder_covers_it() {
        val s = snapshot(markableDay = 6, calendarDay = 7)
        assertNull(reminderFor(ReminderKind.EVENING, "Meditate", s))
    }

    @Test fun tune_up_evening_reminder_uses_the_30_day_track() {
        val s = RuleSnapshot(
            currentDayNumber = 12, calendarDayNumber = 12, effectiveDay = 12,
            doneCount = 11, missCount = 0, missesLeft = 10, bestStreak = 11, atRisk = false,
            state = HabitState.FORMING, failureReason = null, failedOnDay = null,
            canMarkToday = true, todayMarkedDone = false, canUndoMark = false,
            undoDayNumber = null, graceDeadline = null,
        )
        val c = reminderFor(ReminderKind.EVENING, "Read", s, trackLength = 30)!!
        assertEquals("Day 12 of 30 — mark it done.", c.body)
    }
}
