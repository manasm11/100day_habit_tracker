package com.manasm.habit100.backup

import com.manasm.habit100.data.HabitDatabaseTestHooks
import com.manasm.habit100.data.HabitRepository
import com.manasm.habit100.data.habitKind
import com.manasm.habit100.domain.HabitKind
import com.manasm.habit100.support.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRepositoryTest {
    private val zone = ZoneId.of("America/New_York")
    private lateinit var testDb: HabitDatabaseTestHooks.TestDb
    private lateinit var repo: HabitRepository
    private lateinit var backup: BackupRepository
    private lateinit var clock: FakeClock

    private fun at(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant()

    @Before fun setup() {
        clock = FakeClock(at(LocalDate.of(2026, 1, 1)))
        testDb = HabitDatabaseTestHooks.testDb()
        val d = testDb.db
        repo = HabitRepository(d, d.habitDao(), d.dayLogDao(), d.checkinDao(), clock)
        backup = BackupRepository(d, d.habitDao(), d.dayLogDao(), d.checkinDao(), clock, "1.4.0")
    }

    @After fun tearDown() = testDb.close()

    /** Graduates a habit (100 days), leaving the forming slot free. */
    private suspend fun masterAHabit(name: String): Long {
        repo.createHabit(name, zone)
        val id = repo.observeActive().first()!!.habit.id
        repeat(100) { repo.markTodayDone(id); clock.advanceDays(1) }
        return id
    }

    // ---- Export

    @Test fun export_captures_mastered_habits_with_their_trophy_logs_and_checkins() = runTest {
        val id = masterAHabit("Read")
        repo.checkIn(id, strong = true)

        val file = backup.export()

        val h = file.habits.single()
        assertEquals("Read", h.name)
        assertEquals("mastered", h.status)
        assertEquals(1, h.trophyAttempt)
        assertNotNull("a graduated habit keeps its date", h.graduatedAt)
        assertEquals(100, h.dayLogs.size)
        assertEquals(100, h.dayLogs.count { it.status == "done" })
        assertEquals(1, h.checkins.size)
        assertEquals("strong", h.checkins.single().status)
        assertEquals(BackupCodec.FORMAT, file.format)
        assertEquals("1.4.0", file.appVersion)
    }

    @Test fun export_captures_every_habit_not_just_the_active_one() = runTest {
        masterAHabit("Read")                       // mastered
        repo.createHabit("Smoking", zone, kind = HabitKind.QUIT)
        val quitId = repo.observeActive().first()!!.habit.id
        repo.markTodaySlipped(quitId)              // forming, one slip

        val names = backup.export().habits.map { it.name }.toSet()
        assertEquals(setOf("Read", "Smoking"), names)
    }

    @Test fun export_keeps_a_quit_habits_kind_and_its_slip_rows() = runTest {
        repo.createHabit("Smoking", zone, kind = HabitKind.QUIT)
        val id = repo.observeActive().first()!!.habit.id
        repo.markTodaySlipped(id)

        val h = backup.export().habits.single()
        assertEquals("quit", h.kind)
        assertEquals(1, h.dayLogs.count { it.status == "missed" })
    }

    @Test fun an_empty_app_exports_an_empty_but_valid_backup() = runTest {
        val file = backup.export()
        assertTrue(file.habits.isEmpty())
        assertEquals(file, BackupCodec.decode(BackupCodec.encode(file)))
    }

    // ---- Restore

    @Test fun a_full_round_trip_restores_the_shelf_exactly() = runTest {
        val id = masterAHabit("Read")
        repo.checkIn(id, strong = true)
        repo.createHabit("Floss", zone)
        val before = backup.export()

        backup.restore(before)

        val after = backup.export()
        // Ids are regenerated on restore, so compare everything else.
        assertEquals(before.habits.map { it.name }, after.habits.map { it.name })
        assertEquals(before.habits.map { it.status }, after.habits.map { it.status })
        assertEquals(before.habits.map { it.dayLogs }, after.habits.map { it.dayLogs })
        assertEquals(before.habits.map { it.checkins }, after.habits.map { it.checkins })
        assertEquals(before.habits.map { it.trophyAttempt }, after.habits.map { it.trophyAttempt })
        assertEquals(1, testDb.db.habitDao().activeCount())
    }

    @Test fun restore_replaces_rather_than_merging() = runTest {
        masterAHabit("Read")
        val file = backup.export()

        // A different device state: one mastered habit with a different name.
        backup.restore(BackupFile(exportedAt = file.exportedAt, appVersion = "1.4.0", habits = emptyList()))
        assertTrue(backup.export().habits.isEmpty())

        masterAHabit("Meditate")
        backup.restore(file)

        assertEquals(listOf("Read"), backup.export().habits.map { it.name })
    }

    @Test fun a_backup_with_two_forming_habits_is_refused_and_changes_nothing() = runTest {
        masterAHabit("Read")
        val good = backup.export()
        val twoForming = good.copy(
            habits = listOf(
                good.habits.single().copy(name = "A", status = "forming", trophyAttempt = null),
                good.habits.single().copy(name = "B", status = "forming", trophyAttempt = null),
            ),
        )

        try {
            backup.restore(twoForming)
            throw AssertionError("expected the single-slot guard to reject this backup")
        } catch (e: BackupError) {
            assertTrue(e.message!!, e.message!!.contains("backup", ignoreCase = true))
        }

        // The transaction rolled back — the original habit is untouched.
        assertEquals(listOf("Read"), backup.export().habits.map { it.name })
    }

    @Test fun restoring_a_quit_habit_brings_back_its_kind() = runTest {
        repo.createHabit("Smoking", zone, kind = HabitKind.QUIT)
        val file = backup.export()

        backup.restore(BackupFile(exportedAt = file.exportedAt, appVersion = "1.4.0", habits = emptyList()))
        backup.restore(file)

        assertEquals(HabitKind.QUIT, repo.observeActive().first()!!.habit.habitKind())
    }

    // ---- Preview

    @Test fun the_preview_counts_the_shelf_and_names_the_forming_habit() = runTest {
        masterAHabit("Read")
        masterAHabit("Meditate")
        repo.createHabit("Smoking", zone, kind = HabitKind.QUIT)
        repo.markTodayDone(repo.observeActive().first()!!.habit.id)

        val p = backup.preview(backup.export())

        assertEquals(2, p.masteredCount)
        assertEquals("Smoking", p.formingName)
        assertEquals(HabitKind.QUIT, p.formingKind)
        assertEquals(0, p.daysStale)
        assertFalse("a backup taken just now costs nothing", p.formingWillEnd)
    }

    @Test fun a_stale_backup_warns_that_the_forming_attempt_will_end() = runTest {
        repo.createHabit("Read", zone)
        repo.markTodayDone(repo.observeActive().first()!!.habit.id)
        val file = backup.export()

        clock.advanceDays(63)
        val p = backup.preview(file)

        assertEquals(63, p.daysStale)
        assertTrue("63 unmarked days is two in a row many times over", p.formingWillEnd)
        assertEquals(0, p.masteredCount)
    }

    @Test fun a_stale_backup_does_not_endanger_the_mastered_shelf() = runTest {
        masterAHabit("Read")
        val file = backup.export()

        clock.advanceDays(400)
        backup.restore(file)

        // A trophy is a historical fact — it does not get re-derived against today.
        val h = backup.export().habits.single()
        assertEquals("mastered", h.status)
        assertEquals(100, h.dayLogs.count { it.status == "done" })
        assertEquals(1, p(h.trophyAttempt))
        assertNull("the shelf is intact, the forming slot is free", repo.observeActive().first())
    }

    @Test fun a_backup_with_no_forming_habit_previews_without_a_warning() = runTest {
        masterAHabit("Read")
        val p = backup.preview(backup.export())
        assertNull(p.formingName)
        assertFalse(p.formingWillEnd)
        assertEquals(1, p.masteredCount)
    }

    private fun p(v: Int?): Int = v ?: -1
}
