package com.manasm.habit100.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The morning grace window: yesterday stays markable until this local time. */
val DEFAULT_GRACE_CUTOFF: LocalTime = LocalTime.of(10, 0)

/** 1-based calendar day number for [now] given [startDate], measured in [zoneId]. Day 1 == startDate. Floored at 1. */
fun currentDayNumber(startDate: LocalDate, zoneId: ZoneId, now: Instant): Int {
    val today = now.atZone(zoneId).toLocalDate()
    val elapsed = ChronoUnit.DAYS.between(startDate, today)
    return (elapsed + 1L).coerceAtLeast(1L).toInt()
}

/**
 * The single day number the user is allowed to act on right now.
 *
 * Between local midnight and [graceCutoff], if the previous calendar day is still
 * unmarked, it stays the markable day (you can finish "yesterday" over morning coffee).
 * Otherwise the markable day is the current calendar day. This is forward-only: the
 * markable day never goes back more than one calendar day, and only when that day is
 * genuinely unfinished.
 */
fun markableDay(
    startDate: LocalDate,
    zoneId: ZoneId,
    now: Instant,
    doneDays: Set<Int>,
    graceCutoff: LocalTime = DEFAULT_GRACE_CUTOFF,
): Int {
    val wall = now.atZone(zoneId)
    val calDay = currentDayNumber(startDate, zoneId, now)
    val inGrace = wall.toLocalTime() < graceCutoff
    return if (calDay >= 2 && inGrace && (calDay - 1) !in doneDays) calDay - 1 else calDay
}

/** Calendar date of [dayNumber] (1-based) for a track starting on [startDate]. */
fun dateForDay(startDate: LocalDate, dayNumber: Int): LocalDate =
    startDate.plusDays((dayNumber - 1).toLong())

/** "YYYY-MM" period string for [now] in [zoneId]. */
fun periodOf(zoneId: ZoneId, now: Instant): String =
    YearMonth.from(now.atZone(zoneId)).toString()
