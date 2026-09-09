package com.manasm.habit100.ui

import com.manasm.habit100.data.HabitDatabaseTestHooks
import com.manasm.habit100.data.HabitRepository
import com.manasm.habit100.support.FakeClock
import com.manasm.habit100.support.clearForTest
import com.manasm.habit100.ui.newhabit.NewHabitViewModel
import kotlinx.coroutines.test.runTest
import org.junit.After
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewHabitViewModelTest {
    private val dbs = mutableListOf<HabitDatabaseTestHooks.TestDb>()
    private val vms = mutableListOf<NewHabitViewModel>()

    @After fun tearDown() {
        vms.forEach { it.clearForTest() }
        dbs.forEach { it.close() }
    }

    private fun repo(): HabitRepository {
        val db = HabitDatabaseTestHooks.testDb().also { dbs += it }.db
        return HabitRepository(
            db,
            db.habitDao(),
            db.dayLogDao(),
            db.checkinDao(),
            FakeClock(Instant.parse("2026-01-01T09:00:00Z")),
        )
    }

    private fun newHabitVm(r: HabitRepository) = NewHabitViewModel(r).also { vms += it }

    @Test fun blank_name_cannot_create() {
        val vm = newHabitVm(repo())
        vm.onNameChange("   ")
        assertFalse(vm.canCreateEnabled.value)
        vm.onNameChange("Read")
        assertTrue(vm.canCreateEnabled.value)
    }

    @Test fun create_succeeds_and_blocks_second() = runTest {
        val r = repo()
        val vm = newHabitVm(r)
        vm.onNameChange("Read")
        assertTrue(vm.create(ZoneId.of("America/New_York")))
        val vm2 = newHabitVm(r)
        vm2.onNameChange("Run")
        assertFalse(vm2.create(ZoneId.of("America/New_York")))
    }

    @Test fun a_duration_target_needs_a_positive_minute_count() {
        val vm = newHabitVm(repo())
        vm.onNameChange("Meditate")
        vm.onTargetChoice(NewHabitViewModel.TargetChoice.DURATION)
        assertFalse(vm.canCreateEnabled.value)          // no minutes yet
        vm.onTargetValueChange("0")
        assertFalse(vm.canCreateEnabled.value)
        vm.onTargetValueChange("10")
        assertTrue(vm.canCreateEnabled.value)
    }

    @Test fun creates_a_duration_habit_from_minutes() = runTest {
        val r = repo()
        val vm = newHabitVm(r)
        vm.onNameChange("Meditate")
        vm.onTargetChoice(NewHabitViewModel.TargetChoice.DURATION)
        vm.onTargetValueChange("10")
        assertTrue(vm.create(ZoneId.of("America/New_York")))
        val h = r.observeActive().first()!!.habit
        assertEquals("duration", h.targetKind)
        assertEquals(600, h.targetSeconds)
    }

    @Test fun creates_a_reps_habit_from_a_count() = runTest {
        val r = repo()
        val vm = newHabitVm(r)
        vm.onNameChange("Pushups")
        vm.onTargetChoice(NewHabitViewModel.TargetChoice.REPS)
        vm.onTargetValueChange("25")
        assertTrue(vm.create(ZoneId.of("America/New_York")))
        val h = r.observeActive().first()!!.habit
        assertEquals(25, h.targetReps)
    }
}
