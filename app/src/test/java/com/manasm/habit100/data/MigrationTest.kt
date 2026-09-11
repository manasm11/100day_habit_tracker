package com.manasm.habit100.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.manasm.habit100.domain.HabitKind
import com.manasm.habit100.domain.HabitTarget
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val seeded = mutableListOf<String>()

    @After fun cleanup() {
        seeded.forEach { ctx.deleteDatabase(it) }
        seeded.clear()
    }

    // ---- Historical schemas, copied from app/schemas/<db>/N.json, so Room's validation of the
    // current version has a real starting point to upgrade from.

    private val commonTables = listOf(
        "CREATE TABLE IF NOT EXISTS `day_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`habitId` INTEGER NOT NULL, `attempt` INTEGER NOT NULL, `dayNumber` INTEGER NOT NULL, " +
            "`logDate` TEXT NOT NULL, `status` TEXT NOT NULL, `markedAt` INTEGER NOT NULL, " +
            "FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_day_logs_habitId_attempt_dayNumber` ON `day_logs` (`habitId`, `attempt`, `dayNumber`)",
        "CREATE INDEX IF NOT EXISTS `index_day_logs_habitId_attempt` ON `day_logs` (`habitId`, `attempt`)",
        "CREATE TABLE IF NOT EXISTS `maintenance_checkins` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`habitId` INTEGER NOT NULL, `period` TEXT NOT NULL, `status` TEXT NOT NULL, `checkedAt` INTEGER NOT NULL, " +
            "FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_maintenance_checkins_habitId_period` ON `maintenance_checkins` (`habitId`, `period`)",
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
    )

    private val v1HabitsTable =
        "CREATE TABLE IF NOT EXISTS `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `timeZoneId` TEXT NOT NULL, `status` TEXT NOT NULL, " +
            "`currentAttempt` INTEGER NOT NULL, `attemptStartDate` TEXT NOT NULL, " +
            "`attemptTrackLength` INTEGER NOT NULL, `trophyAttempt` INTEGER, `slipped` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `graduatedAt` INTEGER, `failureReason` TEXT, " +
            "`failedOnDay` INTEGER, `graduationAcknowledged` INTEGER NOT NULL)"

    private val v2HabitsTable = v1HabitsTable.dropLast(1) +
        ", `targetKind` TEXT, `targetSeconds` INTEGER, `targetReps` INTEGER)"

    private fun schemaFor(version: Int): List<String> {
        val (habits, hash) = when (version) {
            1 -> v1HabitsTable to "56bc0411f33dec89f2ac17d1c2ccef03"
            2 -> v2HabitsTable to "9de1691cca22eb97bc35efa977b6e4c7"
            else -> error("no historical schema for version $version")
        }
        return listOf(habits) + commonTables +
            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '$hash')"
    }

    /** Creates [name] as a real database at [version] holding one forming habit called [habitName]. */
    private fun seedDatabase(name: String, version: Int, habitName: String) {
        ctx.deleteDatabase(name)
        seeded += name
        val schema = schemaFor(version)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) { schema.forEach(db::execSQL) }
                    override fun onUpgrade(db: SupportSQLiteDatabase, o: Int, n: Int) = Unit
                },
            ).build(),
        )
        helper.writableDatabase.execSQL(
            "INSERT INTO habits (name,timeZoneId,status,currentAttempt,attemptStartDate," +
                "attemptTrackLength,slipped,createdAt,graduationAcknowledged) " +
                "VALUES ('$habitName','UTC','forming',1,'2026-01-01',100,0,0,1)",
        )
        helper.close()
    }

    /** Opens [name] the way Room does in tests, with every migration registered. */
    private fun openMigrated(name: String): HabitDatabase =
        Room.databaseBuilder(ctx, HabitDatabase::class.java, name)
            .addMigrations(*HabitMigrations.ALL)
            .allowMainThreadQueries()
            .build()

    @Test fun migration_1_2_adds_target_columns_and_keeps_existing_rows() = runTest {
        seedDatabase("migration-1.db", version = 1, habitName = "Read")
        val db = openMigrated("migration-1.db")

        val row = db.habitDao().activeOnce()!!
        assertEquals("Read", row.name)
        assertNull(row.targetKind)
        assertNull(row.targetSeconds)
        assertNull(row.targetReps)
        assertEquals(HabitTarget.None, row.target())

        db.close()
    }

    @Test fun migration_2_3_adds_the_kind_column_and_existing_habits_read_as_build() = runTest {
        // The upgrade every v1.2.0 user takes. Habits that predate §15 have no kind, and a
        // null kind must read as BUILD — they were all build habits.
        seedDatabase("migration-2.db", version = 2, habitName = "Meditate")
        val db = openMigrated("migration-2.db")

        val row = db.habitDao().activeOnce()!!
        assertEquals("Meditate", row.name)
        assertNull(row.kind)
        assertEquals(HabitKind.BUILD, row.habitKind())

        db.close()
    }

    @Test fun a_quit_habit_survives_a_round_trip_through_the_column() = runTest {
        seedDatabase("kind-roundtrip.db", version = 2, habitName = "Old")
        val db = openMigrated("kind-roundtrip.db")

        val row = db.habitDao().activeOnce()!!
        db.habitDao().update(row.copy(kind = HabitKind.QUIT.toColumn()))
        assertEquals(HabitKind.QUIT, db.habitDao().activeOnce()!!.habitKind())

        db.close()
    }

    @Test fun the_production_builder_upgrades_every_shipped_version_without_crashing() {
        // Guards against dropping .addMigrations() from HabitDatabase.build() — without it an
        // upgrade throws on open (there is no fallbackToDestructiveMigration anywhere).
        assertTrue(HabitMigrations.ALL.any { it.startVersion == 1 && it.endVersion == 2 })
        assertTrue(HabitMigrations.ALL.any { it.startVersion == 2 && it.endVersion == 3 })

        for ((version, name) in listOf(1 to "prod-from-v1.db", 2 to "prod-from-v2.db")) {
            seedDatabase(name, version = version, habitName = "Legacy")
            val db = HabitDatabase.build(ctx, name)
            val row = runBlocking { db.habitDao().activeOnce() }!!
            assertEquals("upgrading from v$version lost the habit", "Legacy", row.name)
            assertEquals(HabitTarget.None, row.target())
            assertEquals(HabitKind.BUILD, row.habitKind())
            db.close()
        }
    }
}
