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

    /**
     * v3 adds the habit kind (§15). Nullable with no default, so existing rows keep a NULL
     * that [com.manasm.habit100.domain.HabitKind.fromColumn] reads as BUILD — which is what
     * every habit written before §15 was.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE habits ADD COLUMN kind TEXT")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
