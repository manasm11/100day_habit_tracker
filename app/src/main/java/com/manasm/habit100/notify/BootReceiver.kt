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

/** Alarms don't survive a reboot — re-schedule them (or clear them) once the device is back up. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as HabitApplication).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                container.reminderScheduler.syncFor(container.repository.observeActive().first())
            } finally {
                pending.finish()
            }
        }
    }
}
