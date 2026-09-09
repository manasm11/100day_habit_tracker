package com.manasm.habit100.ui

import com.manasm.habit100.data.HabitDatabaseTestHooks
import com.manasm.habit100.data.HabitDatabaseTestHooks.TestDb
import com.manasm.habit100.data.HabitRepository
import com.manasm.habit100.domain.HabitTarget
import com.manasm.habit100.support.FakeClock
import com.manasm.habit100.support.clearForTest
import com.manasm.habit100.ui.target.TargetUi
import com.manasm.habit100.ui.target.TargetViewModel
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
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TargetViewModelTest {
    private val zone = ZoneId.of("America/New_York")
    private val clock = FakeClock(LocalDate.of(2026, 1, 1).atTime(12, 0).atZone(zone).toInstant())
    private var db: TestDb? = null
    private var vm: TargetViewModel? = null

    @Before fun setMain() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() { vm?.clearForTest(); db?.close(); Dispatchers.resetMain() }

    private suspend fun setup(target: HabitTarget): Pair<HabitRepository, Long> {
        val testDb = HabitDatabaseTestHooks.testDb().also { db = it }
        val d = testDb.db
        val repo = HabitRepository(d, d.habitDao(), d.dayLogDao(), d.checkinDao(), clock)
        repo.createHabit("H", zone, target)
        val id = repo.observeActive().first()!!.habit.id
        return repo to id
    }

    private suspend fun TargetViewModel.settled() = ui.filterNot { it is TargetUi.Loading }.first()

    @Test fun reps_counter_auto_marks_the_day_when_the_target_is_reached() = runTest {
        val (repo, id) = setup(HabitTarget.Reps(3))
        vm = TargetViewModel(repo, clock, id)
        val v = vm!!
        v.settled() // wait for load

        v.increment(); v.increment()
        assertEquals(2, (v.ui.first() as TargetUi.Reps).count)
        assertEquals(0, repo.observeActive().first()!!.snapshot.doneCount)

        v.increment() // hits 3
        val done = v.ui.first { it is TargetUi.Reps && (it as TargetUi.Reps).done } as TargetUi.Reps
        assertEquals(3, done.count)
        assertEquals(1, repo.observeActive().first { it!!.snapshot.doneCount == 1 }!!.snapshot.doneCount)
    }

    @Test fun reps_decrement_stops_at_zero() = runTest {
        val (repo, id) = setup(HabitTarget.Reps(5))
        vm = TargetViewModel(repo, clock, id)
        val v = vm!!
        v.settled()
        v.decrement(); v.decrement()
        assertEquals(0, (v.ui.first() as TargetUi.Reps).count)
    }

    @Test fun duration_timer_marks_the_day_once_it_runs_out() = runTest {
        val (repo, id) = setup(HabitTarget.Duration(60))
        vm = TargetViewModel(repo, clock, id)
        val v = vm!!
        v.settled()
        v.start()
        val running = v.ui.first { it is TargetUi.Duration && (it as TargetUi.Duration).running } as TargetUi.Duration
        assertEquals(60, running.remainingSeconds)

        clock.advance(Duration.ofSeconds(61))
        v.tick()

        val done = v.ui.first { it is TargetUi.Duration && (it as TargetUi.Duration).done } as TargetUi.Duration
        assertEquals(0, done.remainingSeconds)
        // the auto-mark is an async side effect — wait for it to land
        assertEquals(1, repo.observeActive().first { it!!.snapshot.doneCount == 1 }!!.snapshot.doneCount)
    }

    @Test fun duration_timer_pause_freezes_the_remaining_time() = runTest {
        val (repo, id) = setup(HabitTarget.Duration(100))
        vm = TargetViewModel(repo, clock, id)
        val v = vm!!
        v.settled()
        v.start()
        clock.advance(Duration.ofSeconds(30))
        v.pause()
        clock.advance(Duration.ofSeconds(45)) // time passes while paused
        v.tick()
        val paused = v.ui.first() as TargetUi.Duration
        assertFalse(paused.running)
        assertEquals(70, paused.remainingSeconds)
        assertEquals(0, repo.observeActive().first()!!.snapshot.doneCount)
    }

    @Test fun already_marked_today_is_shown_without_re_marking() = runTest {
        val (repo, id) = setup(HabitTarget.Reps(3))
        repo.markTodayDone(id)
        vm = TargetViewModel(repo, clock, id)
        val s = vm!!.settled()
        assertTrue((s as TargetUi.Reps).alreadyDone)
    }
}
