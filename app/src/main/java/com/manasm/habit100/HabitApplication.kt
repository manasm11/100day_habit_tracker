package com.manasm.habit100

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.manasm.habit100.rollover.RolloverWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.concurrent.TimeUnit

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

        // Foreground catch-up: run rollover every time the app is brought to the foreground so
        // elapsed misses are filled and terminal transitions persisted while the user is looking.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                appScope.launch { runCatching { container.runRolloverNow() } }
            }
        })

        // Daily backstop: keeps rollover happening even if the app is never foregrounded.
        runCatching {
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "rollover",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RolloverWorker>(1, TimeUnit.DAYS).build(),
            )
        }.onFailure { Log.w("HabitApplication", "Could not schedule rollover backstop work", it) }

        // (Re)schedule the daily / grace-window reminder alarms whenever the active habit
        // (and therefore its timezone) changes.
        container.notifier.ensureChannel()
        appScope.launch {
            container.repository.observeActive()
                .distinctUntilChangedBy { it?.habit?.let { h -> h.id to h.timeZoneId } }
                .collect { active ->
                    val zone = active?.let { ZoneId.of(it.habit.timeZoneId) } ?: ZoneId.systemDefault()
                    runCatching { container.reminderScheduler.scheduleAll(zone) }
                        .onFailure { Log.w("HabitApplication", "Could not schedule reminders", it) }
                }
        }
    }
}
