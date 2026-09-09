package com.manasm.habit100.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.manasm.habit100.domain.HabitTarget
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
    private val dbName = "migration-1-2.db"

    @After fun cleanup() = ctx.deleteDatabase(dbName).let { }

    // The exact v1 schema (from app/schemas/.../1.json) so Room's v2 validation has a real
    // starting point.
    private val v1Schema = listOf(
        "CREATE TABLE IF NOT EXISTS `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `timeZoneId` TEXT NOT NULL, `status` TEXT NOT NULL, " +
            "`currentAttempt` INTEGER NOT NULL, `attemptStartDate` TEXT NOT NULL, " +
            "`attemptTrackLength` INTEGER NOT NULL, `trophyAttempt` INTEGER, `slipped` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `graduatedAt` INTEGER, `failureReason` TEXT, " +
            "`failedOnDay` INTEGER, `graduationAcknowledged` INTEGER NOT NULL)",
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
        "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '56bc0411f33dec89f2ac17d1c2ccef03')",
    )

    @Test fun migration_1_2_adds_target_columns_and_keeps_existing_rows() = runTest {
        ctx.deleteDatabase(dbName)

        // A v1-shaped database with one habit row.
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(dbName).callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) { v1Schema.forEach(db::execSQL) }
                    override fun onUpgrade(db: SupportSQLiteDatabase, o: Int, n: Int) = Unit
                },
            ).build(),
        )
        helper.writableDatabase.execSQL(
            "INSERT INTO habits (name,timeZoneId,status,currentAttempt,attemptStartDate," +
                "attemptTrackLength,slipped,createdAt,graduationAcknowledged) " +
                "VALUES ('Read','UTC','forming',1,'2026-01-01',100,0,0,1)",
        )
        helper.close()

        // Reopen with Room v2 + the migration.
        val db = Room.databaseBuilder(ctx, HabitDatabase::class.java, dbName)
            .addMigrations(HabitMigrations.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        val row = db.habitDao().activeOnce()!!
        assertEquals("Read", row.name)
        assertNull(row.targetKind)
        assertNull(row.targetSeconds)
        assertNull(row.targetReps)
        assertEquals(HabitTarget.None, row.target())

        db.close()
    }

    @Test fun the_production_builder_upgrades_a_real_v1_database_without_crashing() {
        // Guards against dropping .addMigrations() from HabitDatabase.build() — without it a
        // v1 -> v2 upgrade throws on open (there is no fallbackToDestructiveMigration).
        assertTrue(HabitMigrations.ALL.any { it.startVersion == 1 && it.endVersion == 2 })

        val prodName = "prod-upgrade.db"
        ctx.deleteDatabase(prodName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(prodName).callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) { v1Schema.forEach(db::execSQL) }
                    override fun onUpgrade(db: SupportSQLiteDatabase, o: Int, n: Int) = Unit
                },
            ).build(),
        )
        helper.writableDatabase.execSQL(
            "INSERT INTO habits (name,timeZoneId,status,currentAttempt,attemptStartDate," +
                "attemptTrackLength,slipped,createdAt,graduationAcknowledged) " +
                "VALUES ('Legacy','UTC','forming',1,'2026-01-01',100,0,0,1)",
        )
        helper.close()

        val db = HabitDatabase.build(ctx, prodName)
        val row = kotlinx.coroutines.runBlocking { db.habitDao().activeOnce() }!!
        assertEquals("Legacy", row.name)
        assertEquals(HabitTarget.None, row.target())
        db.close()
        ctx.deleteDatabase(prodName)
    }
}
