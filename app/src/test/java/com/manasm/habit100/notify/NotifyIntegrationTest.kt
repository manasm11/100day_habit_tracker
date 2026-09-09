package com.manasm.habit100.notify

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.manasm.habit100.support.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotifyIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val ny = ZoneId.of("America/New_York")

    @Test fun scheduler_sets_a_grace_and_an_evening_alarm() {
        val now = Instant.parse("2026-03-10T12:00:00Z") // 08:00 in New York
        ReminderScheduler(context, FakeClock(now)).scheduleAll(ny)

        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertEquals(2, alarms.size)
        val triggers = alarms.map { it.triggerAtTime }.sorted()
        assertEquals(
            listOf(
                nextTrigger(ReminderKind.GRACE, ny, now).toEpochMilli(),
                nextTrigger(ReminderKind.EVENING, ny, now).toEpochMilli(),
            ).sorted(),
            triggers,
        )
    }

    @Test fun scheduler_cancel_all_clears_the_alarms() {
        val scheduler = ReminderScheduler(context, FakeClock(Instant.parse("2026-03-10T12:00:00Z")))
        scheduler.scheduleAll(ny)
        scheduler.cancelAll()

        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertEquals(0, alarms.size)
    }

    @Test fun notifier_creates_the_reminders_channel() {
        HabitNotifier(context).ensureChannel()
        val nm = context.getSystemService(NotificationManager::class.java)
        assertNotNull(nm.getNotificationChannel(HabitNotifier.CHANNEL_ID))
    }

    @Test fun notifier_posts_when_notifications_are_enabled() {
        val notifier = HabitNotifier(context)
        notifier.post(ReminderContent("Time for Read", "Day 5 of 100 — mark it done."))

        val nm = context.getSystemService(NotificationManager::class.java)
        val posted = shadowOf(nm).allNotifications
        assertEquals(1, posted.size)
    }
}
