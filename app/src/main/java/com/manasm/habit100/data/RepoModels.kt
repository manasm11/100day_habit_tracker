package com.manasm.habit100.data

import com.manasm.habit100.domain.DayLog
import com.manasm.habit100.domain.RuleSnapshot

data class ActiveHabit(
    val habit: HabitEntity,
    val logs: List<DayLog>,
    val snapshot: RuleSnapshot,
)
