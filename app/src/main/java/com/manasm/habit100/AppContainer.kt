package com.manasm.habit100

import android.app.Application
import com.manasm.habit100.clock.Clock
import com.manasm.habit100.clock.DevClock
import com.manasm.habit100.clock.DevClockStore
import com.manasm.habit100.clock.SystemClock
import com.manasm.habit100.data.HabitDatabase
import com.manasm.habit100.data.HabitRepository

/**
 * Manual dependency-injection container. One instance is created by [HabitApplication].
 *
 * In debug builds the clock is a [DevClock] whose offset is persisted via [devClockStore];
 * in release builds it is a plain [SystemClock] and both dev-clock members are null.
 */
class AppContainer(app: Application) {

    val isDebug: Boolean = BuildConfig.DEBUG

    private val db: HabitDatabase = HabitDatabase.build(app)

    val devClockStore: DevClockStore? = if (isDebug) DevClockStore(app) else null
    private val devClockImpl: DevClock? = if (isDebug) DevClock(SystemClock()) else null
    val devClock: DevClock? get() = devClockImpl

    val clock: Clock = devClockImpl ?: SystemClock()

    val repository: HabitRepository =
        HabitRepository(db, db.habitDao(), db.dayLogDao(), db.checkinDao(), clock)

    // Task 13/14 fills this in:
    // val rolloverEngine: RolloverEngine by lazy {
    //     RolloverEngine(RepoRolloverPort(repository, db), clock)
    // }
}
