package com.manasm.habit100.ui

import com.manasm.habit100.data.HabitDatabaseTestHooks
import com.manasm.habit100.data.HabitDatabaseTestHooks.TestDb
import com.manasm.habit100.data.HabitRepository
import com.manasm.habit100.support.FakeClock
import com.manasm.habit100.support.clearForTest
import com.manasm.habit100.ui.tracker.TrackerUiState
import com.manasm.habit100.ui.tracker.TrackerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNot
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
class TrackerViewModelTest {
    private val zone = ZoneId.of("America/New_York")
    private fun clockAt(d: LocalDate) = FakeClock(d.atTime(12, 0).atZone(zone).toInstant())

    private var db: TestDb? = null
    private var vm: TrackerViewModel? = null

    @Before fun setMain() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After fun tearDown() {
        // Cancel the ViewModel's long-lived viewModelScope collectors BEFORE closing the
        // database, otherwise a re-query races the close and Robolectric's CloseGuard logs
        // a spurious "resource never released" stack trace into the suite output.
        vm?.clearForTest()
        vm = null
        db?.close()
        db = null
        Dispatchers.resetMain()
    }

    private fun setup(clock: FakeClock): Pair<HabitRepository, TrackerViewModel> {
        val testDb = HabitDatabaseTestHooks.testDb()
        db = testDb
        val d = testDb.db
        val repo = HabitRepository(d, d.habitDao(), d.dayLogDao(), d.checkinDao(), clock)
        return repo to TrackerViewModel(repo, clock, null, null).also { vm = it }
    }

    private suspend fun TrackerViewModel.settled(): TrackerUiState =
        state.filterNot { it is TrackerUiState.Loading }.first()

    @Test fun empty_when_no_habit() = runTest {
        val (_, vm) = setup(clockAt(LocalDate.of(2026, 1, 1)))
        assertTrue(vm.settled() is TrackerUiState.Empty)
    }

    @Test fun forming_after_create_then_mark() = runTest {
        val (repo, vm) = setup(clockAt(LocalDate.of(2026, 1, 1)))
        repo.createHabit("Read", zone)
        val s1 = vm.settled() as TrackerUiState.Forming
        assertEquals(1, s1.dayNumber)
        assertEquals(100, s1.trackLength)
        assertTrue(s1.canMarkToday)
        vm.markDone()
        val s2 = vm.state.first {
            it is TrackerUiState.Forming && !(it as TrackerUiState.Forming).canMarkToday
        } as TrackerUiState.Forming
        assertEquals(1, s2.doneCount)
        assertTrue(s2.alreadyDoneToday)
    }

    @Test fun amber_at_risk_after_missed_yesterday() = runTest {
        val clock = clockAt(LocalDate.of(2026, 1, 1))
        val (repo, vm) = setup(clock)
        repo.createHabit("Read", zone)
        vm.markDone() // day 1 done
        vm.state.first { it is TrackerUiState.Forming && (it as TrackerUiState.Forming).doneCount == 1 }
        clock.advanceDays(2) // day 2 missed, now day 3
        vm.refresh() // Ruling 3: no DB write, re-derive against current clock
        val s = vm.state.first {
            it is TrackerUiState.Forming && (it as TrackerUiState.Forming).atRisk
        } as TrackerUiState.Forming
        assertTrue(s.atRisk)
        assertEquals(3, s.dayNumber)
    }

    @Test fun graduated_state_routed_via_acknowledgement_flag() = runTest {
        val clock = clockAt(LocalDate.of(2026, 1, 1))
        val (repo, vm) = setup(clock)
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repeat(99) { repo.markTodayDone(id); clock.advanceDays(1) }
        // day 100, not yet marked -> still forming
        assertTrue(vm.settled() is TrackerUiState.Forming)
        vm.markDone() // day 100 done -> applyTransition -> mastered + unacknowledged
        val g = vm.state.first { it is TrackerUiState.Graduated } as TrackerUiState.Graduated
        assertEquals(id, g.habitId)
    }

    @Test fun failed_then_restart_returns_to_forming_day_one() = runTest {
        val clock = clockAt(LocalDate.of(2026, 1, 1))
        val (repo, vm) = setup(clock)
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repo.markTodayDone(id)   // day 1 done
        clock.advanceDays(3)     // days 2,3 missed -> fail on day 3; now day 4
        vm.refresh()             // Ruling 3
        val f = vm.state.first { it is TrackerUiState.Failed } as TrackerUiState.Failed
        assertEquals("two misses in a row", f.reason)
        assertEquals(3, f.failedOnDay)
        assertEquals(id, f.habitId)

        vm.restart()
        val r = vm.state.first { it is TrackerUiState.Forming } as TrackerUiState.Forming
        assertEquals(1, r.dayNumber)
        assertEquals(0, r.doneCount)
    }

    @Test fun failed_then_abandon_returns_to_empty() = runTest {
        val clock = clockAt(LocalDate.of(2026, 1, 1))
        val (repo, vm) = setup(clock)
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repo.markTodayDone(id)
        clock.advanceDays(3)
        vm.refresh()
        vm.state.first { it is TrackerUiState.Failed }
        vm.abandon()
        assertTrue(vm.state.first { it is TrackerUiState.Empty } is TrackerUiState.Empty)
    }

    @Test fun grace_window_shows_the_prior_day_as_still_markable() = runTest {
        val clock = clockAt(LocalDate.of(2026, 1, 1))
        val (repo, vm) = setup(clock)
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repo.markTodayDone(id) // day 1 done
        // day 3 at 08:00: day 2 was never marked, so it is still inside its grace window
        clock.instant = LocalDate.of(2026, 1, 3).atTime(8, 0).atZone(zone).toInstant()
        vm.refresh()
        val s = vm.state.first {
            it is TrackerUiState.Forming && (it as TrackerUiState.Forming).isGraceDay
        } as TrackerUiState.Forming
        assertEquals(2, s.dayNumber)
        assertTrue(s.canMarkToday)
        assertNotNull(s.graceDeadlineText)
    }

    @Test fun undo_is_offered_after_marking_and_clears_the_day() = runTest {
        val (repo, vm) = setup(clockAt(LocalDate.of(2026, 1, 1)))
        repo.createHabit("Read", zone)
        val s1 = vm.settled() as TrackerUiState.Forming
        assertFalse(s1.canUndo)

        vm.markDone()
        val s2 = vm.state.first {
            it is TrackerUiState.Forming && (it as TrackerUiState.Forming).canUndo
        } as TrackerUiState.Forming
        assertTrue(s2.alreadyDoneToday)

        vm.undoMark()
        val s3 = vm.state.first {
            it is TrackerUiState.Forming && (it as TrackerUiState.Forming).canMarkToday
        } as TrackerUiState.Forming
        assertEquals(0, s3.doneCount)
        assertFalse(s3.canUndo)
    }
}
