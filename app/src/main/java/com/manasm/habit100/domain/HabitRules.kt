package com.manasm.habit100.domain

import java.time.Instant

object HabitRules {

    fun evaluate(input: RuleInput, now: Instant): RuleSnapshot {
        val currentDay = currentDayNumber(input.startDate, input.zoneId, now)
        val doneDays = input.dayLogs
            .filter { it.status == DayStatus.DONE }
            .map { it.dayNumber }
            .toHashSet()

        val lastDay = minOf(currentDay, input.trackLength)
        val todayMarkedDone = currentDay <= input.trackLength && currentDay in doneDays

        var done = 0
        var misses = 0
        var consecutive = 0
        var streak = 0
        var bestStreak = 0
        var failureReason: FailureReason? = null
        var failedOnDay: Int? = null

        var day = 1
        while (day <= lastDay) {
            val isDone = day in doneDays
            val isPast = day < currentDay
            if (isDone) {
                done++
                consecutive = 0
                streak++
                if (streak > bestStreak) bestStreak = streak
            } else if (isPast) {
                misses++
                consecutive++
                streak = 0
                if (consecutive >= 2) {
                    // Spec §2.6: the attempt fails the moment a 2nd consecutive miss occurs.
                    failureReason = FailureReason.TWO_IN_A_ROW
                    failedOnDay = day
                } else if (failureReason == null && misses > input.missBudget) {
                    failureReason = FailureReason.BUDGET_EXCEEDED
                    failedOnDay = day
                }
            }
            // else: current day, unmarked -> pending, not counted
            if (failureReason != null) break
            day++
        }

        val state = when {
            failureReason != null -> HabitState.FAILED
            currentDay > input.trackLength -> HabitState.GRADUATED
            todayMarkedDone && currentDay == input.trackLength -> HabitState.GRADUATED
            else -> HabitState.FORMING
        }

        val yesterday = currentDay - 1
        val yesterdayMissed = yesterday in 1..input.trackLength &&
            yesterday < currentDay &&
            yesterday !in doneDays
        val atRisk = state == HabitState.FORMING && yesterdayMissed && !todayMarkedDone

        val canMarkToday = state == HabitState.FORMING &&
            currentDay in 1..input.trackLength &&
            !todayMarkedDone

        return RuleSnapshot(
            currentDayNumber = currentDay,
            effectiveDay = lastDay,
            doneCount = done,
            missCount = misses,
            missesLeft = (input.missBudget - misses).coerceAtLeast(0),
            bestStreak = bestStreak,
            atRisk = atRisk,
            state = state,
            failureReason = failureReason,
            failedOnDay = failedOnDay,
            canMarkToday = canMarkToday,
            todayMarkedDone = todayMarkedDone,
        )
    }
}
