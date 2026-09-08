package com.manasm.habit100.ui

import com.manasm.habit100.data.HabitDatabase
import com.manasm.habit100.data.HabitDatabaseTestHooks
import com.manasm.habit100.data.HabitRepository
import com.manasm.habit100.ui.CellState
import com.manasm.habit100.support.FakeClock
import com.manasm.habit100.support.clearForTest
import com.manasm.habit100.ui.graduation.GraduationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GraduationViewModelTest {
    private val zone = ZoneId.of("America/New_York")
    private lateinit var testDb: HabitDatabaseTestHooks.TestDb
    private val db: HabitDatabase get() = testDb.db
    private lateinit var repo: HabitRepository
    private val vms = mutableListOf<GraduationViewModel>()
    private val clock =
        FakeClock(LocalDate.of(2026, 1, 1).atTime(12, 0).atZone(zone).toInstant())

    private fun gradVm(id: Long) = GraduationViewModel(repo, id).also { vms += it }

    @Before fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        testDb = HabitDatabaseTestHooks.testDb()
        repo = HabitRepository(db, db.habitDao(), db.dayLogDao(), db.checkinDao(), clock)
    }

    @After fun tearDown() {
        vms.forEach { it.clearForTest() }
        vms.clear()
        testDb.close()
        Dispatchers.resetMain()
    }

    @Test fun graduation_ui_reflects_final_stats_with_two_misses() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        // Mark days 1..100, skipping day 10 and day 40 (two non-consecutive misses).
        repeat(100) { i ->
            val day = i + 1
            if (day != 10 && day != 40) repo.markTodayDone(id)
            clock.advanceDays(1)
        }
        assertEquals("mastered", db.habitDao().byId(id)!!.status)

        val vm = gradVm(id)
        val ui = vm.ui.filterNotNull().first()

        assertEquals("Read", ui.name)
        assertEquals(98, ui.daysDone)
        assertEquals(2, ui.missesUsed)
        assertEquals(100, ui.cells.size)
        assertEquals(2, ui.cells.count { it == CellState.MISSED })
        assertEquals(98, ui.cells.count { it == CellState.DONE })
        // Longest run is day 41..100 = 60.
        assertEquals(60, ui.bestStreak)
    }

    @Test fun tune_up_graduation_shows_the_original_100_day_trophy_board() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        // Original 100-day attempt: mark all but day 10 and day 40 (two non-consecutive misses).
        repeat(100) { i ->
            val day = i + 1
            if (day != 10 && day != 40) repo.markTodayDone(id)
            clock.advanceDays(1)
        }
        assertEquals("mastered", db.habitDao().byId(id)!!.status)

        repo.reportSlip(id)
        repo.startTuneUp(id)
        repeat(30) { repo.markTodayDone(id); clock.advanceDays(1) }
        assertEquals("mastered", db.habitDao().byId(id)!!.status)

        val vm = gradVm(id)
        val ui = vm.ui.filterNotNull().first()

        assertEquals(100, ui.trackLength)
        assertTrue(ui.isTuneUp)
        assertEquals(98, ui.daysDone)
        assertEquals(2, ui.missesUsed)
        assertEquals(100, ui.cells.size)
        assertEquals(2, ui.cells.count { it == CellState.MISSED })
        assertEquals(98, ui.cells.count { it == CellState.DONE })
    }

    @Test fun start_next_and_keep_going_both_acknowledge() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repeat(100) { repo.markTodayDone(id); clock.advanceDays(1) }

        val vm = gradVm(id)
        vm.ui.filterNotNull().first()

        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        vm.startNext { done.complete(Unit) }
        done.await()
        assertTrue(db.habitDao().byId(id)!!.graduationAcknowledged)

        // "Keep this one going" also acknowledges (idempotent).
        val done2 = kotlinx.coroutines.CompletableDeferred<Unit>()
        vm.keepGoing { done2.complete(Unit) }
        done2.await()
        assertTrue(db.habitDao().byId(id)!!.graduationAcknowledged)
    }
}
