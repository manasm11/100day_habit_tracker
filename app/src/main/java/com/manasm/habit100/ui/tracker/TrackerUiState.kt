package com.manasm.habit100.ui.tracker

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
        /** The marked day can still be un-marked (with confirmation). */
        val canUndo: Boolean,
    ) : TrackerUiState

    data class Graduated(val habitId: Long) : TrackerUiState

    data class Failed(
        val habitId: Long,
        val habitName: String,
        val reason: String,
        val failedOnDay: Int,
    ) : TrackerUiState
}
