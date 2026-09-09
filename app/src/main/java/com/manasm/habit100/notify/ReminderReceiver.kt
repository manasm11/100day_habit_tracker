package com.manasm.habit100.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.manasm.habit100.HabitApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Fires for a GRACE / EVENING alarm (post the reminder if still relevant, then reschedule that
 * kind for the next day) and for the notification's "Mark done" action.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as HabitApplication).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ReminderKind.GRACE.action -> fireReminder(container, ReminderKind.GRACE)
                    ReminderKind.EVENING.action -> fireReminder(container, ReminderKind.EVENING)
                    HabitNotifier.ACTION_MARK_DONE -> markDone(container)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fireReminder(
        container: com.manasm.habit100.AppContainer,
        kind: ReminderKind,
    ) {
        val active = container.repository.observeActive().first()
        if (active != null) {
            val snap = container.repository.snapshotOf(active.habit, active.logs)
            reminderFor(kind, active.habit.name, snap, active.habit.attemptTrackLength)
                ?.let { container.notifier.post(it) }
        }
        val zone = active?.let { ZoneId.of(it.habit.timeZoneId) } ?: ZoneId.systemDefault()
        container.reminderScheduler.scheduleAll(zone)
    }

    private suspend fun markDone(container: com.manasm.habit100.AppContainer) {
        container.repository.observeActive().first()?.let {
            runCatching { container.repository.markTodayDone(it.habit.id) }
        }
        container.notifier.cancel()
    }
}
