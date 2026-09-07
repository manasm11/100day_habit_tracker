package com.manasm.habit100.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CellStateTest {
    @Test fun maps_done_missed_today_future() {
        val cells = gridCells(
            trackLength = 100, currentDay = 4,
            doneDays = setOf(1, 3), missedDays = setOf(2),
        )
        assertEquals(100, cells.size)
        assertEquals(CellState.DONE, cells[0])
        assertEquals(CellState.MISSED, cells[1])
        assertEquals(CellState.DONE, cells[2])
        assertEquals(CellState.TODAY, cells[3])
        assertEquals(CellState.FUTURE, cells[4])
    }

    @Test fun past_current_day_has_no_today_cell() {
        val cells = gridCells(100, currentDay = 105, doneDays = (1..100).toSet(), missedDays = emptySet())
        assertEquals(CellState.DONE, cells[99])
        assertEquals(0, cells.count { it == CellState.TODAY })
    }

    @Test fun tuneup_cells_beyond_30_are_future() {
        val cells = gridCells(30, currentDay = 5, doneDays = setOf(1,2,3,4), missedDays = emptySet())
        assertEquals(CellState.TODAY, cells[4])
        assertEquals(CellState.FUTURE, cells[29])
        assertEquals(CellState.FUTURE, cells[99])
    }
}
