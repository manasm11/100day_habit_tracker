package com.manasm.habit100.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceTest {

    @Test fun check_in_due_when_period_absent() {
        assertTrue(isCheckInDue("2026-09", setOf("2026-08", "2026-07")))
    }

    @Test fun check_in_not_due_when_period_present() {
        assertFalse(isCheckInDue("2026-09", setOf("2026-09", "2026-08")))
    }

    @Test fun check_in_due_when_no_history() {
        assertTrue(isCheckInDue("2026-09", emptySet()))
    }

    @Test fun streak_counts_back_consecutive_strong_months_and_stops_at_gap() {
        // current 2026-09, strong {2026-09, 2026-08, 2026-06} -> 09, 08 count; 07 missing -> 2
        assertEquals(2, maintenanceStreakMonths("2026-09", setOf("2026-09", "2026-08", "2026-06")))
    }

    @Test fun streak_zero_when_current_month_not_strong() {
        assertEquals(0, maintenanceStreakMonths("2026-09", setOf("2026-08", "2026-07")))
    }

    @Test fun streak_spans_year_boundary() {
        assertEquals(3, maintenanceStreakMonths("2027-01", setOf("2027-01", "2026-12", "2026-11")))
    }
}
