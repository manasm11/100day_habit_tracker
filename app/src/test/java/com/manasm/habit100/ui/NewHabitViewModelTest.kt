package com.manasm.habit100.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.manasm.habit100.data.HabitDatabase
import com.manasm.habit100.data.HabitDatabaseTestHooks
import com.manasm.habit100.data.HabitRepository
import com.manasm.habit100.support.FakeClock
import com.manasm.habit100.ui.newhabit.NewHabitViewModel
import kotlinx.coroutines.test.runTest
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
    private fun repo(): HabitRepository {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HabitDatabase::class.java,
        ).allowMainThreadQueries().addCallback(HabitDatabaseTestHooks.callback()).build()
        return HabitRepository(
            db,
            db.habitDao(),
            db.dayLogDao(),
            db.checkinDao(),
            FakeClock(Instant.parse("2026-01-01T09:00:00Z")),
        )
    }

    @Test fun blank_name_cannot_create() {
        val vm = NewHabitViewModel(repo())
        vm.onNameChange("   ")
        assertFalse(vm.canCreateEnabled.value)
        vm.onNameChange("Read")
        assertTrue(vm.canCreateEnabled.value)
    }

    @Test fun create_succeeds_and_blocks_second() = runTest {
        val r = repo()
        val vm = NewHabitViewModel(r)
        vm.onNameChange("Read")
        assertTrue(vm.create(ZoneId.of("America/New_York")))
        val vm2 = NewHabitViewModel(r)
        vm2.onNameChange("Run")
        assertFalse(vm2.create(ZoneId.of("America/New_York")))
    }
}
