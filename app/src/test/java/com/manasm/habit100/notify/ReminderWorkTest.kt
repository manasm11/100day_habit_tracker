package com.manasm.habit100.notify

import android.app.AlarmManager
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.manasm.habit100.HabitApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HabitApplication::class)
class ReminderWorkTest {
    private val app: HabitApplication = ApplicationProvider.getApplicationContext()
    private val container get() = app.container
    private val zone = ZoneId.of("America/New_York")
    private val nm get() = app.getSystemService(NotificationManager::class.java)
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms

    @Before fun clearAlarms() = shadowOf(app.getSystemService(AlarmManager::class.java)).let {
        while (it.scheduledAlarms.isNotEmpty()) it.peekNextScheduledAlarm()?.let { a -> it.scheduledAlarms.remove(a) }
    }

    @After fun cancel() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.clear()

    @Test fun evening_fire_posts_a_reminder_and_arms_tomorrows_alarms() = runTest {
        container.repository.createHabit("Read", zone)

        ReminderWork.handle(container, ReminderKind.EVENING.action, notifiedDay = 0)

        assertEquals(1, shadowOf(nm).allNotifications.size)
        assertEquals(2, alarms.size) // GRACE + EVENING rescheduled
    }

    @Test fun fire_with_no_active_habit_posts_nothing_and_cancels_alarms() = runTest {
        // arm something first
        container.reminderScheduler.scheduleAll(zone)
        assertEquals(2, alarms.size)

        ReminderWork.handle(container, ReminderKind.GRACE.action, notifiedDay = 0)

        assertEquals(0, shadowOf(nm).allNotifications.size)
        assertEquals(0, alarms.size)
    }

    @Test fun mark_done_marks_the_notified_day_and_clears_the_notification() = runTest {
        container.repository.createHabit("Read", zone)
        container.notifier.post(ReminderContent("t", "b"))
        assertEquals(1, shadowOf(nm).allNotifications.size)

        ReminderWork.handle(container, HabitNotifier.ACTION_MARK_DONE, notifiedDay = 1)

        assertEquals(1, container.repository.observeActive().first()!!.snapshot.doneCount)
        assertEquals(0, shadowOf(nm).allNotifications.size)
    }

    @Test fun mark_done_is_ignored_when_the_day_in_play_no_longer_matches() = runTest {
        container.repository.createHabit("Read", zone)

        // notification was for day 2, but the day in play is 1
        ReminderWork.handle(container, HabitNotifier.ACTION_MARK_DONE, notifiedDay = 2)

        assertEquals(0, container.repository.observeActive().first()!!.snapshot.doneCount)
    }

    @Test fun sync_for_arms_alarms_for_an_active_habit_and_cancels_for_none() = runTest {
        container.repository.createHabit("Read", zone)
        val active = container.repository.observeActive().first()
        container.reminderScheduler.syncFor(active)
        assertEquals(2, alarms.size)

        container.reminderScheduler.syncFor(null)
        assertEquals(0, alarms.size)
    }

    @Test fun posted_reminder_carries_a_mark_done_action() = runTest {
        val deadline = LocalDate.of(2026, 1, 5).atTime(10, 0).atZone(zone).toInstant()
        container.notifier.post(ReminderContent("t", "b"), expiresAt = deadline, forDay = 4)
        val posted = shadowOf(nm).allNotifications.single()
        assertTrue(posted.actions.orEmpty().any { it.title == "Mark done" })
    }
}
