package com.manasm.habit100.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Insert suspend fun insert(h: HabitEntity): Long
    @Update suspend fun update(h: HabitEntity)
    @Query("SELECT * FROM habits WHERE id = :id") suspend fun byId(id: Long): HabitEntity?

    @Query("SELECT * FROM habits WHERE status IN ('forming','tuning_up') LIMIT 1")
    fun observeActive(): Flow<HabitEntity?>

    @Query("SELECT * FROM habits WHERE status IN ('forming','tuning_up') LIMIT 1")
    suspend fun activeOnce(): HabitEntity?

    @Query("SELECT COUNT(*) FROM habits WHERE status IN ('forming','tuning_up')")
    suspend fun activeCount(): Int

    @Query("SELECT * FROM habits WHERE trophyAttempt IS NOT NULL ORDER BY graduatedAt DESC")
    fun observeMastered(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE status = 'mastered' AND graduationAcknowledged = 0 ORDER BY graduatedAt DESC LIMIT 1")
    fun observeUnacknowledgedGraduation(): Flow<HabitEntity?>

    @Query("SELECT * FROM habits WHERE status = 'failed' ORDER BY id DESC LIMIT 1")
    fun observeFailedHabit(): Flow<HabitEntity?>
}

@Dao
interface DayLogDao {
    @Insert suspend fun insert(log: DayLogEntity): Long
    @Insert suspend fun insertAll(logs: List<DayLogEntity>)

    @Query("SELECT * FROM day_logs WHERE habitId = :habitId AND attempt = :attempt ORDER BY dayNumber")
    suspend fun forAttempt(habitId: Long, attempt: Int): List<DayLogEntity>

    @Query("SELECT * FROM day_logs WHERE habitId = :habitId AND attempt = :attempt ORDER BY dayNumber")
    fun observeForAttempt(habitId: Long, attempt: Int): Flow<List<DayLogEntity>>
}

@Dao
interface CheckinDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(c: MaintenanceCheckinEntity): Long

    @Query("SELECT * FROM maintenance_checkins WHERE habitId = :habitId AND period = :period LIMIT 1")
    suspend fun forPeriod(habitId: Long, period: String): MaintenanceCheckinEntity?

    @Query("SELECT * FROM maintenance_checkins WHERE habitId = :habitId ORDER BY checkedAt DESC")
    fun observeForHabit(habitId: Long): Flow<List<MaintenanceCheckinEntity>>
}
