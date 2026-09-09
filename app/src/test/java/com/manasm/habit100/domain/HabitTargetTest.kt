package com.manasm.habit100.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class HabitTargetTest {

    @Test fun round_trips_none() {
        val (kind, seconds, reps) = HabitTarget.None.toColumns()
        assertNull(kind); assertNull(seconds); assertNull(reps)
        assertSame(HabitTarget.None, HabitTarget.fromColumns(null, null, null))
    }

    @Test fun round_trips_duration() {
        val (kind, seconds, reps) = HabitTarget.Duration(600).toColumns()
        assertEquals("duration", kind); assertEquals(600, seconds); assertNull(reps)
        assertEquals(HabitTarget.Duration(600), HabitTarget.fromColumns("duration", 600, null))
    }

    @Test fun round_trips_reps() {
        val (kind, seconds, reps) = HabitTarget.Reps(50).toColumns()
        assertEquals("reps", kind); assertNull(seconds); assertEquals(50, reps)
        assertEquals(HabitTarget.Reps(50), HabitTarget.fromColumns("reps", null, 50))
    }

    @Test fun unknown_or_incomplete_columns_fall_back_to_none() {
        assertSame(HabitTarget.None, HabitTarget.fromColumns("holds", null, null))
        assertSame(HabitTarget.None, HabitTarget.fromColumns("duration", null, null))
        assertSame(HabitTarget.None, HabitTarget.fromColumns("reps", null, null))
    }

    @Test fun rejects_non_positive_values() {
        try {
            HabitTarget.Duration(0); org.junit.Assert.fail()
        } catch (e: IllegalArgumentException) { /* expected */ }
        try {
            HabitTarget.Reps(-1); org.junit.Assert.fail()
        } catch (e: IllegalArgumentException) { /* expected */ }
    }
}
