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
    ) : TrackerUiState

    data class Graduated(val habitId: Long) : TrackerUiState

    data class Failed(
        val habitId: Long,
        val habitName: String,
        val reason: String,
        val failedOnDay: Int,
    ) : TrackerUiState
}
