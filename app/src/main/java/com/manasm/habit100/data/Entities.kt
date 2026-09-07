package com.manasm.habit100.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val timeZoneId: String,
    val status: String,              // forming | mastered | failed | tuning_up | abandoned
    val currentAttempt: Int,
    val attemptStartDate: LocalDate,
    val attemptTrackLength: Int,
    val trophyAttempt: Int?,
    val slipped: Boolean,
    val createdAt: Instant,
    val graduatedAt: Instant?,
    val failureReason: String?,
    val failedOnDay: Int?,
    val graduationAcknowledged: Boolean = true,
)

@Entity(
    tableName = "day_logs",
    foreignKeys = [ForeignKey(
        entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index(value = ["habitId", "attempt", "dayNumber"], unique = true),
        Index(value = ["habitId", "attempt"]),
    ],
)
data class DayLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val attempt: Int,
    val dayNumber: Int,
    val logDate: LocalDate,
    val status: String,              // done | missed
    val markedAt: Instant,
)

@Entity(
    tableName = "maintenance_checkins",
    foreignKeys = [ForeignKey(
        entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["habitId", "period"], unique = true)],
)
data class MaintenanceCheckinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val period: String,              // YYYY-MM
    val status: String,              // strong | slipped
    val checkedAt: Instant,
)
