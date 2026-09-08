package com.manasm.habit100.rollover

import com.manasm.habit100.clock.Clock
import com.manasm.habit100.domain.DayLog
import com.manasm.habit100.domain.DayStatus
import com.manasm.habit100.domain.HabitRules
import com.manasm.habit100.domain.HabitState
import com.manasm.habit100.domain.RuleInput
import com.manasm.habit100.domain.markableDay
import java.time.ZoneId

class RolloverEngine(private val port: RolloverPort, private val clock: Clock) {

    suspend fun run() {
        val habit = port.activeHabit() ?: return
        val zone = ZoneId.of(habit.timeZoneId)
        val now = clock.now()

        val logs = port.loggedDays(habit.id, habit.currentAttempt)
        val loggedNums = logs.map { it.dayNumber }.toHashSet()
        val doneNums = logs.filter { it.status == DayStatus.DONE }.map { it.dayNumber }.toHashSet()

        // Only days that are finalized (past the markable day) become misses — a day still
        // inside its morning grace window is left pending.
        val current = markableDay(habit.attemptStartDate, zone, now, doneNums)
        val lastPastDay = minOf(current - 1, habit.attemptTrackLength)
        val missing = (1..lastPastDay).filter { it !in loggedNums }
        if (missing.isNotEmpty()) {
            port.insertMissedDays(habit.id, habit.currentAttempt, habit.attemptStartDate, missing, now)
        }

        val allLogs = logs + missing.map { DayLog(it, DayStatus.MISSED) }
        val snap = HabitRules.evaluate(
            RuleInput(habit.attemptStartDate, zone, habit.attemptTrackLength, allLogs), now,
        )
        when (snap.state) {
            HabitState.FAILED -> port.onFailed(habit, snap.failureReason!!, snap.failedOnDay!!)
            HabitState.GRADUATED -> port.onGraduated(habit)
            HabitState.FORMING -> Unit
        }
    }
}
