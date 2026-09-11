package com.manasm.habit100.backup

import androidx.room.withTransaction
import com.manasm.habit100.clock.Clock
import com.manasm.habit100.data.CheckinDao
import com.manasm.habit100.data.DayLogDao
import com.manasm.habit100.data.DayLogEntity
import com.manasm.habit100.data.HabitDao
import com.manasm.habit100.data.HabitDatabase
import com.manasm.habit100.data.HabitEntity
import com.manasm.habit100.data.MaintenanceCheckinEntity
import com.manasm.habit100.domain.DayLog
import com.manasm.habit100.domain.DayStatus
import com.manasm.habit100.domain.HabitKind
import com.manasm.habit100.domain.HabitRules
import com.manasm.habit100.domain.HabitState
import com.manasm.habit100.domain.RuleInput
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * What a backup file would do to this device if restored (§16).
 *
 * The mastered shelf always restores cleanly — a trophy is a historical fact, not something
 * re-derived against today. Only the in-flight attempt is exposed to the calendar, which is
 * what [formingWillEnd] reports.
 */
data class RestorePreview(
    val exportedAt: Instant?,
    val daysStale: Long,
    val habitCount: Int,
    val masteredCount: Int,
    val formingName: String?,
    val formingKind: HabitKind?,
    val formingDayNumber: Int?,
    val formingWillEnd: Boolean,
)

/**
 * Export and restore (§16).
 *
 * Restore **replaces**; it never merges. The app's core invariant is one habit forming at a
 * time, and merging two device states would need conflict rules the user cannot reason about
 * at the moment they are restoring a dead phone. "This file is your app as of that date" is a
 * model that survives stress.
 */
class BackupRepository(
    private val db: HabitDatabase,
    private val habitDao: HabitDao,
    private val dayLogDao: DayLogDao,
    private val checkinDao: CheckinDao,
    private val clock: Clock,
    private val appVersion: String,
) {

    suspend fun export(): BackupFile = db.withTransaction {
        val habits = habitDao.all().map { habit ->
            BackupHabit(
                name = habit.name,
                kind = habit.kind,
                timeZoneId = habit.timeZoneId,
                status = habit.status,
                currentAttempt = habit.currentAttempt,
                attemptStartDate = habit.attemptStartDate.toString(),
                attemptTrackLength = habit.attemptTrackLength,
                trophyAttempt = habit.trophyAttempt,
                slipped = habit.slipped,
                createdAt = habit.createdAt.toEpochMilli(),
                graduatedAt = habit.graduatedAt?.toEpochMilli(),
                failureReason = habit.failureReason,
                failedOnDay = habit.failedOnDay,
                graduationAcknowledged = habit.graduationAcknowledged,
                targetKind = habit.targetKind,
                targetSeconds = habit.targetSeconds,
                targetReps = habit.targetReps,
                dayLogs = dayLogDao.allForHabit(habit.id).map {
                    BackupDayLog(
                        attempt = it.attempt,
                        dayNumber = it.dayNumber,
                        logDate = it.logDate.toString(),
                        status = it.status,
                        markedAt = it.markedAt.toEpochMilli(),
                    )
                },
                checkins = checkinDao.allForHabit(habit.id).map {
                    BackupCheckin(
                        period = it.period,
                        status = it.status,
                        checkedAt = it.checkedAt.toEpochMilli(),
                    )
                },
            )
        }
        BackupFile(
            exportedAt = clock.now().toString(),
            appVersion = appVersion,
            habits = habits,
        )
    }

    /** A read-only look at what [file] holds, for the confirmation dialog. */
    fun preview(file: BackupFile): RestorePreview {
        val now = clock.now()
        val exportedAt = runCatching { Instant.parse(file.exportedAt) }.getOrNull()
        val forming = file.habits.firstOrNull { it.status == "forming" || it.status == "tuning_up" }
        val snapshot = forming?.let { h ->
            runCatching {
                HabitRules.evaluate(
                    RuleInput(
                        startDate = LocalDate.parse(h.attemptStartDate),
                        zoneId = ZoneId.of(h.timeZoneId),
                        trackLength = h.attemptTrackLength,
                        dayLogs = h.dayLogs.map {
                            DayLog(
                                it.dayNumber,
                                if (it.status == "done") DayStatus.DONE else DayStatus.MISSED,
                            )
                        },
                    ),
                    now,
                )
            }.getOrNull()
        }
        return RestorePreview(
            exportedAt = exportedAt,
            daysStale = exportedAt?.let { ChronoUnit.DAYS.between(it, now).coerceAtLeast(0) } ?: 0,
            habitCount = file.habits.size,
            masteredCount = file.habits.count { it.trophyAttempt != null },
            formingName = forming?.name,
            formingKind = forming?.let { HabitKind.fromColumn(it.kind) },
            formingDayNumber = snapshot?.currentDayNumber,
            formingWillEnd = snapshot != null && snapshot.state != HabitState.FORMING,
        )
    }

    /**
     * Wipes the database and writes [file] in its place, all in one transaction — a failure
     * part-way through leaves the device exactly as it was.
     *
     * The single-active-slot trigger still applies, so a damaged backup carrying two forming
     * habits aborts the whole restore rather than producing a state the app cannot represent.
     */
    suspend fun restore(file: BackupFile) {
        try {
            db.withTransaction {
                habitDao.deleteAll() // day_logs and maintenance_checkins cascade
                file.habits.forEach { h ->
                    val id = habitDao.insert(h.toEntity())
                    dayLogDao.insertAll(h.dayLogs.map { it.toEntity(id) })
                    checkinDao.insertAll(h.checkins.map { it.toEntity(id) })
                }
            }
        } catch (e: BackupError) {
            throw e
        } catch (e: Exception) {
            throw BackupError(
                "That backup couldn't be restored — the file looks damaged. Nothing on this " +
                    "device was changed.",
                e,
            )
        }
    }

    private fun BackupHabit.toEntity() = HabitEntity(
        name = name,
        timeZoneId = timeZoneId,
        status = status,
        currentAttempt = currentAttempt,
        attemptStartDate = LocalDate.parse(attemptStartDate),
        attemptTrackLength = attemptTrackLength,
        trophyAttempt = trophyAttempt,
        slipped = slipped,
        createdAt = Instant.ofEpochMilli(createdAt),
        graduatedAt = graduatedAt?.let(Instant::ofEpochMilli),
        failureReason = failureReason,
        failedOnDay = failedOnDay,
        graduationAcknowledged = graduationAcknowledged,
        kind = kind,
        targetKind = targetKind,
        targetSeconds = targetSeconds,
        targetReps = targetReps,
    )

    private fun BackupDayLog.toEntity(habitId: Long) = DayLogEntity(
        habitId = habitId,
        attempt = attempt,
        dayNumber = dayNumber,
        logDate = LocalDate.parse(logDate),
        status = status,
        markedAt = Instant.ofEpochMilli(markedAt),
    )

    private fun BackupCheckin.toEntity(habitId: Long) = MaintenanceCheckinEntity(
        habitId = habitId,
        period = period,
        status = status,
        checkedAt = Instant.ofEpochMilli(checkedAt),
    )
}
