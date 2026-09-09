package com.manasm.habit100.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.manasm.habit100.HabitApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires for a GRACE / EVENING alarm and for the notification's "Mark done" action. The work
 * itself lives in [ReminderWork]; this is just the `goAsync` + coroutine shell.
 */
class ReminderReceiver : BroadcastReceiver() {

    private val known = setOf(
        ReminderKind.GRACE.action,
        ReminderKind.EVENING.action,
        HabitNotifier.ACTION_MARK_DONE,
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in known) return
        val container = (context.applicationContext as HabitApplication).container
        val notifiedDay = intent.getIntExtra(HabitNotifier.EXTRA_FOR_DAY, 0)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                ReminderWork.handle(container, intent.action, notifiedDay)
            } finally {
                pending.finish()
            }
        }
    }
}
