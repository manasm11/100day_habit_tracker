package com.manasm.habit100.domain

import java.time.Instant

object HabitRules {

    fun evaluate(input: RuleInput, now: Instant): RuleSnapshot {
        val doneDays = input.dayLogs
            .filter { it.status == DayStatus.DONE }
            .map { it.dayNumber }
            .toHashSet()

        val calendarDay = currentDayNumber(input.startDate, input.zoneId, now)
        // The one day the user can act on: today, or yesterday during the morning grace window.
        val currentDay = markableDay(input.startDate, input.zoneId, now, doneDays, input.graceCutoff)

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
            // else: the markable day, unmarked -> pending, not counted
            if (failureReason != null) break
            day++
        }

        val state = when {
            failureReason != null -> HabitState.FAILED
            currentDay > input.trackLength -> HabitState.GRADUATED
            todayMarkedDone && currentDay == input.trackLength -> HabitState.GRADUATED
            else -> HabitState.FORMING
        }

        val prior = currentDay - 1
        val priorMissed = prior in 1..input.trackLength &&
            prior < currentDay &&
            prior !in doneDays
        val atRisk = state == HabitState.FORMING && priorMissed && !todayMarkedDone

        val canMarkToday = state == HabitState.FORMING &&
            currentDay in 1..input.trackLength &&
            !todayMarkedDone

        val canUndoMark = state == HabitState.FORMING &&
            currentDay in 1..input.trackLength &&
            todayMarkedDone

        val graceDeadline = if (currentDay < calendarDay) {
            input.startDate.plusDays((calendarDay - 1).toLong())
                .atTime(input.graceCutoff)
                .atZone(input.zoneId)
                .toInstant()
        } else {
            null
        }

        return RuleSnapshot(
            currentDayNumber = currentDay,
            calendarDayNumber = calendarDay,
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
            canUndoMark = canUndoMark,
            graceDeadline = graceDeadline,
        )
    }
}
