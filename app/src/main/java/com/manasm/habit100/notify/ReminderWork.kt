package com.manasm.habit100.notify

import com.manasm.habit100.AppContainer
import com.manasm.habit100.domain.dateForDay
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * The actual work behind [ReminderReceiver] — pulled out of the receiver so it is a plain
 * `suspend fun` that Robolectric can drive directly.
 */
object ReminderWork {

    suspend fun handle(container: AppContainer, action: String?, notifiedDay: Int) {
        when (action) {
            ReminderKind.GRACE.action -> fire(container, ReminderKind.GRACE)
            ReminderKind.EVENING.action -> fire(container, ReminderKind.EVENING)
            HabitNotifier.ACTION_MARK_DONE -> markDone(container, notifiedDay)
        }
    }

    private suspend fun fire(container: AppContainer, kind: ReminderKind) {
        val active = container.repository.observeActive().first()
        if (active != null) {
            val snap = container.repository.snapshotOf(active.habit, active.logs)
            reminderFor(kind, active.habit.name, snap, active.habit.attemptTrackLength)?.let {
                val zone = ZoneId.of(active.habit.timeZoneId)
                val expiresAt = when (kind) {
                    ReminderKind.GRACE -> snap.graceDeadline
                    ReminderKind.EVENING ->
                        dateForDay(active.habit.attemptStartDate, snap.calendarDayNumber + 1)
                            .atStartOfDay(zone).toInstant()
                }
                container.notifier.post(it, expiresAt, snap.currentDayNumber)
            }
        }
        // Alarms exist iff a habit is forming; this re-arms tomorrow's (or clears them).
        container.reminderScheduler.syncFor(active)
    }

    private suspend fun markDone(container: AppContainer, notifiedDay: Int) {
        val active = container.repository.observeActive().first()
        if (active != null) {
            val snap = container.repository.snapshotOf(active.habit, active.logs)
            if (snap.currentDayNumber == notifiedDay && snap.canMarkToday) {
                runCatching { container.repository.markTodayDone(active.habit.id) }
            }
        }
        container.notifier.cancel()
    }
}
