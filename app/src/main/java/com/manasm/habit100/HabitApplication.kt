package com.manasm.habit100

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HabitApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Debug only: keep the in-memory DevClock in sync with the persisted offset so a
        // saved offset takes effect on the next process start and stays live afterwards.
        val devClock = container.devClock
        val devClockStore = container.devClockStore
        if (devClock != null && devClockStore != null) {
            appScope.launch {
                devClockStore.offsetSeconds.collect { devClock.update(it) }
            }
        }
    }
}
