package com.manasm.habit100.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HabitDaoTest {
    private lateinit var db: HabitDatabase

    private fun habit(status: String = "forming", attempt: Int = 1) = HabitEntity(
        name = "Read", timeZoneId = "America/New_York", status = status,
        currentAttempt = attempt, attemptStartDate = LocalDate.of(2026, 1, 1),
        attemptTrackLength = 100, trophyAttempt = null, slipped = false,
        createdAt = Instant.EPOCH, graduatedAt = null, failureReason = null, failedOnDay = null,
    )

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), HabitDatabase::class.java,
        ).allowMainThreadQueries()
            .addCallback(HabitDatabaseTestHooks.callback())
            .build()
    }

    @After fun tearDown() = db.close()

    @Test fun insert_and_read_active() = runTest {
        db.habitDao().insert(habit())
        assertEquals("Read", db.habitDao().activeOnce()?.name)
        assertEquals(1, db.habitDao().activeCount())
    }

    @Test fun day_log_unique_index_rejects_duplicate_day() = runTest {
        val id = db.habitDao().insert(habit())
        db.dayLogDao().insert(DayLogEntity(habitId = id, attempt = 1, dayNumber = 3, logDate = LocalDate.of(2026,1,3), status = "done", markedAt = Instant.EPOCH))
        var threw = false
        try {
            db.dayLogDao().insert(DayLogEntity(habitId = id, attempt = 1, dayNumber = 3, logDate = LocalDate.of(2026,1,3), status = "missed", markedAt = Instant.EPOCH))
        } catch (e: android.database.sqlite.SQLiteConstraintException) { threw = true }
        assertTrue(threw)
    }

    @Test fun trigger_blocks_second_active_habit() = runTest {
        db.habitDao().insert(habit())
        var threw = false
        try { db.habitDao().insert(habit(attempt = 2)) }
        catch (e: android.database.sqlite.SQLiteException) { threw = true }
        assertTrue(threw)
    }

    @Test fun unacknowledged_graduation_query() = runTest {
        val id = db.habitDao().insert(habit(status = "mastered").copy(trophyAttempt = 1, graduationAcknowledged = false, graduatedAt = Instant.EPOCH))
        assertEquals(id, db.habitDao().observeUnacknowledgedGraduation().first()?.id)
        db.habitDao().update(db.habitDao().byId(id)!!.copy(graduationAcknowledged = true))
        assertNull(db.habitDao().observeUnacknowledgedGraduation().first())
    }
}
