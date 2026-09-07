package com.manasm.habit100.data

import androidx.room.withTransaction
import com.manasm.habit100.clock.Clock
import com.manasm.habit100.domain.DayLog
import com.manasm.habit100.domain.HabitRules
import com.manasm.habit100.domain.HabitState
import com.manasm.habit100.domain.RuleSnapshot
import com.manasm.habit100.domain.dateForDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HabitRepository(
    private val db: HabitDatabase,
    private val habitDao: HabitDao,
    private val dayLogDao: DayLogDao,
    private val checkinDao: CheckinDao,
    private val clock: Clock,
) {
    fun snapshotOf(habit: HabitEntity, logs: List<DayLog>): RuleSnapshot =
        HabitRules.evaluate(habit.toRuleInput(logs), clock.now())

    fun observeUnacknowledgedGraduation(): Flow<HabitEntity?> =
        habitDao.observeUnacknowledgedGraduation()

    fun observeFailedHabitFlow(): Flow<HabitEntity?> =
        habitDao.observeFailedHabit()

    fun observeActive(): Flow<ActiveHabit?> =
        habitDao.observeActive().flatMapLatest { habit ->
            if (habit == null) {
                flowOf(null)
            } else {
                dayLogDao.observeForAttempt(habit.id, habit.currentAttempt).map { rows ->
                    val logs = rows.map { it.toDayLog() }
                    ActiveHabit(habit, logs, snapshotOf(habit, logs))
                }
            }
        }

    suspend fun createHabit(name: String, zoneId: ZoneId) {
        check(habitDao.activeCount() == 0) { "A habit is already forming" }
        val now = clock.now()
        val today = now.atZone(zoneId).toLocalDate()
        habitDao.insert(
            HabitEntity(
                name = name.trim(),
                timeZoneId = zoneId.id,
                status = "forming",
                currentAttempt = 1,
                attemptStartDate = today,
                attemptTrackLength = 100,
                trophyAttempt = null,
                slipped = false,
                createdAt = now,
                graduatedAt = null,
                failureReason = null,
                failedOnDay = null,
            )
        )
    }

    suspend fun markTodayDone(habitId: Long) {
        db.withTransaction {
            val habit = habitDao.byId(habitId) ?: return@withTransaction
            val logs = dayLogDao.forAttempt(habit.id, habit.currentAttempt).map { it.toDayLog() }
            val snap = snapshotOf(habit, logs)
            check(snap.canMarkToday) { "Today is not markable" }
            dayLogDao.insert(
                DayLogEntity(
                    habitId = habit.id,
                    attempt = habit.currentAttempt,
                    dayNumber = snap.currentDayNumber,
                    logDate = dateForDay(habit.attemptStartDate, snap.currentDayNumber),
                    status = "done",
                    markedAt = clock.now(),
                )
            )
            applyTransitionLocked(habit.id)
        }
    }

    suspend fun applyTransition(habitId: Long) {
        db.withTransaction { applyTransitionLocked(habitId) }
    }

    private suspend fun applyTransitionLocked(habitId: Long) {
        val habit = habitDao.byId(habitId) ?: return
        if (habit.status != "forming" && habit.status != "tuning_up") return
        val logs = dayLogDao.forAttempt(habit.id, habit.currentAttempt).map { it.toDayLog() }
        val snap = snapshotOf(habit, logs)
        when (snap.state) {
            HabitState.GRADUATED -> {
                val wasTuneUp = habit.status == "tuning_up"
                habitDao.update(
                    habit.copy(
                        status = "mastered",
                        trophyAttempt = if (wasTuneUp) habit.trophyAttempt else habit.currentAttempt,
                        graduatedAt = habit.graduatedAt ?: clock.now(),
                        slipped = false,
                        failureReason = null,
                        failedOnDay = null,
                        graduationAcknowledged = false,
                    )
                )
            }
            HabitState.FAILED -> {
                val wasTuneUp = habit.status == "tuning_up"
                habitDao.update(
                    habit.copy(
                        status = if (wasTuneUp) "mastered" else "failed",
                        slipped = wasTuneUp,
                        failureReason = snap.failureReason?.name,
                        failedOnDay = snap.failedOnDay,
                    )
                )
            }
            HabitState.FORMING -> Unit
        }
    }

    /**
     * The mastered habit plus its trophy-attempt day logs, for the graduation / trophy screens.
     * Falls back to the current attempt when no trophy attempt has been stamped yet.
     */
    suspend fun trophyView(habitId: Long): Pair<HabitEntity, List<DayLog>>? {
        val habit = habitDao.byId(habitId) ?: return null
        val attempt = habit.trophyAttempt ?: habit.currentAttempt
        val logs = dayLogDao.forAttempt(habit.id, attempt).map { it.toDayLog() }
        return habit to logs
    }

    /** True when no habit is currently forming, i.e. the user may start a new one. */
    suspend fun canStartNew(): Boolean = habitDao.activeCount() == 0

    suspend fun acknowledgeGraduation(habitId: Long) {
        val habit = habitDao.byId(habitId) ?: return
        habitDao.update(habit.copy(graduationAcknowledged = true))
    }

    suspend fun restartFailedHabit(habitId: Long) {
        db.withTransaction {
            val habit = habitDao.byId(habitId) ?: return@withTransaction
            check(habit.status == "failed") { "Habit is not failed" }
            check(habitDao.activeCount() == 0) { "A habit is already forming" }
            val zone = ZoneId.of(habit.timeZoneId)
            val today = clock.now().atZone(zone).toLocalDate()
            habitDao.update(
                habit.copy(
                    status = "forming",
                    currentAttempt = habit.currentAttempt + 1,
                    attemptStartDate = today,
                    attemptTrackLength = 100,
                    failureReason = null,
                    failedOnDay = null,
                )
            )
        }
    }

    suspend fun abandonHabit(habitId: Long) {
        db.withTransaction {
            val habit = habitDao.byId(habitId) ?: return@withTransaction
            check(habit.status == "failed") { "Habit is not failed" }
            // "abandoned" is a free-string status excluded from every observe* query, so
            // the habit simply disappears from all screens.
            habitDao.update(habit.copy(status = "abandoned"))
        }
    }
}
