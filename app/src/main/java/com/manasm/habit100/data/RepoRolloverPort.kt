package com.manasm.habit100.data

import androidx.room.withTransaction
import com.manasm.habit100.domain.DayLog
import com.manasm.habit100.domain.FailureReason
import com.manasm.habit100.domain.dateForDay
import com.manasm.habit100.rollover.RolloverPort
import java.time.Instant
import java.time.LocalDate

class RepoRolloverPort(
    private val repo: HabitRepository,
    private val db: HabitDatabase,
    private val habitDao: HabitDao,
    private val dayLogDao: DayLogDao,
) : RolloverPort {

    override suspend fun activeHabit(): HabitEntity? = habitDao.activeOnce()

    override suspend fun loggedDays(habitId: Long, attempt: Int): List<DayLog> =
        dayLogDao.forAttempt(habitId, attempt).map { it.toDayLog() }

    override suspend fun insertMissedDays(
        habitId: Long,
        attempt: Int,
        startDate: LocalDate,
        days: List<Int>,
        markedAt: Instant,
    ) {
        db.withTransaction {
            dayLogDao.insertAll(
                days.sorted().map {
                    DayLogEntity(
                        habitId = habitId,
                        attempt = attempt,
                        dayNumber = it,
                        logDate = dateForDay(startDate, it),
                        status = "missed",
                        markedAt = markedAt,
                    )
                }
            )
        }
    }

    override suspend fun onFailed(habit: HabitEntity, reason: FailureReason, onDay: Int) {
        repo.applyTransition(habit.id)
    }

    override suspend fun onGraduated(habit: HabitEntity) {
        repo.applyTransition(habit.id)
    }
}
