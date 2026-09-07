package com.manasm.habit100.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ConvertersTest {
    private val c = Converters()
    @Test fun instant_round_trip() {
        val i = Instant.ofEpochMilli(1_725_000_000_000)
        assertEquals(i, c.longToInstant(c.instantToLong(i)))
    }
    @Test fun date_round_trip() {
        val d = LocalDate.of(2026, 9, 7)
        assertEquals(d, c.stringToDate(c.dateToString(d)))
    }
    @Test fun nulls() {
        assertEquals(null, c.instantToLong(null))
        assertEquals(null, c.stringToDate(null))
    }
}
