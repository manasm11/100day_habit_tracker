package com.manasm.habit100.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.manasm.habit100.domain.HabitState
import com.manasm.habit100.support.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HabitRepositoryTest {
    private lateinit var db: HabitDatabase
    private lateinit var repo: HabitRepository
    private val zone = ZoneId.of("America/New_York")
    private val clock = FakeClock(
        LocalDate.of(2026, 1, 1).atTime(9, 0).atZone(ZoneId.of("America/New_York")).toInstant()
    )

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HabitDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(HabitDatabaseTestHooks.callback())
            .build()
        repo = HabitRepository(db, db.habitDao(), db.dayLogDao(), db.checkinDao(), clock)
    }
    @After fun tearDown() = db.close()

    @Test fun create_then_mark_today() = runTest {
        repo.createHabit("Read", zone)
        val a1 = repo.observeActive().first()!!
        assertEquals(1, a1.snapshot.currentDayNumber)
        repo.markTodayDone(a1.habit.id)
        val a2 = repo.observeActive().first()!!
        assertEquals(1, a2.snapshot.doneCount)
        assertTrue(a2.snapshot.todayMarkedDone)
    }

    @Test fun second_create_is_rejected() = runTest {
        repo.createHabit("Read", zone)
        try {
            repo.createHabit("Run", zone)
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test fun cannot_mark_twice_same_day() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repo.markTodayDone(id)
        try {
            repo.markTodayDone(id)
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test fun marking_day_100_graduates() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repeat(100) {
            repo.markTodayDone(id)
            clock.advanceDays(1)
        }
        assertNull(repo.observeActive().first())
        val mastered = db.habitDao().byId(id)!!
        assertEquals("mastered", mastered.status)
        assertEquals(1, mastered.trophyAttempt)
    }

    // Ruling 1: assert the persisted transition directly (observeActive() emits null once failed).
    @Test fun two_missed_days_fail_on_next_open() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repo.markTodayDone(id)            // day 1 done
        clock.advanceDays(3)             // days 2,3 elapsed unmarked; now day 4
        repo.applyTransition(id)
        val h = db.habitDao().byId(id)!!
        assertEquals("failed", h.status)
        assertEquals("TWO_IN_A_ROW", h.failureReason)
        assertEquals(3, h.failedOnDay)
    }

    // Ruling 2: graduation acknowledgement flag.
    @Test fun graduation_sets_unacknowledged_then_acknowledge_clears_it() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repeat(100) { repo.markTodayDone(id); clock.advanceDays(1) }
        val g = db.habitDao().byId(id)!!
        assertEquals("mastered", g.status)
        assertEquals(false, g.graduationAcknowledged)
        assertEquals(id, db.habitDao().observeUnacknowledgedGraduation().first()?.id)
        repo.acknowledgeGraduation(id)
        assertEquals(true, db.habitDao().byId(id)!!.graduationAcknowledged)
        assertNull(db.habitDao().observeUnacknowledgedGraduation().first())
    }

    @Test fun trophy_view_after_graduation() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repeat(100) { repo.markTodayDone(id); clock.advanceDays(1) }
        val (h, logs) = repo.trophyView(id)!!
        assertEquals("mastered", h.status)
        assertEquals(
            100,
            logs.count { it.status == com.manasm.habit100.domain.DayStatus.DONE },
        )
        assertTrue(repo.canStartNew())
    }

    @Test fun failed_state_still_computable_from_snapshot() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repo.markTodayDone(id)
        clock.advanceDays(3)
        val logs = db.dayLogDao().forAttempt(id, 1).map { it.toDayLog() }
        val snap = repo.snapshotOf(db.habitDao().byId(id)!!, logs)
        assertEquals(HabitState.FAILED, snap.state)
    }
}
