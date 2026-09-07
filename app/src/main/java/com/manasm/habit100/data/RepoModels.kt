package com.manasm.habit100.data

import com.manasm.habit100.domain.DayLog
import com.manasm.habit100.domain.RuleSnapshot
import com.manasm.habit100.ui.CellState

data class ActiveHabit(
    val habit: HabitEntity,
    val logs: List<DayLog>,
    val snapshot: RuleSnapshot,
)

/** One row on the mastered-habit shelf: the trophy thumbnail plus the monthly maintenance pulse. */
data class MasteredHabitRow(
    val habit: HabitEntity,
    val trophyCells: List<CellState>,     // always 100 cells from the trophy attempt
    val checkInDue: Boolean,
    val maintenanceStreakMonths: Int,
    val slipped: Boolean,
    val slotFree: Boolean,                // true iff no forming/tuning_up habit
)
