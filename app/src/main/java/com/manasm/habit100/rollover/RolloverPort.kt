package com.manasm.habit100.rollover

import com.manasm.habit100.data.HabitEntity
import com.manasm.habit100.domain.DayLog
import com.manasm.habit100.domain.FailureReason
import java.time.Instant
import java.time.LocalDate

interface RolloverPort {
    suspend fun activeHabit(): HabitEntity?
    suspend fun loggedDays(habitId: Long, attempt: Int): List<DayLog>
    suspend fun insertMissedDays(
        habitId: Long,
        attempt: Int,
        startDate: LocalDate,
        days: List<Int>,
        markedAt: Instant,
    )
    suspend fun onFailed(habit: HabitEntity, reason: FailureReason, onDay: Int)
    suspend fun onGraduated(habit: HabitEntity)
}
