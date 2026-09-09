package com.manasm.habit100.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.manasm.habit100.MainActivity
import com.manasm.habit100.R
import java.time.Instant

/** Owns the reminder notification channel and posts / clears the single reminder notification. */
class HabitNotifier(private val context: Context) {

    fun ensureChannel() {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Daily nudges to mark your habit, and the morning grace-window last call."
            },
        )
    }

    /**
     * @param expiresAt when the reminder stops being actionable (the grace cutoff, or midnight)
     *        — the notification auto-dismisses then so a stale "Mark done" can't mark the wrong day.
     * @param forDay the day-in-play the notification is about; the "Mark done" action only marks
     *        if the day-in-play still matches when it is tapped.
     */
    fun post(content: ReminderContent, expiresAt: Instant? = null, forDay: Int = 0) {
        ensureChannel()
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val markDone = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_MARK_DONE)
                .putExtra(EXTRA_FOR_DAY, forDay),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "Mark done", markDone)

        expiresAt?.let {
            val remaining = it.toEpochMilli() - System.currentTimeMillis()
            if (remaining > 0) builder.setTimeoutAfter(remaining)
        }

        try {
            nm.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check and here — nothing to do.
        }
    }

    fun cancel() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    companion object {
        const val CHANNEL_ID = "reminders"
        const val NOTIFICATION_ID = 100
        const val ACTION_MARK_DONE = "com.manasm.habit100.notify.MARK_DONE"
        const val EXTRA_FOR_DAY = "for_day"
    }
}
