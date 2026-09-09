package com.manasm.habit100.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HabitEntity::class, DayLogEntity::class, MaintenanceCheckinEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class HabitDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun dayLogDao(): DayLogDao
    abstract fun checkinDao(): CheckinDao

    companion object {
        private val CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(TriggerSql.INSERT_GUARD)
                db.execSQL(TriggerSql.UPDATE_GUARD)
            }
        }

        fun build(context: Context, name: String = "habit.db"): HabitDatabase =
            Room.databaseBuilder(context, HabitDatabase::class.java, name)
                .addCallback(CALLBACK)
                .addMigrations(*HabitMigrations.ALL)
                .build()
    }
}
