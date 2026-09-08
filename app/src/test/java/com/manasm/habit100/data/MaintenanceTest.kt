package com.manasm.habit100.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.manasm.habit100.support.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MaintenanceTest {
    private lateinit var db: HabitDatabase
    private lateinit var repo: HabitRepository
    private val zone = ZoneId.of("America/New_York")
    private val clock = FakeClock(
        LocalDate.of(2026, 1, 1).atTime(12, 0).atZone(ZoneId.of("America/New_York")).toInstant()
    )

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HabitDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(HabitDatabaseTestHooks.callback())
            .build()
        repo = HabitRepository(db, db.habitDao(), db.dayLogDao(), db.checkinDao(), clock)
    }
    @After fun tearDown() = db.close()

    private suspend fun graduatedHabitId(): Long {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repeat(100) { repo.markTodayDone(id); clock.advanceDays(1) }
        return id
    }

    @Test fun mastered_row_is_check_in_due_with_full_trophy_grid() = runTest {
        val id = graduatedHabitId()
        val rows = repo.observeMastered().first()
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(id, row.habit.id)
        assertTrue(row.checkInDue)
        assertEquals(100, row.trophyCells.size)
        assertEquals(0, row.maintenanceStreakMonths)
        assertFalse(row.slipped)
        assertTrue(row.slotFree) // nothing forming after graduation
    }

    @Test fun strong_check_in_clears_due_and_starts_streak() = runTest {
        val id = graduatedHabitId()
        repo.checkIn(id, strong = true)
        val row = repo.observeMastered().first().single()
        assertFalse(row.checkInDue)
        assertEquals(1, row.maintenanceStreakMonths)
        assertFalse(row.slipped)
    }

    @Test fun same_month_slip_overrides_a_prior_strong_check_in() = runTest {
        val id = graduatedHabitId()
        repo.checkIn(id, strong = true)
        var row = repo.observeMastered().first().single()
        assertEquals(1, row.maintenanceStreakMonths)
        assertFalse(row.checkInDue)

        repo.reportSlip(id) // same calendar month
        val period = com.manasm.habit100.domain.periodOf(zone, clock.now())
        assertEquals("slipped", db.checkinDao().forPeriod(id, period)!!.status)

        row = repo.observeMastered().first().single()
        assertTrue(row.slipped)
        assertEquals(0, row.maintenanceStreakMonths)
    }

    // reportSlip is done in isolation (no prior check-in this month) so the
    // OnConflictStrategy.IGNORE against UNIQUE(habitId, period) does not swallow it.
    @Test fun report_slip_sets_flag_and_writes_slipped_checkin() = runTest {
        val id = graduatedHabitId()
        repo.reportSlip(id)
        val row = repo.observeMastered().first().single()
        assertTrue(row.slipped)
        val period = com.manasm.habit100.domain.periodOf(zone, clock.now())
        val checkin = db.checkinDao().forPeriod(id, period)
        assertNotNull(checkin)
        assertEquals("slipped", checkin!!.status)
    }
}
