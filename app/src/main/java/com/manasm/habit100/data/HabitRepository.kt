package com.manasm.habit100.data

import androidx.room.withTransaction
import com.manasm.habit100.clock.Clock
import com.manasm.habit100.domain.DayLog
import com.manasm.habit100.domain.DayStatus
import com.manasm.habit100.domain.HabitRules
import com.manasm.habit100.domain.HabitState
import com.manasm.habit100.domain.RuleSnapshot
import com.manasm.habit100.domain.dateForDay
import com.manasm.habit100.domain.isCheckInDue
import com.manasm.habit100.domain.maintenanceStreakMonths
import com.manasm.habit100.domain.periodOf
import com.manasm.habit100.ui.gridCells
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    /**
     * Records this month's maintenance check-in for a mastered habit. A "slipped" check-in
     * also raises the habit's [HabitEntity.slipped] flag.
     *
     * One row per (habit, period). A slip is sticky: a same-month slip after a "strong"
     * check-in overwrites the row to "slipped" (§2.10 — the slip must take effect), while a
     * "strong" check-in never overwrites an existing "slipped" row. Strong-when-strong and
     * slip-when-slipped are no-ops.
     */
    suspend fun checkIn(habitId: Long, strong: Boolean) {
        db.withTransaction {
            val habit = habitDao.byId(habitId) ?: return@withTransaction
            if (habit.status != "mastered") return@withTransaction
            val period = periodOf(ZoneId.of(habit.timeZoneId), clock.now())
            val status = if (strong) "strong" else "slipped"
            val existing = checkinDao.forPeriod(habitId, period)
            when {
                existing == null -> checkinDao.insert(
                    MaintenanceCheckinEntity(
                        habitId = habitId,
                        period = period,
                        status = status,
                        checkedAt = clock.now(),
                    )
                )
                !strong && existing.status == "strong" ->
                    checkinDao.update(existing.copy(status = "slipped", checkedAt = clock.now()))
            }
            if (!strong) habitDao.update(habit.copy(slipped = true))
        }
    }

    suspend fun reportSlip(habitId: Long) = checkIn(habitId, strong = false)

    /**
     * Begins a 30-day tune-up for a slipped mastered habit. Moves it back into the forming
     * slot (`tuning_up`), bumping the attempt counter and shortening the track to 30 days.
     * The trophy attempt is left untouched, so the habit stays on the mastered shelf.
     * Task 18 owns the tune-up tracker/graduation lifecycle.
     */
    suspend fun startTuneUp(habitId: Long) {
        db.withTransaction {
            val habit = habitDao.byId(habitId) ?: return@withTransaction
            check(habit.status == "mastered") { "Only a mastered habit can tune up" }
            check(habit.slipped) { "Habit is not slipped" }
            check(habitDao.activeCount() == 0) { "A habit is already forming" }
            val zone = ZoneId.of(habit.timeZoneId)
            habitDao.update(
                habit.copy(
                    status = "tuning_up",
                    currentAttempt = habit.currentAttempt + 1,
                    attemptStartDate = clock.now().atZone(zone).toLocalDate(),
                    attemptTrackLength = 30,
                )
            )
        }
    }

    /** The mastered-habit shelf: one [MasteredHabitRow] per graduated habit, newest first. */
    fun observeMastered(): Flow<List<MasteredHabitRow>> =
        combine(habitDao.observeMastered(), habitDao.observeActive()) { mastered, active ->
            mastered to (active == null)
        }.flatMapLatest { (mastered, slotFree) ->
            if (mastered.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(mastered.map { habit -> masteredRowFlow(habit, slotFree) }) { rows ->
                    rows.toList()
                }
            }
        }

    private fun masteredRowFlow(habit: HabitEntity, slotFree: Boolean): Flow<MasteredHabitRow> {
        val trophyAttempt = habit.trophyAttempt ?: habit.currentAttempt
        return combine(
            dayLogDao.observeForAttempt(habit.id, trophyAttempt),
            checkinDao.observeForHabit(habit.id),
        ) { logRows, checkins ->
            val logs = logRows.map { it.toDayLog() }
            val done = logs.filter { it.status == DayStatus.DONE }.map { it.dayNumber }.toSet()
            // Positional misses: rollover may graduate without ever materializing MISSED rows,
            // so any day 1..100 not explicitly DONE is an honest red miss on the thumbnail
            // (same reasoning as GraduationViewModel).
            val missed = (1..100).filterNot { it in done }.toSet()
            val period = periodOf(ZoneId.of(habit.timeZoneId), clock.now())
            val checkinPeriods = checkins.map { it.period }.toSet()
            val strongPeriods = checkins.filter { it.status == "strong" }.map { it.period }.toSet()
            MasteredHabitRow(
                habit = habit,
                trophyCells = gridCells(
                    trackLength = 100,
                    currentDay = 101,
                    doneDays = done,
                    missedDays = missed,
                ),
                checkInDue = isCheckInDue(period, checkinPeriods) && habit.status == "mastered",
                maintenanceStreakMonths = maintenanceStreakMonths(period, strongPeriods),
                slipped = habit.slipped,
                slotFree = slotFree,
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
