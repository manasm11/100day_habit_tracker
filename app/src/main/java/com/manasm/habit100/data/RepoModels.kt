package com.manasm.habit100.data

import com.manasm.habit100.domain.DayLog
import com.manasm.habit100.domain.RuleSnapshot
import com.manasm.habit100.ui.CellState

data class ActiveHabit(
    val habit: HabitEntity,
    val logs: List<DayLog>,
    val snapshot: RuleSnapshot,
)

/**
 * The mastered habit plus its trophy-attempt day logs, for the graduation / trophy screens.
 * [trackLength] is the trophy attempt's own track length (100 once a trophy is stamped),
 * NOT the habit's current [HabitEntity.attemptTrackLength] (which is 30 during a tune-up).
 * [isTuneUp] is true when the habit graduated via a tune-up whose attempt differs from the trophy.
 */
data class TrophyView(
    val habit: HabitEntity,
    val logs: List<DayLog>,
    val trackLength: Int,
    val isTuneUp: Boolean,
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
