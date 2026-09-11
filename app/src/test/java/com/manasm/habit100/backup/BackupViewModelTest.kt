package com.manasm.habit100.backup

import com.manasm.habit100.data.HabitDatabaseTestHooks
import com.manasm.habit100.data.HabitRepository
import com.manasm.habit100.support.FakeClock
import com.manasm.habit100.support.clearForTest
import com.manasm.habit100.ui.backup.BackupUi
import com.manasm.habit100.ui.backup.BackupViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class BackupViewModelTest {
    private val zone = ZoneId.of("America/New_York")
    private lateinit var testDb: HabitDatabaseTestHooks.TestDb
    private lateinit var repo: HabitRepository
    private lateinit var backup: BackupRepository
    private lateinit var clock: FakeClock
    private var vm: BackupViewModel<String>? = null
    private val vms = mutableListOf<BackupViewModel<String>>()

    /** Stands in for the system file picker: one in-memory "file" per destination string. */
    private class FakeIo : BackupFileIo<String> {
        val files = mutableMapOf<String, String>()
        var failNext: String? = null

        override suspend fun write(destination: String, text: String) {
            failNext?.let { failNext = null; throw java.io.IOException(it) }
            files[destination] = text
        }

        override suspend fun read(destination: String): String {
            failNext?.let { failNext = null; throw java.io.IOException(it) }
            return files[destination] ?: throw java.io.IOException("no such file")
        }
    }

    private val io = FakeIo()

    @Before fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        clock = FakeClock(LocalDate.of(2026, 1, 1).atTime(12, 0).atZone(zone).toInstant())
        testDb = HabitDatabaseTestHooks.testDb()
        val d = testDb.db
        repo = HabitRepository(d, d.habitDao(), d.dayLogDao(), d.checkinDao(), clock)
        backup = BackupRepository(d, d.habitDao(), d.dayLogDao(), d.checkinDao(), clock, "1.4.0")
    }

    @After fun tearDown() {
        vms.forEach { it.clearForTest() }
        vms.clear()
        vm = null
        testDb.close()
        Dispatchers.resetMain()
    }

    private fun newVm() = BackupViewModel(backup, io, clock).also { vms += it; vm = it }

    /** Waits for the view model to leave [BackupUi.Working], whatever the outcome. */
    private suspend fun BackupViewModel<String>.settle(): BackupUi = ui.first { it !is BackupUi.Working }

    @Test fun the_suggested_filename_carries_the_date() {
        assertEquals("100-day-habits-2026-01-01.json", newVm().suggestedFileName())
    }

    @Test fun exporting_writes_the_file_and_reports_what_went_into_it() = runTest {
        repo.createHabit("Read", zone)
        val vm = newVm()

        vm.exportTo("out.json")

        val done = vm.settle() as BackupUi.Exported
        assertEquals(1, done.habitCount)
        assertTrue(io.files.getValue("out.json").contains("\"name\": \"Read\""))
    }

    @Test fun a_write_that_fails_surfaces_an_error_rather_than_claiming_success() = runTest {
        repo.createHabit("Read", zone)
        val vm = newVm()
        io.failNext = "disk full"

        vm.exportTo("out.json")

        val e = vm.settle() as BackupUi.Error
        assertTrue(e.message, e.message.contains("couldn't be saved", ignoreCase = true))
    }

    @Test fun choosing_a_backup_shows_what_it_would_do_before_touching_anything() = runTest {
        repo.createHabit("Read", zone)
        repo.markTodayDone(repo.observeActive().first()!!.habit.id)
        newVm().let { it.exportTo("out.json"); assertTrue("${it.settle()}", it.settle() is BackupUi.Exported) }

        val vm2 = newVm()
        vm2.loadForRestore("out.json")

        val p = vm2.settle() as BackupUi.ConfirmRestore
        assertEquals("Read", p.preview.formingName)
        assertEquals(1, p.preview.habitCount)
        // Still untouched: nothing is written until the user confirms.
        assertEquals(1, backup.export().habits.size)
    }

    @Test fun confirming_the_restore_replaces_the_database() = runTest {
        repo.createHabit("Read", zone)
        newVm().let { it.exportTo("out.json"); assertTrue("${it.settle()}", it.settle() is BackupUi.Exported) }

        // Device moves on: the habit is abandoned and a different one started.
        testDb.db.habitDao().deleteAll()
        repo.createHabit("Something else", zone)

        val vm2 = newVm()
        vm2.loadForRestore("out.json")
        assertTrue("${vm2.settle()}", vm2.settle() is BackupUi.ConfirmRestore)
        vm2.confirmRestore()

        val done = vm2.settle() as BackupUi.Restored
        assertEquals(1, done.habitCount)
        assertEquals(listOf("Read"), backup.export().habits.map { it.name })
    }

    @Test fun cancelling_the_restore_leaves_the_database_alone() = runTest {
        repo.createHabit("Read", zone)
        newVm().let { it.exportTo("out.json"); assertTrue("${it.settle()}", it.settle() is BackupUi.Exported) }

        testDb.db.habitDao().deleteAll()
        repo.createHabit("Something else", zone)

        val vm2 = newVm()
        vm2.loadForRestore("out.json")
        assertTrue("${vm2.settle()}", vm2.settle() is BackupUi.ConfirmRestore)
        vm2.cancelRestore()

        assertTrue(vm2.ui.first() is BackupUi.Idle)
        assertEquals(listOf("Something else"), backup.export().habits.map { it.name })
    }

    @Test fun a_file_that_is_not_a_backup_is_rejected_with_the_codecs_own_words() = runTest {
        io.files["junk.json"] = "this is my shopping list"
        val vm = newVm()

        vm.loadForRestore("junk.json")

        val e = vm.settle() as BackupUi.Error
        assertTrue(e.message, e.message.contains("isn't a 100 Day Habit Tracker backup"))
    }

    @Test fun a_backup_from_a_newer_app_is_rejected_by_name() = runTest {
        repo.createHabit("Read", zone)
        io.files["future.json"] = BackupCodec.encode(backup.export()).replace(
            "\"format\": ${BackupCodec.FORMAT}",
            "\"format\": ${BackupCodec.FORMAT + 1}",
        )
        val vm = newVm()

        vm.loadForRestore("future.json")

        val e = vm.settle() as BackupUi.Error
        assertTrue(e.message, e.message.contains("newer version"))
    }

    @Test fun dismissing_an_error_returns_to_idle() = runTest {
        io.files["junk.json"] = "nope"
        val vm = newVm()
        vm.loadForRestore("junk.json")
        assertTrue(vm.settle() is BackupUi.Error)

        vm.dismiss()

        assertTrue(vm.ui.first() is BackupUi.Idle)
    }
}
