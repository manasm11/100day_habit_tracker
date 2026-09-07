package com.manasm.habit100.ui.shelf

import com.manasm.habit100.ui.CellState

/** The monthly maintenance pulse for a mastered habit, shown as a chip on its shelf row. */
enum class Badge { CHECK_IN, GOING_STRONG, SLIPPED }

/** One row on the mastered shelf. */
data class ShelfRow(
    val id: Long,
    val name: String,
    val cells: List<CellState>,   // 100 trophy cells
    val badge: Badge,
    val subtitle: String,
    val slipped: Boolean,
    val canTuneUp: Boolean,       // slipped, mastered, and the forming slot is free
    val tuneUpInProgress: Boolean, // this habit's own tune-up is currently running
)

/** The "Forming now" footer — the single habit currently in the forming/tune-up slot. */
data class FormingNow(
    val name: String,
    val dayNumber: Int,
    val trackLength: Int,
    val missesLeft: Int,
    val progress: Float,
)
