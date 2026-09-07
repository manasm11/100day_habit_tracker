package com.manasm.habit100.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.manasm.habit100.data.HabitDatabase
import com.manasm.habit100.data.HabitDatabaseTestHooks
import com.manasm.habit100.data.HabitRepository
import com.manasm.habit100.support.FakeClock
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
    private lateinit var db: HabitDatabase
    private lateinit var repo: HabitRepository
    private val clock =
        FakeClock(LocalDate.of(2026, 1, 1).atTime(9, 0).atZone(zone).toInstant())

    @Before fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), HabitDatabase::class.java,
        ).allowMainThreadQueries().addCallback(HabitDatabaseTestHooks.callback()).build()
        repo = HabitRepository(db, db.habitDao(), db.dayLogDao(), db.checkinDao(), clock)
    }

    @After fun tearDown() {
        db.close()
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

        val vm = GraduationViewModel(repo, id)
        val ui = vm.ui.filterNotNull().first()

        assertEquals("Read", ui.name)
        assertEquals(98, ui.daysDone)
        assertEquals(2, ui.missesUsed)
        assertEquals(100, ui.cells.size)
        // Longest run is day 41..100 = 60.
        assertEquals(60, ui.bestStreak)
    }

    @Test fun start_next_and_keep_going_both_acknowledge() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repeat(100) { repo.markTodayDone(id); clock.advanceDays(1) }

        val vm = GraduationViewModel(repo, id)
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
