package com.manasm.habit100.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.manasm.habit100.clock.Clock
import java.time.ZoneId

/** Schedules the next GRACE (09:00) and EVENING (19:30) reminder alarms in the habit's zone. */
class ReminderScheduler(private val context: Context, private val clock: Clock) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    fun scheduleAll(zoneId: ZoneId) {
        ReminderKind.entries.forEach { schedule(it, zoneId) }
    }

    fun cancelAll() {
        ReminderKind.entries.forEach { alarmManager?.cancel(pendingIntent(it)) }
    }

    private fun schedule(kind: ReminderKind, zoneId: ZoneId) {
        val am = alarmManager ?: return
        val at = nextTrigger(kind, zoneId, clock.now()).toEpochMilli()
        // Inexact + allow-while-idle: no special permission, survives Doze with a modest delay
        // (fine for a 09:00 nudge against a 10:00 deadline).
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(kind))
    }

    private fun pendingIntent(kind: ReminderKind): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + kind.ordinal,
            Intent(context, ReminderReceiver::class.java).setAction(kind.action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        private const val REQUEST_BASE = 700
    }
}

val ReminderKind.action: String
    get() = when (this) {
        ReminderKind.GRACE -> "com.manasm.habit100.notify.REMINDER_GRACE"
        ReminderKind.EVENING -> "com.manasm.habit100.notify.REMINDER_EVENING"
    }
