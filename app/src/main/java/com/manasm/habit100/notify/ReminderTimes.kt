package com.manasm.habit100.notify

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** One hour before the 10:00 grace cutoff. */
val GRACE_TIME: LocalTime = LocalTime.of(9, 0)

/** Evening "did you do it today?" nudge. */
val EVENING_TIME: LocalTime = LocalTime.of(19, 30)

/** The next instant [kind]'s alarm should fire, in [zoneId], strictly after [now]. */
fun nextTrigger(kind: ReminderKind, zoneId: ZoneId, now: Instant): Instant {
    val time = if (kind == ReminderKind.GRACE) GRACE_TIME else EVENING_TIME
    var candidate = now.atZone(zoneId).toLocalDate().atTime(time).atZone(zoneId)
    if (!candidate.toInstant().isAfter(now)) candidate = candidate.plusDays(1)
    return candidate.toInstant()
}
