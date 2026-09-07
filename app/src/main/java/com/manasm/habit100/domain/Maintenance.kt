package com.manasm.habit100.domain

import java.time.YearMonth

/** True when the current period ("YYYY-MM") has no check-in yet. */
fun isCheckInDue(currentPeriod: String, existingPeriods: Set<String>): Boolean =
    currentPeriod !in existingPeriods

/**
 * Number of consecutive months ("YYYY-MM"), counting back from [currentPeriod]
 * inclusive, that appear in [strongPeriods]. Stops at the first gap.
 */
fun maintenanceStreakMonths(currentPeriod: String, strongPeriods: Set<String>): Int {
    var p = YearMonth.parse(currentPeriod)
    var count = 0
    while (p.toString() in strongPeriods) {
        count++
        p = p.minusMonths(1)
    }
    return count
}
