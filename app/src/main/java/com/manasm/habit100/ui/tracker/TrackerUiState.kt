package com.manasm.habit100.ui.tracker

import com.manasm.habit100.domain.HabitKind
import com.manasm.habit100.ui.CellState

sealed interface TrackerUiState {
    data object Loading : TrackerUiState

    data object Empty : TrackerUiState

    data class Forming(
        val habitId: Long,
        val name: String,
        val dayNumber: Int,
        val trackLength: Int,
        val doneCount: Int,
        val missesLeft: Int,
        val atRisk: Boolean,
        val canMarkToday: Boolean,
        val alreadyDoneToday: Boolean,
        val cells: List<CellState>,
        val isTuneUp: Boolean,
        /** [dayNumber] is yesterday, still markable during the morning grace window. */
        val isGraceDay: Boolean,
        /** Local time the grace day locks as a miss, e.g. "10:00 AM" — non-null only while [isGraceDay]. */
        val graceDeadlineText: String?,
        /** A marked day can still be un-marked (with confirmation). */
        val canUndo: Boolean,
        /** The day the undo affordance would clear (0 when [canUndo] is false). */
        val undoDayNumber: Int,
        /** The habit has a timer / rep target — the primary action is "Start" (§14). */
        val hasTarget: Boolean,
        /** What the daily mark means — drives every label on this screen (§15). */
        val kind: HabitKind,
        /** A quit habit with its day still open can name a slip. */
        val canSlip: Boolean,
        /** The day in play was named a slip. */
        val todaySlipped: Boolean,
        /** That slip can still be taken back — false once it has ended the attempt. */
        val canUndoSlip: Boolean,
        /** Slipping right now would end the attempt: second in a row, or the budget is spent. */
        val slipEndsAttempt: Boolean,
    ) : TrackerUiState

    data class Graduated(val habitId: Long) : TrackerUiState

    data class Failed(
        val habitId: Long,
        val habitName: String,
        val reason: String,
        val failedOnDay: Int,
        val kind: HabitKind,
    ) : TrackerUiState
}
