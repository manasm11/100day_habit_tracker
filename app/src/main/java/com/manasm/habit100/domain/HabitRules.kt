package com.manasm.habit100.domain

import java.time.Instant

object HabitRules {

    fun evaluate(input: RuleInput, now: Instant): RuleSnapshot {
        val doneDays = input.dayLogs
            .filter { it.status == DayStatus.DONE }
            .map { it.dayNumber }
            .toHashSet()
        // Explicit slips (§15). Misses are otherwise derived positionally, so a MISSED row on
        // an elapsed day is redundant — it only changes anything on the day still in play,
        // which it finalizes immediately instead of leaving pending until rollover.
        val slippedDays = input.dayLogs
            .filter { it.status == DayStatus.MISSED }
            .map { it.dayNumber }
            .toHashSet()

        val calendarDay = currentDayNumber(input.startDate, input.zoneId, now)
        // The one day the user can act on: today, or yesterday during the morning grace window.
        val currentDay = markableDay(input.startDate, input.zoneId, now, doneDays)
        val graceWindowOpen = calendarDay >= 2 &&
            now.atZone(input.zoneId).toLocalTime() < DEFAULT_GRACE_CUTOFF

        val lastDay = minOf(currentDay, input.trackLength)
        val todayMarkedDone = currentDay <= input.trackLength && currentDay in doneDays
        val todaySlipped = currentDay <= input.trackLength && currentDay in slippedDays

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
            // An explicit slip finalizes its day even when that day is still the one in play.
            val isFinalized = day < currentDay || day in slippedDays
            if (isDone) {
                done++
                consecutive = 0
                streak++
                if (streak > bestStreak) bestStreak = streak
            } else if (isFinalized) {
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
            !todayMarkedDone &&
            !todaySlipped

        // An accidental slip is recoverable right up until it ends the attempt; past that the
        // attempt is over and stays over (the confirm dialog warns before a fatal one).
        val undoSlipDayNumber: Int? =
            currentDay.takeIf { state == HabitState.FORMING && todaySlipped }

        // The day undo would clear: the current calendar day if marked, else the grace day
        // while its window is still open and it is marked. Prefer the later of the two.
        val undoDayNumber: Int? = if (state != HabitState.FORMING) {
            null
        } else {
            listOfNotNull(
                (calendarDay - 1).takeIf {
                    graceWindowOpen && it in 1..input.trackLength && it in doneDays
                },
                calendarDay.takeIf { it in 1..input.trackLength && it in doneDays },
            ).maxOrNull()
        }
        val canUndoMark = undoDayNumber != null

        val graceDeadline = if (currentDay < calendarDay) {
            input.startDate.plusDays((calendarDay - 1).toLong())
                .atTime(DEFAULT_GRACE_CUTOFF)
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
            todaySlipped = todaySlipped,
            undoSlipDayNumber = undoSlipDayNumber,
            canUndoMark = canUndoMark,
            undoDayNumber = undoDayNumber,
            graceDeadline = graceDeadline,
        )
    }
}
