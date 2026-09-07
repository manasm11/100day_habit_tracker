package com.manasm.habit100.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object HabitDatabaseTestHooks {
    fun callback(): RoomDatabase.Callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(TriggerSql.INSERT_GUARD)
            db.execSQL(TriggerSql.UPDATE_GUARD)
        }
    }

    /**
     * An in-memory database plus an ordered teardown.
     *
     * The production ViewModels keep Room Flows hot via `stateIn(viewModelScope, ...)`.
     * Cancelling that scope (see `clearForTest`) makes Room asynchronously run
     * `InvalidationTracker.syncTriggers` on the query executor while removing the observer;
     * if that lands after `close()` it reopens the connection and Robolectric's CloseGuard
     * dumps a spurious "resource never released" stack trace into the suite output.
     *
     * [close] fixes the ordering: it flushes the single-threaded query executor (FIFO, so
     * any queued sync has finished) before closing, then shuts the executor down.
     */
    class TestDb(val db: HabitDatabase, private val onClose: () -> Unit) {
        fun close() = onClose()
    }

    fun testDb(
        context: Context = ApplicationProvider.getApplicationContext(),
    ): TestDb {
        val queryExecutor = Executors.newSingleThreadExecutor()
        val db = Room.inMemoryDatabaseBuilder(context, HabitDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(queryExecutor)
            .addCallback(callback())
            .build()
        return TestDb(db) {
            runCatching { queryExecutor.submit { }.get(2, TimeUnit.SECONDS) }
            db.close()
            queryExecutor.shutdownNow()
        }
    }
}
