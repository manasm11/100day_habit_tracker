package com.manasm.habit100.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

object HabitDatabaseTestHooks {
    fun callback(): RoomDatabase.Callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(TriggerSql.INSERT_GUARD)
            db.execSQL(TriggerSql.UPDATE_GUARD)
        }
    }
}
