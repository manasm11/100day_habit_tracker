package com.manasm.habit100.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 1-based day number for [now] given [startDate], measured in [zoneId]. Day 1 == startDate. Floored at 1. */
fun currentDayNumber(startDate: LocalDate, zoneId: ZoneId, now: Instant): Int {
    val today = now.atZone(zoneId).toLocalDate()
    val elapsed = ChronoUnit.DAYS.between(startDate, today)
    return (elapsed + 1L).coerceAtLeast(1L).toInt()
}

/** Calendar date of [dayNumber] (1-based) for a track starting on [startDate]. */
fun dateForDay(startDate: LocalDate, dayNumber: Int): LocalDate =
    startDate.plusDays((dayNumber - 1).toLong())

/** "YYYY-MM" period string for [now] in [zoneId]. */
fun periodOf(zoneId: ZoneId, now: Instant): String =
    YearMonth.from(now.atZone(zoneId)).toString()
