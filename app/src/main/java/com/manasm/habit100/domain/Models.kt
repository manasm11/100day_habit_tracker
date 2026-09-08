package com.manasm.habit100.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class DayStatus { DONE, MISSED }
enum class HabitState { FORMING, GRADUATED, FAILED }
enum class FailureReason { TWO_IN_A_ROW, BUDGET_EXCEEDED }

data class DayLog(val dayNumber: Int, val status: DayStatus)

data class RuleInput(
    val startDate: LocalDate,
    val zoneId: ZoneId,
    val trackLength: Int,
    val dayLogs: List<DayLog>,
    val missBudget: Int = 10,
)

data class RuleSnapshot(
    /** The day the user can act on right now — the calendar day, or yesterday during the morning grace window. */
    val currentDayNumber: Int,
    /** The true calendar day number. Differs from [currentDayNumber] only during an open grace window. */
    val calendarDayNumber: Int,
    val effectiveDay: Int,
    val doneCount: Int,
    val missCount: Int,
    val missesLeft: Int,
    val bestStreak: Int,
    val atRisk: Boolean,
    val state: HabitState,
    val failureReason: FailureReason?,
    val failedOnDay: Int?,
    val canMarkToday: Boolean,
    val todayMarkedDone: Boolean,
    /** Can the user un-mark a day right now (the attempt is forming and [undoDayNumber] is set). */
    val canUndoMark: Boolean,
    /**
     * The day `undoMarkDay` would clear: the current day if it is marked, or the grace day
     * while its window is still open and it is marked. Never a finalized day. Null when there
     * is nothing to undo.
     */
    val undoDayNumber: Int?,
    /** When the grace day locks as a miss — non-null only while a grace window is open. */
    val graceDeadline: Instant?,
)
