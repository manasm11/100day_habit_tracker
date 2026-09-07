package com.manasm.habit100.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.manasm.habit100.data.HabitDatabase
import com.manasm.habit100.data.HabitDatabaseTestHooks
import com.manasm.habit100.data.HabitRepository
import com.manasm.habit100.support.FakeClock
import com.manasm.habit100.ui.shelf.Badge
import com.manasm.habit100.ui.shelf.ShelfViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ShelfViewModelTest {
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

    private suspend fun graduate(name: String): Long {
        repo.createHabit(name, zone)
        val id = repo.observeActive().first()!!.habit.id
        repeat(100) { repo.markTodayDone(id); clock.advanceDays(1) }
        return id
    }

    @Test fun mastered_habit_shows_a_check_in_row_and_no_forming_habit() = runTest {
        val id = graduate("Read")

        val rows = repo.let { ShelfViewModel(it) }.let { vm ->
            val r = vm.rows.first { it.isNotEmpty() }
            assertEquals(1, r.size)
            assertEquals(id, r[0].id)
            assertEquals(Badge.CHECK_IN, r[0].badge)
            assertEquals(1, vm.masteredCount.first { it == 1 })
            assertNull(vm.formingNow.first())
            assertFalse(r[0].canTuneUp)
            r
        }
        assertEquals(1, rows.size)
    }

    @Test fun starting_a_second_habit_lights_up_forming_now_and_blocks_tune_up() = runTest {
        graduate("Read")
        val vm = ShelfViewModel(repo)
        vm.rows.first { it.isNotEmpty() }

        repo.createHabit("Run", zone)

        val forming = vm.formingNow.first { it != null }!!
        assertEquals("Run", forming.name)
        assertEquals(1, forming.dayNumber)
        assertEquals(100, forming.trackLength)

        val row = vm.rows.first { it.isNotEmpty() }[0]
        assertFalse(row.canTuneUp)
    }

    @Test fun confirming_a_check_in_flips_the_badge_to_going_strong() = runTest {
        val id = graduate("Read")
        val vm = ShelfViewModel(repo)
        assertEquals(Badge.CHECK_IN, vm.rows.first { it.isNotEmpty() }[0].badge)

        vm.confirm(id)

        val row = vm.rows.first { it.isNotEmpty() && it[0].badge != Badge.CHECK_IN }[0]
        assertEquals(Badge.GOING_STRONG, row.badge)
    }
}
