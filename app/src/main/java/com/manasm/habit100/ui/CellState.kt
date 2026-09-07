package com.manasm.habit100.ui

enum class CellState { DONE, MISSED, TODAY, FUTURE }

fun gridCells(
    trackLength: Int,
    currentDay: Int,
    doneDays: Set<Int>,
    missedDays: Set<Int>,
): List<CellState> = (1..100).map { day ->
    when {
        day in doneDays -> CellState.DONE
        day in missedDays -> CellState.MISSED
        day == currentDay && day <= trackLength -> CellState.TODAY
        else -> CellState.FUTURE
    }
}
