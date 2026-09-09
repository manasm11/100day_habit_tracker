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

    @Test fun create_then_mark_today() = runTest {
        repo.createHabit("Read", zone)
        val a1 = repo.observeActive().first()!!
        assertEquals(1, a1.snapshot.currentDayNumber)
        repo.markTodayDone(a1.habit.id)
        val a2 = repo.observeActive().first()!!
        assertEquals(1, a2.snapshot.doneCount)
        assertTrue(a2.snapshot.todayMarkedDone)
    }

    @Test fun create_with_a_duration_target_round_trips() = runTest {
        repo.createHabit("Meditate", zone, com.manasm.habit100.domain.HabitTarget.Duration(600))
        val h = repo.observeActive().first()!!.habit
        assertEquals("duration", h.targetKind)
        assertEquals(600, h.targetSeconds)
        assertEquals(com.manasm.habit100.domain.HabitTarget.Duration(600), h.target())
    }

    @Test fun create_with_a_reps_target_round_trips() = runTest {
        repo.createHabit("Pushups", zone, com.manasm.habit100.domain.HabitTarget.Reps(40))
        val h = repo.observeActive().first()!!.habit
        assertEquals(com.manasm.habit100.domain.HabitTarget.Reps(40), h.target())
    }

    @Test fun create_without_a_target_is_a_plain_habit() = runTest {
        repo.createHabit("Floss", zone)
        assertEquals(com.manasm.habit100.domain.HabitTarget.None, repo.observeActive().first()!!.habit.target())
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
        val tv = repo.trophyView(id)!!
        assertEquals("mastered", tv.habit.status)
        assertEquals(
            100,
            tv.logs.count { it.status == com.manasm.habit100.domain.DayStatus.DONE },
        )
        assertEquals(100, tv.trackLength)
        assertFalse(tv.isTuneUp)
        assertTrue(repo.canStartNew())
    }

    @Test fun start_tune_up_moves_slipped_mastered_habit_back_into_forming_slot() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repeat(100) { repo.markTodayDone(id); clock.advanceDays(1) }
        assertEquals("mastered", db.habitDao().byId(id)!!.status)
        repo.reportSlip(id)

        repo.startTuneUp(id)

        val active = repo.observeActive().first()!!
        assertEquals(id, active.habit.id)
        assertEquals("tuning_up", active.habit.status)
        assertEquals(30, active.habit.attemptTrackLength)
        assertEquals(2, active.habit.currentAttempt)
        assertEquals(1, active.habit.trophyAttempt)
        assertFalse(repo.canStartNew())
    }

    @Test fun start_tune_up_is_rejected_when_the_forming_slot_is_busy() = runTest {
        repo.createHabit("A", zone)
        val id = repo.observeActive().first()!!.habit.id
        repeat(100) { repo.markTodayDone(id); clock.advanceDays(1) }
        repo.reportSlip(id)
        repo.createHabit("B", zone)

        try {
            repo.startTuneUp(id)
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected — one habit at a time
        }
        assertEquals("mastered", db.habitDao().byId(id)!!.status)
    }

    @Test fun graduating_a_tune_up_returns_to_mastered_and_keeps_the_original_trophy() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repeat(100) { repo.markTodayDone(id); clock.advanceDays(1) }
        repo.reportSlip(id)
        repo.startTuneUp(id)
        repeat(30) { repo.markTodayDone(id); clock.advanceDays(1) }

        val h = db.habitDao().byId(id)!!
        assertEquals("mastered", h.status)
        assertFalse(h.slipped)
        assertEquals(1, h.trophyAttempt)          // still the original 100-day board
        assertEquals(2, h.currentAttempt)
        assertFalse(h.graduationAcknowledged)      // tune-up graduation also shows the trophy screen
        assertNull(h.failureReason)
        assertNull(h.failedOnDay)

        val tv = repo.trophyView(id)!!
        assertEquals(
            100,
            tv.logs.count { it.status == com.manasm.habit100.domain.DayStatus.DONE },
        )
        assertEquals(100, tv.trackLength)
        assertTrue(tv.isTuneUp)
        assertNull(repo.observeActive().first())   // slot free again
    }

    @Test fun failing_a_tune_up_returns_to_mastered_with_no_stale_failure() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repeat(100) { repo.markTodayDone(id); clock.advanceDays(1) }
        repo.reportSlip(id)
        repo.startTuneUp(id)
        repo.markTodayDone(id)                     // tune-up day 1
        clock.advanceDays(3)                       // days 2, 3 missed -> two in a row
        repo.applyTransition(id)

        val h = db.habitDao().byId(id)!!
        assertEquals("mastered", h.status)         // a failed tune-up returns to mastered
        assertTrue(h.slipped)                      // still flagged - user can try another tune-up
        assertNull(h.failureReason)                // THE FIX - no stale failure on a mastered habit
        assertNull(h.failedOnDay)
        assertEquals(2, h.currentAttempt)
        assertEquals(1, h.trophyAttempt)
        assertTrue(h.graduationAcknowledged)      // no stale graduation screen after a failed tune-up

        val rows = repo.observeMastered().first()
        val row = rows.single { it.habit.id == id }
        assertTrue(row.slipped)
        assertTrue(row.slotFree)                   // tune-up eligible again
    }

    @Test fun a_second_tune_up_can_start_after_a_failed_one() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repeat(100) { repo.markTodayDone(id); clock.advanceDays(1) }
        repo.reportSlip(id)
        repo.startTuneUp(id)
        repo.markTodayDone(id)
        clock.advanceDays(3)
        repo.applyTransition(id)

        repo.startTuneUp(id)

        val h = db.habitDao().byId(id)!!
        assertEquals("tuning_up", h.status)
        assertEquals(3, h.currentAttempt)
        assertEquals(30, h.attemptTrackLength)
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

    @Test fun undo_mark_day_returns_the_markable_day_to_pending() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repo.markTodayDone(id)
        assertTrue(repo.observeActive().first()!!.snapshot.todayMarkedDone)

        repo.undoMarkDay(id)

        val a = repo.observeActive().first()!!
        assertFalse(a.snapshot.todayMarkedDone)
        assertTrue(a.snapshot.canMarkToday)
        assertEquals(0, a.snapshot.doneCount)
        assertEquals(0, db.dayLogDao().forAttempt(id, 1).size)
    }

    @Test fun undo_mark_day_is_rejected_when_the_markable_day_is_not_marked() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        try {
            repo.undoMarkDay(id)
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test fun undo_mark_day_never_touches_a_finalized_past_day() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repo.markTodayDone(id)          // day 1 done
        clock.advanceDays(1)           // now day 2, noon -> day 1 is finalized
        repo.markTodayDone(id)          // day 2 done
        repo.undoMarkDay(id)            // undoes day 2 only

        val logs = db.dayLogDao().forAttempt(id, 1)
        assertEquals(listOf(1), logs.map { it.dayNumber })
        assertEquals("done", logs.single().status)
    }

    @Test fun a_grace_day_mark_can_be_undone_while_the_window_is_open() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repo.markTodayDone(id)          // day 1 done
        // day 3 at 08:00 -> day 2 is inside its grace window
        clock.instant = LocalDate.of(2026, 1, 3).atTime(8, 0).atZone(zone).toInstant()
        repo.markTodayDone(id)          // marks the grace day (day 2)
        assertEquals(2, db.dayLogDao().forAttempt(id, 1).size)

        repo.undoMarkDay(id)            // takes back the grace-day mark

        assertEquals(listOf(1), db.dayLogDao().forAttempt(id, 1).map { it.dayNumber })
        assertTrue(repo.observeActive().first()!!.snapshot.canMarkToday)
    }
}
