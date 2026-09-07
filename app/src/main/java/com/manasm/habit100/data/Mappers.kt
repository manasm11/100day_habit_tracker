package com.manasm.habit100.data

import com.manasm.habit100.domain.DayLog
import com.manasm.habit100.domain.DayStatus
import com.manasm.habit100.domain.RuleInput
import java.time.ZoneId

fun DayLogEntity.toDayLog(): DayLog =
    DayLog(dayNumber, if (status == "done") DayStatus.DONE else DayStatus.MISSED)

fun HabitEntity.toRuleInput(logs: List<DayLog>): RuleInput = RuleInput(
    startDate = attemptStartDate,
    zoneId = ZoneId.of(timeZoneId),
    trackLength = attemptTrackLength,
    dayLogs = logs,
)
