package com.manasm.habit100.notify

import com.manasm.habit100.domain.HabitKind
import com.manasm.habit100.domain.HabitState
import com.manasm.habit100.domain.RuleSnapshot

enum class ReminderKind { GRACE, EVENING }

data class ReminderContent(val title: String, val body: String)

/**
 * Whether a reminder should fire right now, and what it should say. Pure — the receiver
 * supplies a fresh [snapshot] (or null when no habit is forming).
 *
 * - GRACE: yesterday is still unmarked and inside its grace window.
 * - EVENING: today is unmarked and markable, and it is not a grace day (GRACE covers that).
 */
fun reminderFor(
    kind: ReminderKind,
    habitName: String?,
    snapshot: RuleSnapshot?,
    trackLength: Int = 100,
    habitKind: HabitKind = HabitKind.BUILD,
): ReminderContent? {
    val quit = habitKind == HabitKind.QUIT
    if (habitName == null || snapshot == null) return null
    if (snapshot.state != HabitState.FORMING) return null

    val isGraceDay = snapshot.currentDayNumber < snapshot.calendarDayNumber

    return when (kind) {
        ReminderKind.GRACE ->
            // isGraceDay already implies the grace day is unmarked (markableDay only steps back
            // while yesterday is unmarked); the !todayMarkedDone check is belt-and-suspenders.
            if (isGraceDay && !snapshot.todayMarkedDone) {
                ReminderContent(
                    title = "Mark yesterday for $habitName",
                    body = if (quit) {
                        "Yesterday isn't marked. Mark it clean before 10:00 AM or it locks as a slip."
                    } else {
                        "Yesterday isn't marked. Do it before 10:00 AM or it counts as a miss."
                    },
                )
            } else {
                null
            }

        ReminderKind.EVENING ->
            if (!isGraceDay && snapshot.canMarkToday) {
                ReminderContent(
                    title = if (quit) "Staying clean — $habitName" else "Time for $habitName",
                    body = if (quit) {
                        "Day ${snapshot.currentDayNumber} of $trackLength — did you stay clean?"
                    } else {
                        "Day ${snapshot.currentDayNumber} of $trackLength — mark it done."
                    },
                )
            } else {
                null
            }
    }
}
