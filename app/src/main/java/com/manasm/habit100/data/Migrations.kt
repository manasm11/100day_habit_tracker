package com.manasm.habit100.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object HabitMigrations {

    /** v2 adds the optional habit-target columns (§14). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE habits ADD COLUMN targetKind TEXT")
            db.execSQL("ALTER TABLE habits ADD COLUMN targetSeconds INTEGER")
            db.execSQL("ALTER TABLE habits ADD COLUMN targetReps INTEGER")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
